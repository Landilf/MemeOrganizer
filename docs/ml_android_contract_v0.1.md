# Спецификация контракта ML ↔ Android (v0.1)

Документ фиксирует архитектурный контракт между ML-частью и Android-приложением (Kotlin, Jetpack Compose, SQLite) для **Meme Organizer**, отвечая на стартовые вопросы по интеграции.

Контракт спроектирован **модельно-независимым**: финальные champion-модели для каждого компонента будут выбраны по итогам R&D-исследования (баланс скорость/качество на реальном датасете), поэтому интерфейсы, БД и логи не хардкодят конкретную архитектуру — параметры моделей читаются из файла манифеста `preset_manifest.json`.

---

## 1. Состав ML-пайплайна и ориентировочные кандидаты

Пайплайн состоит из 5 независимых компонентов. Базовый ориентировочный бюджет пресета `base` после квантизации (INT8/FP16): **~300–600 МБ** на диске (позже добавятся пресеты `lite` и `heavy`).

| Компонент | Роль | Ориентировочные семейства-кандидаты (R&D) | Типичная размерность выхода | Ориентировочный вес (INT8/FP16) |
|---|---|---|---|---|
| `visual_encoder` | Поиск «текст → картинка/кадры», zero-shot авто-теги | SigLIP 2 / Multilingual OpenCLIP / ruCLIP (vision + text towers) | `512` или `768` (из манифеста) | ~150–250 МБ |
| `text_encoder` | Семантический поиск по OCR, ASR, тегам и описаниям | Multilingual E5-small / BGE / ruBERT-tiny / FRIDA | `384` или `512` (из манифеста) | ~60–130 МБ |
| `ocr_engine` | Извлечение текста с картинок и ключевых кадров видео (RU + EN) | PaddleOCR Mobile (детектор + кириллический/латинский распознаватель) | `List<OcrBlock>` | ~15–25 МБ |
| `asr_engine` | Извлечение речи из видео (RU + EN) | Silero VAD + GigaAM (CTC/RNNT) или Whisper-tiny/base | `List<TranscriptChunk>` | ~70–150 МБ |
| `reranker` | Слияние и финальное ранжирование топ-K кандидатов | MVP: алгоритмический RRF + веса каналов; далее: опционально лёгкий Cross-Encoder или MLP/GBDT | `List<RankedMeme>` | 0 МБ (MVP) / до 50 МБ |

**Что определено уже сейчас:** границы компонентов, единый формат поставки и рантайм, форматы входов/выходов и структура БД. Этого достаточно, чтобы реализовать заглушки и всю логику приложения, не дожидаясь финального выбора моделей.

---

## 2. Рантайм и исполнение моделей на Android

### 2.1. Базовый стек инференса
- **Основной рантайм:** **ONNX Runtime Mobile** (официальный Android AAR для Kotlin). Все модели (`visual_encoder`, `text_encoder`, `ocr_engine`, будущий нейро-`reranker`) поставляются в едином формате **`.onnx`** с квантизацией (**INT8** или **FP16**).
- **Аппаратное ускорение (Execution Providers):**
  - **XNNPACK** — базовый провайдер по умолчанию (CPU ARM NEON FP16/INT8), работает стабильно на всех Android-устройствах.
  - **QNN (Qualcomm AI Engine Direct)** — опциональный провайдер для устройств на Snapdragon (перенос инференса на Hexagon NPU).
- **ASR-модуль:** **`sherpa-onnx`** (готовый Android AAR поверх ONNX Runtime). Закрывает VAD (Silero VAD) и декодирование речи (GigaAM/Whisper) без ручной реализации DSP/мел-спектрограмм на Kotlin.
- **Токенизация текста:** используется файл токенизатора из поставки модели (`tokenizer.json` / SentencePiece `.model`) через нативную обёртку (JNI) или `onnxruntime-extensions`, чтобы токенизация на Android совпадала с Python бит-в-бит.
- **Упрощение постпроцессинга:** операции `mean_pooling` и `L2-normalization` зашиваются прямо в ONNX-граф при экспорте. На стороне Kotlin достаточно передать подготовленный входной тензор и забрать готовый L2-нормализованный `FloatArray`.

### 2.2. Поставка весов и управление памятью (RAM)
- **Хранение весов:** модели не зашиваются внутрь APK. При первом запуске (или в бете — через локальную папку) загружается выбранный пакет пресета (`lite` / `base` / `heavy`) в локальное хранилище приложения с проверкой SHA-256 из манифеста.
- **Жизненный цикл в RAM (защита от OOM):** запрещено держать все 4 модели в памяти одновременно.
  - **В режиме поиска (foreground):** в памяти держатся только лёгкие текстовые башни (`visual_encoder.text` + `text_encoder`) для мгновенного кодирования запроса (<30 мс). На вводе текста обязателен **debounce `150–250 мс`** и отмена предыдущего незавершённого поиска (`Coroutine Job.cancel()`), чтобы не запускать инференс на каждую введённую букву.
  - **В режиме индексации (background `WorkManager`):** тяжёлые модели (`visual_encoder.vision`, `ocr_engine`, `asr_engine`) загружаются лениво, обрабатывают очередь мемов поэтапно (или батчами) и **явно выгружаются (`close()`)** после завершения задачи.

