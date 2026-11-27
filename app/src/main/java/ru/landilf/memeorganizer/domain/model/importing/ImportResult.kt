package ru.landilf.memeorganizer.domain.model.importing

import ru.landilf.memeorganizer.domain.model.meme.Meme

sealed interface ImportResult {
    data class Imported(val meme: Meme) : ImportResult
    data class Duplicate(val existingMeme: Meme) : ImportResult
    data class Failed(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    SourceUnavailable,
    UnsupportedMedia,
    InsufficientStorage,
    StorageError
}
