package ru.landilf.memeorganizer.domain.model.ml

/** Persistent state for the media-analysis pipeline, separate from user-owned meme data. */
enum class MediaIndexingStatus(val storageValue: String) {
    Pending("pending"),
    Partial("partial"),
    Done("done"),
    Failed("failed")
}
