# Спецификация контракта ML ↔ Android (v0.2)

Документ фиксирует согласованный архитектурный контракт между ML-частью и Android-приложением (Kotlin, Jetpack Compose, SQLite) для **Meme Organizer**.

> Контракт v0.2 описывает целевую архитектуру и сохранён как история решений.
> Он не является описанием текущего формата мультимодального пакета: актуальные
> правила его установки и реально подключённые компоненты приведены в
> [model_updates.md](model_updates.md).

- **Идентификаторы:** внутренний первичный ключ — `memeId: Long`, `file_hash` (SHA-256) используется для дедупликации и сопоставления между экспортами.
- **Модельная независимость:** финальные champion-модели выбираются по итогам R&D-бенчмарка; параметры моделей читаются из `preset_manifest.json`.
- **Ориентиры по производительности:** указанные ниже цифры по памяти и latency являются **целевыми ориентирами (targets)** и будут проверены на реальных устройствах после сборки первого рабочего пакета.

---

## 1. Состав ML-пайплайна и ориентировочные кандидаты

Пайплайн состоит из 5 независимых компонентов. Целевой бюджет пресета `base` после квантизации (INT8/FP16): **~300–600 МБ** на диске (позже добавятся пресеты `lite` и `heavy`).

| Компонент | Роль | Ориентировочные семейства-кандидаты (R&D) | Целевой вес (INT8/FP16) |
|---|---|---|---|
| `visual_encoder` | Поиск «текст → картинка/кадры», zero-shot авто-теги | SigLIP 2 / Multilingual OpenCLIP / ruCLIP (раздельные vision + text башни) | ~150–250 МБ |
| `text_encoder` | Семантический поиск по OCR, ASR, тегам и описаниям | Multilingual E5-small / BGE / ruBERT-tiny / FRIDA | ~60–130 МБ |
| `ocr_engine` | Текст с картинок и ключевых кадров видео (RU + EN) | PaddleOCR Mobile (DBNet det + Cyrillic/EN rec) | ~15–25 МБ |
| `asr_engine` | Речь из видео (RU + EN) | Silero VAD + GigaAM (CTC/RNNT) или Whisper-tiny/base | ~70–150 МБ |
| `reranker` | Слияние и финальное ранжирование топ-K кандидатов | MVP: алгоритмический RRF + веса каналов; далее: опционально лёгкий Cross-Encoder или MLP | 0 МБ (MVP) / до 50 МБ |

---

## 2. Рантайм и исполнение моделей на Android

### 2.1. Базовый стек инференса
- **Основной рантайм:** **ONNX Runtime Mobile** (Android AAR). Все модели поставляются в формате **`.onnx`** с квантизацией (**INT8** или **FP16**).
- **Аппаратное ускорение:** **XNNPACK** (CPU ARM NEON) по умолчанию на всех устройствах + опциональный **QNN** (Qualcomm NPU) на Snapdragon.
- **ASR-модуль:** **`sherpa-onnx`** (Android AAR поверх ONNX Runtime) — закрывает Silero VAD и декодирование речи без ручного DSP на Kotlin.
- **Токенизация:** используется файл токенизатора из пакета модели (`tokenizer.json` / `spiece.model`) через нативную обёртку (JNI) или `onnxruntime-extensions`.
- **Постпроцессинг:** `mean_pooling` и `L2-normalization` зашиваются внутрь ONNX-графа при экспорте.

