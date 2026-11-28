package ru.landilf.memeorganizer.domain.model.ml

/**
 * Isolates speech-recognition runtime details from media indexing.
 * The caller supplies mono 16 kHz PCM samples in the [-1, 1] range.
 */
interface AsrEngine : AutoCloseable {
    suspend fun transcribe(pcm16kMono: FloatArray): String
}
