# Light Log Schema v1

Light Log включён по умолчанию и хранится только локально. Передача по сети не выполняется. Экспорт создаётся пользователем вручную в формате `events.jsonl.gz`.

`events.jsonl` — это **JSON Lines (JSONL)**: каждая физическая строка файла является самостоятельным JSON-объектом. Файл не является одним JSON-объектом или массивом, поэтому его нужно открывать в режиме JSON Lines / NDJSON, а не проверять обычным JSON-валидатором.

`schema_version` всегда равен `1`. Поля этой версии не переименовываются и не меняют смысл; несовместимые дополнения получают новую версию схемы.

## Диагностика логирования

Экспортный заголовок содержит `logging_health`, чтобы отличать пропуски данных модели от сбоев сохранения самих логов:

```json lines
{
  "logging_health": {
    "events_created": 128,
    "events_persisted": 126,
    "events_dropped": 2,
    "events_pruned": 0,
    "last_success_at_ms": 1764000000000,
    "last_error_type": "SQLiteFullException",
    "last_error_at_ms": 1764000000100
  }
}
```

Поля `last_success_at_ms`, `last_error_type` и `last_error_at_ms` необязательны: пока соответствующего события не было, текущая реализация не включает их в JSON-объект.

При ошибке записи текущая реализация обновляет `logging_health`, если это возможно, и дублирует техническое сообщение в Logcat. Отдельное событие `logging_error` не записывается: попытка записать его в уже недоступное хранилище не была бы надёжной.

Light Log и его диагностика автономны: им не нужны Android Studio, ADB, сеть или внешний сервис. События хранятся в локальной БД, а `logging_health` — в отдельном минимальном атомарном файле во внутреннем хранилище приложения. После каждой попытки записи этот файл обновляет счётчики и последнюю ошибку.

Хранилище Light Log кольцевое: сохраняются последние 10 000 событий, более старые записи удаляются. Число таких удалений накапливается в `events_pruned`.

Событие самого сбоя может не сохраниться при заполненном диске, аварийном завершении процесса или повреждённой БД. В этих случаях экспорт всё равно читает последнее доступное `logging_health` из отдельного файла и показывает расхождение `events_created` / `events_persisted`. Logcat может дублировать сообщения для разработки, но не считается источником данных и не нужен для экспорта.

## Заголовок экспорта

Первая строка — `export_header`:

```json lines
{
  "schema_version": 1,
  "record_type": "export_header",
  "exported_at_ms": 1764000000000,
  "app_version": "1.0",
  "device_info": {
    "manufacturer": "Google",
    "model": "Pixel 8",
    "device": "shiba",
    "android_release": "16",
    "android_sdk": 36,
    "supported_abis": ["arm64-v8a"],
    "cpu_cores": 8,
    "total_ram_mb": 7800,
    "memory_class_mb": 384,
    "available_storage_mb": 42000,
    "execution_provider": "CPU"
  },
  "model": {
    "release_tag": "v0.2.0-mobile",
    "onnx_sha256": "…"
  }
}
```

## Показ выдачи

`search_impression` записывается один раз после debounce и завершения поиска. Промежуточные символы при наборе не логируются. В `candidates` сохраняется не более 20 элементов: сначала показанные пользователю результаты в порядке выдачи, затем ближайшие к прохождению порога отклонённые кандидаты.

```json lines
{
  "schema_version": 1,
  "record_type": "search_impression",
  "search_id": "uuid-v4",
  "session_id": "uuid-v4",
  "timestamp_ms": 1764000000000,
  "query_text": "ахахаха",
  "expanded_query": "ахахаха смех хахаха hahaha",
  "query_expansion_version": "laughter-v1",
  "model_release_tag": "v0.2.0-mobile",
  "relevance_filter": {
    "corpus_size": 318,
    "raw_cosine_gte": 0.25,
    "raw_bm25_gte": 3.4838
  },
  "latency_ms": {
    "database_search": 4,
    "model": {
      "text_encoder": 18,
      "visual_text_encoder": null,
      "reranker": 0
    },
    "scoring": {
      "dense": 2,
      "bm25": 1,
      "fusion": 1
    },
    "total": 26
  },
  "candidates": [
    {
      "diagnostic_order": 1,
      "rank": 1,
      "displayed": true,
      "file_hash": "sha256…",
      "file_name": "cat.jpg",
      "mime_type": "image/jpeg",
      "candidate_input": {
        "description": "грустный кот в капюшоне",
        "tags": ["кот", "грусть"],
        "indexed_text": "грустный кот в капюшоне\nкот грусть"
      },
      "scores": {
        "text_raw": 0.71,
        "bm25_raw": 4.2,
        "text_normalized": 0.91,
        "bm25_normalized": 1.0,
        "final": 0.95
      },
      "relevance": {
        "corpus_size": 318,
        "raw_cosine_gte": 0.25,
        "raw_bm25_gte": 3.4838,
        "exact_tag_match": false,
        "passed_by_dense": true,
        "passed_by_bm25": true,
        "passed_by_exact_tag": false,
        "passed_relevance_filter": true
      }
    }
  ]
}
```