### 2.2. Поставка весов, состав пакета и управление RAM
- **Хранение весов:** модели не зашиваются в APK — пресет скачивается/подкладывается в локальное хранилище приложения с проверкой `sha256` из манифеста.
- **Обязательные файлы в пакете по компонентам:**
  - `visual_encoder`: `vision.onnx` (визуальная башня), `text.onnx` (текстовая башня), `tokenizer.json` (или `spiece.model`), `tag_bank.bin` (предрасчитанные векторы базовых тегов) + `tag_labels.json`.
  - `text_encoder`: `model.onnx`, `tokenizer.json` (или `spiece.model`).
  - `ocr_engine`: `det.onnx`, `rec.onnx`, `dict.txt` (словарь символов), `ocr_config.json` (пороги DBNet).
  - `asr_engine` (`sherpa-onnx`): `silero_vad.onnx`, файлы модели ASR (`*.onnx`), `tokens.txt`, `asr_config.json`.
  - `reranker`: в MVP — без файлов (параметры RRF задаются прямо в `preset_manifest.json`).
- **Жизненный цикл в RAM:**
  - **Поиск (foreground):** в RAM держатся только лёгкие текстовые башни (`visual_encoder.text` + `text_encoder`). Обязателен **debounce `150–250 мс`** и отмена предыдущего поиска (`Job.cancel()`).
  - **Индексация (background `WorkManager`):** `visual_encoder.vision`, `ocr_engine`, `asr_engine` загружаются лениво и **явно выгружаются (`close()`)** после завершения задачи.

---

## 3. Форматы входов/выходов и Kotlin-интерфейсы

Все модельно-зависимые параметры (`dim`, `image_size`, `mean/std`, `max_tokens`, префиксы) читаются из `preset_manifest.json`.

### 3.1. Спецификация компонентов

1. **`VisualEncoder` (две раздельные ONNX-башни в одном пространстве `embedding_space_id`)**
   - *Общее пространство:* `vision.onnx`, `text.onnx` и `tag_bank.bin` принадлежат одному `embedding_space_id` и всегда имеют одинаковую размерность `dim`. При смене `embedding_space_id` обновляются все три файла вместе.
   - *Вход (картинка/кадр):* `Bitmap` → RGB, ресайз `image_size × image_size`, нормализация `(pixel / 255.0 - mean) / std`, тензор `float32[1, 3, H, W]` (NCHW).
   - *Выход (картинка/кадр):* `FloatArray(dim)`, L2-нормализованный (`||v|| = 1`).
   - *Вход (запрос):* строка → токенизатор → `input_ids: int64[1, max_tokens]`.
   - *Выход (запрос):* `FloatArray(dim)`, L2-нормализованный.
   - *Видео/GIF:* короткие (≤15 сек) — сэмплинг каждые `0.5–1.0 сек` (минимум 3 кадра); длинные — I-frames (`MediaExtractor`) не чаще 1 кадра в `2–3 сек`, далее дедупликация соседних кадров при `cosine_similarity > 0.90–0.92`.
   - *Авто-теги (`tag_bank`):* матрица `N × dim` (`int8`) + `tag_labels.json`. Авто-тегирование нового мема = одно умножение `v_img × tag_bank` (<1 мс).

2. **`TextEncoder`**
   - *Вход:* строка + префикс из манифеста (`query_prefix` / `passage_prefix`) → `input_ids: int64[1, L]`, `attention_mask: int64[1, L]` (`1 <= L <= max_tokens`).
   - *Выход:* `FloatArray(dim)`, L2-нормализованный.
   - *Чанкинг:* длинные тексты (ASR длинных видео) режутся на смысловые чанки (~15–30 сек / 1–2 предложения) с сохранением `timestamp_ms`.

3. **`OcrEngine`**
   - *Вход:* `Bitmap` (RGB, длинная сторона `<= 960px` с сохранением пропорций).
   - *Выход:* `List<OcrBlock(text: String, confidence: Float, bbox: NormalizedRect)>`.
   - *Формат координат `bbox`:* **нормализованные значения `[0.0 .. 1.0]`** (`left, top, right, bottom` относительно исходного изображения), независимые от ресайза. Блоки с `confidence < 0.5` отбрасываются; дубли субтитров между кадрами схлопываются.

