package ru.landilf.memeorganizer.data.ml

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class TextModelPackage(
    val modelFile: File,
    val tokenizerFile: File,
    val dimension: Int,
    val maxLength: Int,
    val queryPrefix: String,
    val passagePrefix: String,
    val paddingTokenId: Long,
    val modelSha256: String,
    val denseWeight: Float,
    val bm25Weight: Float,
    val releaseTag: String
) {
    companion object {
        /** Validates an already extracted package. This does not extract untrusted archives. */
        fun load(directory: File): TextModelPackage {
            val root = directory.canonicalFile
            val manifest = JSONObject(File(root, "preset_manifest.json").readText())
            require(manifest.getString("schema_version") == "1.1.0") { "Unsupported manifest version" }
            require(manifest.getString("component") == "text_encoder") { "Expected text encoder" }
            fun asset(path: String): File {
                val file = File(root, path).canonicalFile
                require(file.toPath().startsWith(root.toPath()) && file != root && file.isFile) { "Invalid package file" }
                return file
            }
            val model = asset(manifest.getString("model_file"))
            val tokenizer = manifest.getJSONObject("tokenizer")
            val tokenizerFile = asset(tokenizer.getString("recommended_asset"))
            val expectedHash = manifest.getJSONObject("provenance").getString("onnx_sha256")
            require(expectedHash.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256" }
            val hash = MessageDigest.getInstance("SHA-256")
            model.inputStream().buffered().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(expectedHash, ignoreCase = true)) { "Model SHA-256 mismatch" }
            val dimension = manifest.getInt("embedding_dimension")
            val maxLength = manifest.getInt("max_sequence_length")
            require(dimension in 1..8192 && maxLength in 1..4096 && tokenizer.getInt("max_length") == maxLength)
            require(tokenizer.getString("padding") == "max_length" && tokenizer.getBoolean("truncation")) { "Unsupported tokenizer configuration" }
            val paddingTokenId = tokenizer.getJSONObject("special_token_ids").getLong("pad")
            require(paddingTokenId >= 0)
            val prefixes = manifest.getJSONObject("prefixes")
            val scoring = manifest.getJSONObject("hybrid_scoring")
            val denseWeight = scoring.getDouble("dense_weight").toFloat()
            val bm25Weight = scoring.getDouble("bm25_weight").toFloat()
            require(denseWeight >= 0f && bm25Weight >= 0f && denseWeight + bm25Weight > 0f) { "Invalid hybrid weights" }
            val releaseTag = manifest.getString("release_tag")
            require(RELEASE_TAG.matches(releaseTag)) { "Invalid release tag" }
            return TextModelPackage(
                model, tokenizerFile, dimension, maxLength, prefixes.getString("query"), prefixes.getString("passage"),
                paddingTokenId, actualHash, denseWeight, bm25Weight, releaseTag
            )
        }

        private val RELEASE_TAG = Regex("v\\d+\\.\\d+\\.\\d+-mobile")
    }
}