---

## 3. Форматы входов/выходов, ограничения и Kotlin-интерфейсы

Все модельно-зависимые параметры (`dim`, `image_size`, `mean`, `std`, `max_tokens`, текстовые префиксы) читаются из `preset_manifest.json`.

### 3.1. Спецификация компонентов

1. **`VisualEncoder`**
   - *Вход (картинка/кадр):* `Bitmap` → RGB, ресайз к `image_size × image_size` (обычно `224×224` или `384×384`), нормализация `(pixel / 255.0 - mean) / std`, тензор `float32[1, 3, H, W]` (NCHW).
   - *Выход (картинка/кадр):* `FloatArray(dim)`, L2-нормализованный (`||v|| = 1`).
   - *Вход (запрос):* строка → токенизатор модели → `input_ids: int64[1, max_tokens]` (обычно `max_tokens = 64`).
   - *Выход (запрос):* `FloatArray(dim)`, L2-нормализованный.
   - *Ограничения для видео/GIF:* короткие ролики (≤15 сек) — сэмплинг каждые `0.5–1.0 сек` (минимум 3 кадра); длинные видео — извлечение I-frames (`MediaExtractor`) не чаще 1 кадра в `2–3 сек`, затем дедупликация соседних кадров при `cosine_similarity > 0.90–0.92`.
   - *Авто-тегирование (`tag_bank`):* в пакете пресета рядом с `visual_encoder` поставляется предрасчитанный словарь базовых тегов (`tag_bank.bin` + список названий, ~0.7 МБ в `int8`, 500–2000 тегов) с готовыми эмбеддингами. На Android авто-тегирование нового мема сводится к одному матричному умножению вектора картинки на `tag_bank` (<1 мс) без прогрева текстовой модели.

2. **`TextEncoder`**
   - *Вход:* строка + опциональный префикс из манифеста (`query_prefix` / `passage_prefix`) → токенизатор → `input_ids: int64[1, L]`, `attention_mask: int64[1, L]` (`1 <= L <= max_tokens`, обычно `max_tokens = 256`).
   - *Выход:* `FloatArray(dim)`, L2-нормализованный.
   - *Ограничения:* длинные тексты (например, ASR 5–10 минутного видео) режутся на смысловые чанки по ~15–30 сек / 1–2 предложения с сохранением `timestamp_ms`.

3. **`OcrEngine`**
   - *Вход:* `Bitmap` (RGB, масштабирование длинной стороны до `<= 960px` с сохранением пропорций).
   - *Выход:* `List<OcrBlock(text: String, confidence: Float, bbox: RectF)>`. Блоки с `confidence < 0.5` отфильтровываются; между кадрами одного видео строки дедуплицируются.

4. **`AsrEngine` (`sherpa-onnx`)**
   - *Вход:* аудиодорожка видео, декодированная в **PCM 16 kHz, mono, `FloatArray` в диапазоне `[-1.0, 1.0]`** (или потоково чанками для длинных видео).
   - *Выход:* `List<TranscriptChunk(startMs: Long, endMs: Long, text: String)>`. Silero VAD предварительно отсекает тишину и музыку.

5. **`Reranker`**
   - *Вход:* `query: String` + список кандидатов из Stage 1 (`List<RetrievalCandidate>` со скорами и рангами по каналам: `visual`, `text`, `bm25`).
   - *Выход:* отсортированный `List<RankedMeme>` с финальным скором и лучшим совпавшим `timestamp_ms`.

### 3.2. Kotlin-интерфейсы (для заглушек и параллельной разработки)

```kotlin
data class OcrBlock(val text: String, val confidence: Float, val bbox: RectF)
data class TranscriptChunk(val startMs: Long, val endMs: Long, val text: String)

data class RetrievalCandidate(
    val memeId: Long,
    val visualScore: Float?,      // max cosine sim среди кадров
    val bestFrameMs: Long?,
    val textScore: Float?,        // max cosine sim среди текстовых чанков
    val bm25Score: Float?,        // FTS5 rank
    val matchedText: String?      // опционально для будущего Cross-Encoder
)

data class RankedMeme(
    val memeId: Long,
    val finalScore: Float,
    val bestTimestampMs: Long?,
    val channelScores: Map<String, Float>
)

interface VisualEncoder : AutoCloseable {
    val componentVersion: String
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
}

interface Reranker : AutoCloseable {
    val componentVersion: String
    suspend fun rank(query: String, candidates: List<RetrievalCandidate>): List<RankedMeme>
}
```