4. **`AsrEngine` (`sherpa-onnx`)**
   - *Вход:* **моно PCM 16 kHz, `float32` в диапазоне `[-1.0, 1.0]`**.
   - *Потоковая обработка длинных видео и таймкоды:* чтобы не грузить всю 10-минутную аудиодорожку в RAM, Android подаёт поток `AudioChunk(offsetMs: Long, pcm16kMono: FloatArray)`, где `offsetMs` — абсолютное смещение начала чанка от старта видео (0 мс). Внутри `AsrEngine` (Silero VAD + ASR) все таймкоды смещаются на `offsetMs`, и на выходе `TranscriptChunk(startMs, endMs, text)` **всегда возвращает абсолютные миллисекунды от начала видео (`0 .. duration_ms`)**.

5. **`Reranker`**
   - *Вход:* `query: String` + `List<RetrievalCandidate>` со скорами по каналам (`visual`, `text`, `bm25`).
   - *Выход:* отсортированный `List<RankedMeme>`.

### 3.2. Kotlin-интерфейсы (для заглушек)

```kotlin
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) // [0.0..1.0]
data class OcrBlock(val text: String, val confidence: Float, val bbox: NormalizedRect)
data class AudioChunk(val offsetMs: Long, val pcm16kMono: FloatArray)
data class TranscriptChunk(val startMs: Long, val endMs: Long, val text: String) // абсолютные мс от 0

data class RetrievalCandidate(
    val memeId: Long,
    val visualScore: Float?,
    val bestFrameMs: Long?,
    val textScore: Float?,
    val bm25Score: Float?,
    val matchedText: String? = null
)

data class RankedMeme(
    val memeId: Long,
    val finalScore: Float,
    val bestTimestampMs: Long?,
    val channelScores: Map<String, Float>
)

interface VisualEncoder : AutoCloseable {
    val componentVersion: String
    val embeddingSpaceId: String
    val dim: Int
    suspend fun encodeImage(bitmap: Bitmap): FloatArray
    suspend fun encodeQuery(query: String): FloatArray
}

interface TextEncoder : AutoCloseable {
    val componentVersion: String
    val dim: Int
    suspend fun encodePassage(text: String): FloatArray
    suspend fun encodeQuery(query: String): FloatArray
}

interface OcrEngine : AutoCloseable {
    val componentVersion: String
    suspend fun extractText(bitmap: Bitmap): List<OcrBlock>
}

interface AsrEngine : AutoCloseable {
    val componentVersion: String
    suspend fun transcribe(pcm16kMono: FloatArray): List<TranscriptChunk>
    fun transcribeStream(chunks: Flow<AudioChunk>): Flow<TranscriptChunk>
}

interface Reranker : AutoCloseable {
    val componentVersion: String
    suspend fun rank(query: String, candidates: List<RetrievalCandidate>): List<RankedMeme>
}
```

---

## 4. Независимое версионирование и схема SQLite (`sqlite-vec`)

### 4.1. Манифест пресета (`preset_manifest.json`)
Каждый компонент версионируется **независимо** (обновление одного компонента не вызывает переиндексацию OCR/ASR или других неизменившихся модальностей):

