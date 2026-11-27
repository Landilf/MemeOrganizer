package ru.landilf.memeorganizer.data.ml

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ModelPackageComponent(
    val name: String,
    val modelFile: File,
    val runtime: String,
    val auxiliaryFiles: Map<String, File>
)

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
    val releaseTag: String,
    val components: Map<String, ModelPackageComponent>
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
                require(
                    file.toPath().startsWith(root.toPath()) && file != root && file.isFile
                ) { "Invalid package file" }
                return file
            }

            val model = asset(manifest.getString("model_file"))
            val tokenizer = manifest.getJSONObject("tokenizer")
            val tokenizerFile = asset(tokenizer.getString("recommended_asset"))
            val expectedHash = manifest.getJSONObject("provenance").getString("onnx_sha256")
            val actualHash = verifyHash(model, expectedHash)
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
            val components = loadComponents(manifest.optJSONArray("components"), ::asset)
            components[TEXT_ENCODER_COMPONENT]?.let { component ->
                require(component.modelFile == model && component.runtime == ONNX_RUNTIME) {
                    "Invalid text encoder component"
                }
            }
            return TextModelPackage(
                model,
                tokenizerFile,
                dimension,
                maxLength,
                prefixes.getString("query"),
                prefixes.getString("passage"),
                paddingTokenId,
                actualHash,
                denseWeight,
                bm25Weight,
                releaseTag,
                components
            )
        }

        private fun loadComponents(
            json: JSONArray?,
            asset: (String) -> File
        ): Map<String, ModelPackageComponent> {
            if (json == null) return emptyMap()
            require(json.length() > 0) { "Model components must not be empty" }
            val components = buildMap {
                repeat(json.length()) { index ->
                    val component = json.getJSONObject(index)
                    val name = component.getString("name")
                    require(name in SUPPORTED_COMPONENTS && name !in this) { "Unsupported component" }
                    val model = asset(component.getString("path"))
                    verifyHash(model, component.getString("sha256"))
                    val runtime = component.getString("runtime")
                    require(runtime == expectedRuntime(name)) { "Unsupported component runtime" }
                    validateTensorSpecs(component.getJSONArray("inputs"))
                    validateTensorSpecs(component.getJSONArray("outputs"))
                    require(component.optJSONObject("preprocessing") != null) {
                        "Missing component preprocessing"
                    }
                    val auxiliaryFiles = component.optJSONObject("auxiliary_files")
                        ?.let { files ->
                            buildMap {
                                files.keys().forEach { path ->
                                    val file = asset(path)
                                    verifyHash(file, files.getString(path))
                                    put(path, file)
                                }
                            }
                        }
                        .orEmpty()
                    put(name, ModelPackageComponent(name, model, runtime, auxiliaryFiles))
                }
            }
            require(components.keys == SUPPORTED_COMPONENTS) { "Incomplete multimodal package" }
            return components
        }

        private fun validateTensorSpecs(tensors: JSONArray) {
            require(tensors.length() > 0) { "Component tensors are missing" }
            repeat(tensors.length()) { index ->
                val tensor = tensors.getJSONObject(index)
                require(tensor.getString("name").isNotBlank()) { "Invalid tensor name" }
                require(tensor.getString("dtype") in SUPPORTED_DTYPES) { "Unsupported tensor type" }
                val shape = tensor.getJSONArray("shape")
                require(shape.length() > 0) { "Invalid tensor shape" }
                repeat(shape.length()) { dimensionIndex ->
                    val dimension = shape.get(dimensionIndex)
                    require(
                        (dimension is Number && dimension.toLong() > 0) ||
                            (dimension is String && dimension.matches(Regex("[a-z_][a-z0-9_]*")))
                    ) { "Invalid tensor dimension" }
                }
            }
        }

        private fun verifyHash(file: File, expectedHash: String): String {
            require(expectedHash.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256" }
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(expectedHash, ignoreCase = true)) { "Model SHA-256 mismatch" }
            return actualHash
        }

        private fun expectedRuntime(component: String): String = when (component) {
            ASR_COMPONENT -> SHERPA_RUNTIME
            else -> ONNX_RUNTIME
        }

        private val RELEASE_TAG = Regex("v\\d+\\.\\d+\\.\\d+-mobile")
        private const val TEXT_ENCODER_COMPONENT = "text_encoder"
        private const val ASR_COMPONENT = "asr"
        private const val ONNX_RUNTIME = "onnxruntime"
        private const val SHERPA_RUNTIME = "sherpa-onnx"
        private val SUPPORTED_COMPONENTS = setOf(
            TEXT_ENCODER_COMPONENT,
            "ocr_det",
            "ocr_rec",
            ASR_COMPONENT,
            "fusion"
        )
        private val SUPPORTED_DTYPES = setOf("float32", "int64", "bool")
    }
}