### 3.3. Тестовые файлы и эталонные результаты (Smoke-Test Pack)
Вместе с каждой версией ONNX-моделей с ML-стороны будет передаваться **Smoke-Test Pack**:
- 4 медиафайла: фото с текстом, фото без текста, GIF, короткое видео с речью (~5 сек).
- 3 текстовых запроса.
- `expected_outputs.json` с эталонными векторами из Python, ожидаемыми текстами OCR/ASR и эталонной выдачей топ-3.

---

## 4. Независимое версионирование компонентов и структура БД

### 4.1. Политика версионирования
**OCR и ASR НЕ переиндексируются при обновлении других моделей.** Каждый из 5 компонентов версионируется независимо через `preset_manifest.json`:

```json
{
  "preset_id": "base_v1",
  "components": {
    "visual_encoder": {
      "version": "vis-candidate-v1",
      "file": "visual_encoder.onnx",
      "sha256": "...",
      "dim": 768,
      "image_size": 224,
      "mean": [0.5, 0.5, 0.5],
      "std": [0.5, 0.5, 0.5],
      "max_tokens": 64,
      "tag_bank_file": "tag_bank_v1.bin",
      "tag_bank_sha256": "..."
    },
    "text_encoder": {
      "version": "txt-candidate-v1",
      "file": "text_encoder.onnx",
      "sha256": "...",
      "dim": 384,
      "max_tokens": 256,
      "query_prefix": "query: ",
      "passage_prefix": "passage: "
    },
    "ocr_engine": { "version": "ocr-candidate-v1", "file": "ocr.zip", "sha256": "..." },
    "asr_engine": { "version": "asr-candidate-v1", "file": "asr.zip", "sha256": "..." },
    "reranker":   { "version": "rrf-v1" }
  }
}
```

**Правила частичной переиндексации при смене пресета или версии компонента:**
- Обновился только `visual_encoder` → пересоздаётся и пересчитывается только `vec_visual` (и `visual_frames_meta`). OCR, ASR, FTS5 и `vec_text` не трогаются.
- Обновился только `text_encoder` → пересоздаётся и пересчитывается только `vec_text` из уже сохранённых в БД `text_chunks` (видео повторно не декодируется).
- Обновился `ocr_engine` или `asr_engine` → пересчитываются только строки `text_chunks` с соответствующим `source`, после чего обновляются затронутые записи в `FTS5` и `vec_text`.
- **Пользовательские таблицы (`memes`, `tags`, `meme_tags`, альбомы) никогда не удаляются и не сбрасываются.**
- **Хранение векторов:** в БД хранится индекс только для текущего активного пресета (при переключении `base ↔ lite` с разной размерностью векторные виртуальные таблицы пересоздаются под новый `dim` из манифеста и заполняются в фоне).

### 4.2. Базовая схема SQLite (`Room` + `FTS5` + `sqlite-vec` / `USearch`)

> [!NOTE]
> Для базы ~10 000 ВП-шек (~25–30k векторов с учётом кадров видео) рекомендуется скалярная квантизация векторов в `int8` при сохранении в индекс: размер индекса ~15–22 МБ, точный поиск на ARM NEON занимает <10 мс.

```sql
-- ==========================================
-- 1. ПОЛЬЗОВАТЕЛЬСКИЕ ДАННЫЕ (независимы от версий ML)
-- ==========================================
CREATE TABLE memes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    file_hash       TEXT NOT NULL UNIQUE,      -- SHA-256 файла (дедупликация + матчинг в логах)
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

-- ==========================================
-- 2. ВЕРСИИ КОМПОНЕНТОВ И ОЧЕРЕДЬ ИНДЕКСАЦИИ
-- ==========================================
CREATE TABLE ml_active_state (
    component_name  TEXT PRIMARY KEY,          -- 'visual_encoder' | 'text_encoder' | 'ocr_engine' | 'asr_engine' | 'reranker'
    active_version  TEXT NOT NULL,
    preset_id       TEXT NOT NULL,
    dim             INTEGER,                   -- актуальная размерность для векторных энкодеров
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

-- ==========================================
-- 3. ТЕКСТОВЫЕ ЧАНКИ И ПОЛНОТЕКСТ (FTS5)
-- ==========================================
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

-- ==========================================
-- 4. ВЕКТОРНЫЕ ИНДЕКСЫ (динамический dim из preset_manifest.json)
-- ==========================================
CREATE TABLE visual_frames_meta (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    meme_id         INTEGER NOT NULL REFERENCES memes(id) ON DELETE CASCADE,
    timestamp_ms    INTEGER DEFAULT 0,
    model_version   TEXT NOT NULL
);

-- Размерности {VISUAL_DIM} и {TEXT_DIM} подставляются из preset_manifest.json при инициализации/переключении пресета
-- CREATE VIRTUAL TABLE vec_visual USING vec0(embedding int8[{VISUAL_DIM}]);
-- CREATE VIRTUAL TABLE vec_text   USING vec0(embedding int8[{TEXT_DIM}]);
```