```json
{
  "preset_id": "base_v1",
  "components": {
    "visual_encoder": {
      "version": "vis-candidate-v1",
      "embedding_space_id": "vis_space_candidate_v1",
      "dim": 768,
      "image_size": 224,
      "mean": [0.5, 0.5, 0.5],
      "std": [0.5, 0.5, 0.5],
      "max_tokens": 64,
      "files": {
        "vision_model": { "path": "visual/vision.onnx", "sha256": "..." },
        "text_model":   { "path": "visual/text.onnx",   "sha256": "..." },
        "tokenizer":    { "path": "visual/tokenizer.json", "sha256": "..." },
        "tag_bank":     { "path": "visual/tag_bank.bin",   "sha256": "..." },
        "tag_labels":   { "path": "visual/tag_labels.json","sha256": "..." }
      }
    },
    "text_encoder": {
      "version": "txt-candidate-v1",
      "dim": 384,
      "max_tokens": 256,
      "query_prefix": "query: ",
      "passage_prefix": "passage: ",
      "files": {
        "model":     { "path": "text/model.onnx",     "sha256": "..." },
        "tokenizer": { "path": "text/tokenizer.json", "sha256": "..." }
      }
    },
    "ocr_engine": {
      "version": "ocr-candidate-v1",
      "files": {
        "det_model": { "path": "ocr/det.onnx",        "sha256": "..." },
        "rec_model": { "path": "ocr/rec.onnx",        "sha256": "..." },
        "dict":      { "path": "ocr/dict.txt",        "sha256": "..." },
        "config":    { "path": "ocr/ocr_config.json", "sha256": "..." }
      }
    },
    "asr_engine": {
      "version": "asr-candidate-v1",
      "files": {
        "vad_model": { "path": "asr/silero_vad.onnx", "sha256": "..." },
        "asr_model": { "path": "asr/model.onnx",      "sha256": "..." },
        "tokens":    { "path": "asr/tokens.txt",      "sha256": "..." },
        "config":    { "path": "asr/asr_config.json", "sha256": "..." }
      }
    },
    "reranker": {
      "version": "rrf-v1",
      "params": { "k": 60, "weights": { "visual": 1.0, "text": 1.0, "bm25": 0.7 } }
    }
  }
}
```

### 4.2. Схема SQLite и явная связь векторов с кадрами/чанками

В виртуальных таблицах `sqlite-vec` (`vec0`) связь 1-к-1 с метаданными строится **через общий `rowid`**:
- `vec_visual.rowid` **=** `visual_frames_meta.id` → `visual_frames_meta.meme_id` (`memes.id`) + `timestamp_ms`.
- `vec_text.rowid` **=** `text_chunks.id` → `text_chunks.meme_id` (`memes.id`) + `source` + `timestamp_ms`.

> [!IMPORTANT]
> SQLite не поддерживает `ON DELETE CASCADE` на виртуальные таблицы `vec0`. При удалении мема или переиндексации строки в `vec_visual` / `vec_text` необходимо удалять явно в той же транзакции (или через `AFTER DELETE` триггер на `visual_frames_meta` / `text_chunks`).

