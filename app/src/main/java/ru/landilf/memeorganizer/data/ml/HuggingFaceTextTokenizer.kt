package ru.landilf.memeorganizer.data.ml

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TokenizedText(val inputIds: LongArray, val attentionMask: LongArray)

class HuggingFaceTextTokenizer private constructor(
    private val tokenizer: HuggingFaceTokenizer,
    private val modelPackage: TextModelPackage
) : AutoCloseable {
    private val lock = Mutex()
    private var closed = false

    suspend fun encodeQuery(text: String): TokenizedText = encode(modelPackage.queryPrefix + text)
    suspend fun encodePassage(text: String): TokenizedText = encode(modelPackage.passagePrefix + text)

    private suspend fun encode(text: String): TokenizedText = withContext(Dispatchers.Default) {
        lock.withLock {
            check(!closed)
            val encoding = tokenizer.encode(text)
            val ids = encoding.ids
            require(ids.size <= modelPackage.maxLength)
            val padded = LongArray(modelPackage.maxLength) { modelPackage.paddingTokenId }
            val mask = LongArray(modelPackage.maxLength)
            ids.copyInto(padded)
            encoding.attentionMask.copyInto(mask)
            TokenizedText(padded, mask)
        }
    }

    override fun close() {
        kotlinx.coroutines.runBlocking { lock.withLock { if (!closed) { closed = true; tokenizer.close() } } }
    }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): HuggingFaceTextTokenizer = withContext(Dispatchers.IO) {
            val options = mapOf("addSpecialTokens" to "true", "padding" to "false", "truncation" to "true", "maxLength" to modelPackage.maxLength.toString(), "modelMaxLength" to modelPackage.maxLength.toString())
            HuggingFaceTextTokenizer(HuggingFaceTokenizer.newInstance(modelPackage.tokenizerFile.toPath(), options), modelPackage)
        }
    }
}