---

## 5. Логирование и формат экспорта

События поиска сохраняются локально в SQLite и экспортируются по запросу в формате **`.jsonl.gz`** (GZIP-сжатый JSON Lines).

### Два независимых режима экспорта
1. **Light Log (`events.jsonl.gz`) — диагностика и анализ ошибок (по умолчанию):**
   - **Не содержит** медиафайлов, картинок и личных текстов.
   - Содержит только поисковые запросы, `file_hash` кандидатов, скоры каналов, выбранный пользователем результат, переформулировки и latency.
   - Используется для замера реального качества, анализа провалов и сбора пар `(query, positive_hash, hard_negative_hashes)` на общей базе.
2. **Full Debug Bundle (`debug_bundle.zip`) — отдельная кнопка Opt-in для дообучения:**
   - Содержит `events.jsonl` + тексты (`ocr`, `asr`, `tags`, `user_caption`) и опционально уменьшенные превью-кадры (`224×224` JPEG) только для мемов, попавших в лог.
   - Формируется строго по явному действию пользователя.

### Схема записей в `events.jsonl`

Первой строкой каждого экспорта идёт заголовок `export_header` с характеристиками устройства и окружения (чтобы замеры latency и ошибки инференса были привязаны к конкретному железу, без дублирования в каждой строке):

```json
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

Далее следуют строки событий поиска (`search_event`):

```json
{
  "record_type": "search_event",
  "event_id": "uuid-v4",
  "session_id": "uuid-search-session",
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
    {
      "rank": 1,
      "meme_hash": "sha256_abc...",
      "scores": { "visual": 0.31, "text": 0.42, "bm25": 0.0, "final": 0.032 }
    },
    {
      "rank": 2,
      "meme_hash": "sha256_def...",
      "scores": { "visual": 0.29, "text": 0.65, "bm25": 4.1, "final": 0.031 }
    }
  ],
  "user_action": {
    "type": "share",
    "selected_meme_hash": "sha256_def...",
    "selected_rank": 2,
    "time_to_action_ms": 1450
  }
}
```
*Значения `user_action.type`: `open`, `copy`, `share`, `favorite`, `reformulate` (пользователь изменил запрос без выбора — основной сигнал hard negative), `abandon` (закрыл поиск).*

> [!NOTE]
> **Фильтрация шума в логах поиска:** в `search_event` пишется только завершённый запрос (после debounce / нажатия поиска / действия пользователя), а не промежуточные префиксы при наборе (`"ф"`, `"фи"`, `"фии"`).

Для диагностики производительности индексации и падений на реальных устройствах в тот же поток пишутся события индексации (`indexing_event`):

```json
{
  "record_type": "indexing_event",
  "event_id": "uuid-v4",
  "timestamp_ms": 1763880050000,
  "preset_id": "base_v1",
  "meme_hash": "sha256_abc...",
  "media_type": "video",
  "duration_ms": 42000,
  "stats": {
    "frames_extracted": 18,
    "frames_kept_after_dedup": 7,
    "speech_duration_ms": 21500,
    "ocr_chars": 140,
    "asr_chars": 310
  },
  "step_latency_ms": {
    "extract_media": 420,
    "visual_encode": 1150,
    "ocr": 680,
    "asr": 3200,
    "text_encode": 95
  },
  "status": "done",
  "error": null
}
```

---

## 6. Инструменты и протокол проверки качества

1. **Offline-бенчмарк (ML-сторона, Python):**
   - **Golden Eval Set:** 300–400 реальных ВП-шек (фото, GIF, видео) + 150–200 реальных пользовательских запросов с разметкой релевантных `file_hash`.
   - **Ключевые метрики:** `Recall@1`, `Recall@3`, `Recall@5`, `MRR`, `NDCG@10`, а также замеры веса модели и latency. По этим метрикам выбираются champion-модели для `lite` / `base` / `heavy`.
2. **Контроль паритета Android ↔ Python (на устройстве):**
   - Instrumented-тест на Android прогоняет **Smoke-Test Pack** (4 файла + 3 запроса) и сверяет выход с `expected_outputs.json`.
   - Критерий прохождения: `cosine_similarity(android_vec, python_vec) > 0.995` для векторных энкодеров и совпадение ключевых токенов OCR/ASR. Это гарантирует отсутствие расхождений в препроцессинге (RGB/BGR, нормализация, токенизация, ресэмплинг PCM).