```sql
-- 1. ПОЛЬЗОВАТЕЛЬСКИЕ ДАННЫЕ (не затрагиваются при смене ML-моделей)
CREATE TABLE memes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    file_hash       TEXT NOT NULL UNIQUE,      -- SHA-256 файла
    local_uri       TEXT NOT NULL,
    media_type      TEXT NOT NULL,             -- 'image' | 'gif' | 'video'
    duration_ms     INTEGER,
    width           INTEGER,
    height          INTEGER,
    user_caption    TEXT DEFAULT '',
    is_favorite     INTEGER DEFAULT 0,
    use_count       INTEGER DEFAULT 0,
    last_used_at    INTEGER,
    created_at      INTEGER NOT NULL
);

CREATE TABLE tags (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE meme_tags (
    meme_id         INTEGER NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    tag_id          INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    source          TEXT NOT NULL,             -- 'user' | 'auto_accepted' | 'auto_suggested'
    confidence      REAL DEFAULT 1.0,
    PRIMARY KEY (meme_id, tag_id)
);

-- 2. СОСТОЯНИЕ МОДЕЛЕЙ И ОЧЕРЕДЬ ИНДЕКСАЦИИ
CREATE TABLE ml_active_state (
    component_name  TEXT PRIMARY KEY,
    active_version  TEXT NOT NULL,
    preset_id       TEXT NOT NULL,
    dim             INTEGER,
    updated_at      INTEGER NOT NULL
);

CREATE TABLE meme_indexing_state (
    meme_id             INTEGER PRIMARY KEY REFERENCES memes(id) ON DELETE CASCADE,
    visual_version      TEXT,
    ocr_version         TEXT,
    asr_version         TEXT,                  -- 'skipped' для фото и немых GIF
    text_embed_version  TEXT,
    status              TEXT NOT NULL,         -- 'pending' | 'partial' | 'done' | 'failed'
    error_message       TEXT,
    updated_at          INTEGER NOT NULL
);

-- 3. ТЕКСТОВЫЕ ЧАНКИ И FTS5
CREATE TABLE text_chunks (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    meme_id         INTEGER NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    source          TEXT NOT NULL,             -- 'caption' | 'tag' | 'ocr' | 'asr'
    timestamp_ms    INTEGER DEFAULT 0,
    content         TEXT NOT NULL,
    extractor_ver   TEXT NOT NULL
);

CREATE VIRTUAL TABLE text_chunks_fts USING fts5(
    content,
    content='text_chunks',
    content_rowid='id',
    tokenize='unicode61 remove_diacritics 2'
);

-- 4. МЕТАДАННЫЕ КАДРОВ И ВЕКТОРНЫЕ ИНДЕКСЫ (sqlite-vec)
CREATE TABLE visual_frames_meta (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    meme_id         INTEGER NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    timestamp_ms    INTEGER DEFAULT 0,
    model_version   TEXT NOT NULL
);

-- Размерности {VISUAL_DIM} и {TEXT_DIM} подставляются из preset_manifest.json:
-- CREATE VIRTUAL TABLE vec_visual USING vec0(embedding int8[{VISUAL_DIM}] distance_metric=cosine);
-- CREATE VIRTUAL TABLE vec_text   USING vec0(embedding int8[{TEXT_DIM}]   distance_metric=cosine);
```

**Пример записи и поиска с `JOIN` по `rowid`:**
```sql
-- Вставка кадра и его вектора в одной транзакции:
INSERT INTO visual_frames_meta(meme_id, timestamp_ms, model_version) VALUES (?, ?, ?);
-- frame_id = last_insert_rowid()
INSERT INTO vec_visual(rowid, embedding) VALUES (:frame_id, :quantized_int8_vec);

-- Поиск топ-30 ближайших кадров с привязкой к meme_id и timestamp_ms:
SELECT v.rowid AS frame_id, m.meme_id, m.timestamp_ms, (1.0 - v.distance) AS cosine_sim
FROM vec_visual v
JOIN visual_frames_meta m ON m.id = v.rowid
WHERE v.embedding MATCH :query_int8_vec AND k = 30
ORDER BY v.distance ASC;
```

---

## 5. Логирование, приватность и данные для обучения

### 5.1. Политика приватности
- **Логирование выключено по умолчанию** (включается вручную в настройках / дебаг-меню).
- **Экспорт строго вручную** (формат **`.jsonl.gz`**), никаких фоновых отправок.
- Даже Light-логи содержат текстовые запросы и хеши, поэтому считаются чувствительными пользовательскими данными.

### 5.2. Что нужно для обучения в первую очередь
1. **Этап 1 — Light Log (`events.jsonl.gz`):** в первую очередь нужны **только запросы, `file_hash`, скоры каналов, ранги и последовательность действий пользователя**. Этого достаточно для анализа ошибок, подбора весов RRF / обучения реранкера и построения троек `(query, positive_hash, hard_negative_hashes)`.
2. **Этап 2 — Full Debug Bundle (`debug_bundle.zip`, отдельная кнопка Opt-in):** исходные тексты (`ocr`, `asr`, `tags`, `user_caption`) и уменьшенные превью-кадры (`224×224`) понадобятся только на этапе глубокого fine-tuning'а энкодеров и экспортируются отдельным явным действием.

### 5.3. Схема записей в `events.jsonl` (раздельные `search_impression` и `search_action`)

`events.jsonl` использует формат **JSON Lines (JSONL)**: каждая строка — отдельный JSON-объект, а не элемент одного JSON-массива.