`null` в задержке модели означает, что компонент в этом поиске не использовался. `0` означает, что компонент был использован, но измеренная задержка меньше 1 мс. До нормализации кандидат остаётся в выдаче, если он прошёл хотя бы одно условие: точное совпадение тега, `raw_cosine >= 0.25` или `raw_bm25 >= tb(N)`, где `N` — `corpus_size` проиндексированных документов:

```kotlin
val tb = minOf(
    3.5f,
    0.65f * ln((maxOf(corpusSize, 3) - 0.5f) / 1.5f + 1.0f)
)
```

В `relevance` каждой записи фиксируются фактически использованные пороги и все причины прохождения; это сохраняет контекст при будущих динамических правилах. `exact_tag_match` срабатывает при совпадении полного нормализованного запроса с тегом либо при наличии однословного тега среди токенов запроса. У отклонённого диагностического кандидата `rank`, `text_normalized`, `bm25_normalized` и `final` равны `null`, а `displayed` — `false`; его raw-скоры и причины отказа позволяют калибровать пороги. Если в базе нет ни одного проиндексированного документа, `candidates` — пустой массив. Текущая версия `laughter-v1` распознаёт повторяющиеся `ха` / `аха` / `ha` и добавляет к запросу отсутствующие термины `смех`, `хахаха`, `hahaha`; мини-словарь сленга не применяется.

## Действие по выдаче

```json lines
{
  "schema_version": 1,
  "record_type": "search_action",
  "search_id": "uuid-v4",
  "session_id": "uuid-v4",
  "timestamp_ms": 1764000001450,
  "action_type": "open",
  "file_hash": "sha256…",
  "rank": 2,
  "time_since_impression_ms": 1450
}
```

Контракт допускает `open`, `copy`, `share`, `favorite`, `reformulate`, `abandon`. Сейчас приложение записывает `open`, `copy`, `favorite`, `reformulate` и `abandon`; `share` будет добавлен вместе с соответствующим действием в выдаче поиска.

## Индексация

Для обработки одного мема записывается `indexing_event`. В `model_latency_ms` фиксируется время каждого реально вызванного компонента: `text_encoder`, `visual_encoder`, `ocr_engine`, `asr_engine`, `reranker`. Дополнительно фиксируются `database_write_ms` и `total_ms`.

## Импорт и ручная разметка

Целевой контракт предусматривает один `import_batch` на пачку добавления и `import_item` на каждый выбранный файл. Эти события пока не записываются приложением; до их реализации импорт не следует считать источником размеченного датасета.

```json lines
{
  "schema_version": 1,
  "record_type": "import_item",
  "session_id": "uuid-v4",
  "batch_id": "uuid-v4",
  "timestamp_ms": 1764000000000,
  "source": "gallery",
  "file_hash": "sha256…",
  "file_name": "cat.jpg",
  "mime_type": "image/jpeg",
  "result": "imported",
  "latency_ms": 84
}
```
Допустимые `source`: `gallery`, `files`, `share`. Допустимые `result`: `imported`, `duplicate`, `unsupported`, `source_unavailable`, `storage_error`.

Импорт сам по себе не создаёт размеченный датасет. Пользовательская разметка фиксируется отдельным `meme_annotation` при каждом успешном сохранении описания или тегов:

```json lines
{
  "schema_version": 1,
  "record_type": "meme_annotation",
  "session_id": "uuid-v4",
  "timestamp_ms": 1764000001200,
  "file_hash": "sha256…",
  "description": "грустный кот в капюшоне",
  "tags": ["кот", "грусть"],
  "annotation_source": "user"
}
```

После реализации `import_item` связка `media/<file_hash>`, `import_item` и последнего `meme_annotation` будет давать данные для формирования датасета. Исходные URI, абсолютные пути и имена каталогов не записываются.

## Full Debug Bundle

Полный архив создаётся только по явному действию пользователя. Копии медиа включены в экспорт по умолчанию, но пользователь может отключить их перед созданием архива. Full Debug Bundle содержит `events.jsonl`, `memes.jsonl` и `media/<file_hash>`. Снимок `candidate_input` в событии выдачи остаётся источником данных на момент поиска, даже если пользователь позднее изменил описание или теги.

В Debug-сборке пользователь также может вручную очистить локальные записи. Очищаются Light Log, health-метрики и технический журнал, включая устаревший файл старого формата на устройстве; уже выгруженные архивы, медиафайлы и пакет модели не изменяются.
