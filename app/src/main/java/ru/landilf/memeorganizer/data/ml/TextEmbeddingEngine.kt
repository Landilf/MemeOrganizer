package ru.landilf.memeorganizer.data.ml

/** Converts raw text to embeddings using the tokenizer and encoder from one package. */
class TextEmbeddingEngine private constructor(
    private val tokenizer: HuggingFaceTextTokenizer,
    private val encoder: OnnxTextEncoder
) : AutoCloseable {
    suspend fun encodeQuery(text: String): FloatArray {
        val tokens = tokenizer.encodeQuery(text)
        return encoder.encode(tokens.inputIds, tokens.attentionMask)
    }

    suspend fun encodePassage(text: String): FloatArray {
        val tokens = tokenizer.encodePassage(text)
        return encoder.encode(tokens.inputIds, tokens.attentionMask)
    }

    override fun close() {
        try {
            tokenizer.close()
        } finally {
            encoder.close()
        }
    }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): TextEmbeddingEngine {
            val tokenizer = HuggingFaceTextTokenizer.open(modelPackage)
            try {
                return TextEmbeddingEngine(tokenizer, OnnxTextEncoder.open(modelPackage))
            } catch (error: Throwable) {
                tokenizer.close(); throw error
            }
        }
    }
}