Выдача и каждое последующее действие пользователя (открыл несколько результатов → вернулся → скопировал один) пишутся **отдельными событиями**, связанными общим `search_id`. Промежуточные префиксы при наборе (`"ф"`, `"фи"`) не логируются — только завершённый запрос после debounce.

**1. Заголовок экспорта (1-я строка файла):**
```json lines
{
  "record_type": "export_header",
  "exported_at_ms": 1763880100000,
  "app_version": "0.1.0",
  "device_info": {
    "manufacturer": "Google",
    "model": "Pixel 8",
    "soc": "Tensor G3",
    "abi": "arm64-v8a",
    "android_sdk": 35,
    "total_ram_mb": 8192,
    "execution_provider": "XNNPACK"
  }
}
```

**2. Показ выдачи (`search_impression` — 1 запись на запрос):**
```json lines
{
  "record_type": "search_impression",
  "search_id": "uuid-search",
  "session_id": "uuid-session",
  "timestamp_ms": 1763880000000,
  "preset_id": "base_v1",
  "component_versions": {
    "visual_encoder": "vis-candidate-v1",
    "text_encoder": "txt-candidate-v1",
    "ocr_engine": "ocr-candidate-v1",
    "asr_engine": "asr-candidate-v1",
    "reranker": "rrf-v1"
  },
  "query_text": "фииу вон смотри я я",
  "latency_ms": { "encode_query": 18, "retrieval": 6, "rerank": 1 },
  "candidates_top_k": [
    { "rank": 1, "meme_hash": "sha256_abc...", "scores": { "visual": 0.31, "text": 0.42, "bm25": 0.0, "final": 0.032 } },
    { "rank": 2, "meme_hash": "sha256_def...", "scores": { "visual": 0.29, "text": 0.65, "bm25": 4.1, "final": 0.031 } }
  ]
}
```

**3. Действие пользователя (`search_action` — 0..N записей на один `search_id`):**
```json lines
{
  "record_type": "search_action",
  "search_id": "uuid-search",
  "session_id": "uuid-session",
  "timestamp_ms": 1763880001450,
  "time_since_impression_ms": 1450,
  "action_type": "share",
  "meme_hash": "sha256_def...",
  "rank": 2
}
```
*Значения `action_type`: `open`, `copy`, `share`, `favorite`, `reformulate`, `abandon`.*

**4. Событие индексации (`indexing_event` — диагностика производительности и OOM):**
```json lines
{
  "record_type": "indexing_event",
  "event_id": "uuid-v4",
  "timestamp_ms": 1763880050000,
  "preset_id": "base_v1",
  "meme_hash": "sha256_abc...",
  "media_type": "video",
  "duration_ms": 42000,
  "stats": { "frames_extracted": 18, "frames_kept_after_dedup": 7, "speech_duration_ms": 21500, "ocr_chars": 140, "asr_chars": 310 },
  "step_latency_ms": { "extract_media": 420, "visual_encode": 1150, "ocr": 680, "asr": 3200, "text_encode": 95 },
  "status": "done",
  "error": null
}
```

---

## 6. Проверка качества и Smoke-Test Pack

1. **Offline-бенчмарк (Python):** Golden Eval Set из 300–400 реальных ВП-шек (фото, GIF, видео) + 150–200 запросов с разметкой релевантных `file_hash`. Метрики: `Recall@1/3/5`, `MRR`, `NDCG@10`.
2. **Smoke-Test Pack (поставляется с первым рабочим пакетом моделей и с каждым обновлением):**
   - Реальный пример `preset_manifest.json`.
   - 4 тестовых файла (фото с текстом, фото без текста, GIF, короткое видео с речью) + 3 текстовых запроса.
   - `expected_outputs.json` с эталонными векторами из Python, текстами OCR/ASR и топ-3 выдачей.
   - Критерий паритета Android ↔ Python: `cosine_similarity(android_vec, python_vec) > 0.995`.
