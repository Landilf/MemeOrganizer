package ru.landilf.memeorganizer.domain.model.ml

/** Persistent state for the media-analysis pipeline, separate from user-owned meme data. */
enum class MediaIndexingStatus(val storageValue: String) {
    Pending("pending"),
    Running("running"),
    Partial("partial"),
    Done("done"),
    Failed("failed");

    companion object {
        fun fromStorageValue(value: String): MediaIndexingStatus? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}
