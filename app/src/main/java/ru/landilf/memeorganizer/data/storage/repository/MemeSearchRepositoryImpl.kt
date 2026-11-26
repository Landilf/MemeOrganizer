package ru.landilf.memeorganizer.data.storage.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.core.logging.LightLogStore
import ru.landilf.memeorganizer.data.ml.TextEmbeddingEngine
import ru.landilf.memeorganizer.data.ml.TextModelPackage
import ru.landilf.memeorganizer.data.search.EmbeddingCodec
import ru.landilf.memeorganizer.data.search.SimpleBm25
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.dao.SearchDocumentRow
import ru.landilf.memeorganizer.data.storage.dao.TextEmbeddingDao
import ru.landilf.memeorganizer.data.storage.entities.search.TextEmbeddingEntity
import ru.landilf.memeorganizer.data.storage.mappers.meme.MemeStorageMapper
import ru.landilf.memeorganizer.domain.model.search.MemeSearchResult
import ru.landilf.memeorganizer.domain.model.search.SearchPreparation
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemeSearchRepositoryImpl @Inject constructor(
    private val installer: LocalModelInstaller,
    private val memeDao: MemeDao,
    private val embeddingDao: TextEmbeddingDao,
    private val mapper: MemeStorageMapper,
    private val lightLog: LightLogStore
) : MemeSearchRepository {
    override suspend fun prepare(): SearchPreparation = withContext(Dispatchers.IO) {
        val modelPackage = installer.activePackage() ?: return@withContext SearchPreparation(false, 0)
        val documents = embeddingDao.searchDocuments()
        AppLogger.ml { "index.prepare documents=${documents.size}" }
        SearchPreparation(true, indexDocuments(modelPackage, documents))
    }

    override suspend fun reindexAll(): SearchPreparation = withContext(Dispatchers.IO) {
        val modelPackage = installer.activePackage() ?: return@withContext SearchPreparation(false, 0)
        val documents = embeddingDao.searchDocuments()
        AppLogger.ml { "index.rebuild documents=${documents.size}" }
        SearchPreparation(true, indexDocuments(modelPackage, documents, force = true))
    }

    override suspend fun indexMeme(memeId: Long): Unit = withContext(Dispatchers.IO) {
        val modelPackage = installer.activePackage() ?: return@withContext
        val document = embeddingDao.searchDocuments().firstOrNull { it.memeId == memeId } ?: return@withContext
        AppLogger.ml { "index.single meme_id=$memeId" }
        indexDocuments(modelPackage, listOf(document))
    }

    override suspend fun search(query: String): List<MemeSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val totalStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val modelPackage = installer.activePackage() ?: return@withContext emptyList()
        val databaseStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val documents = embeddingDao.searchDocuments()
        val embeddings = embeddingDao.getByModel(modelPackage.modelSha256).associateBy { it.memeId }
        val indexed = documents.mapNotNull { document -> embeddings[document.memeId]?.let { document to it } }
        val databaseMs = elapsedMs(databaseStartedAt)
        if (indexed.isEmpty()) {
            recordEmptySearchImpression(
                query = query,
                modelPackage = modelPackage,
                databaseMs = databaseMs,
                denseMs = null,
                bm25Ms = null,
                totalMs = elapsedMs(totalStartedAt)
            )
            return@withContext emptyList()
        }

        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        AppLogger.ml { "search.start query_length=${query.length} candidates=${indexed.size}" }
        val encodeStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val queryEmbedding = TextEmbeddingEngine.open(modelPackage).use { it.encodeQuery(query) }
        val encodeMs = elapsedMs(encodeStartedAt)
        val documentsText = indexed.map { documentText(it.first) }
        val denseStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val denseScores = indexed.map { EmbeddingCodec.cosine(queryEmbedding, EmbeddingCodec.decode(it.second.embedding, modelPackage.dimension)) }
        val denseMs = elapsedMs(denseStartedAt)
        val bm25StartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val bm25Scores = SimpleBm25(documentsText).scores(query).toList()
        val bm25Ms = elapsedMs(bm25StartedAt)
        val relevantIndexes = indexed.indices.filter { index ->
            denseScores[index] >= COSINE_THRESHOLD || bm25Scores[index] >= BM25_THRESHOLD
        }
        if (relevantIndexes.isEmpty()) {
            recordEmptySearchImpression(
                query = query,
                modelPackage = modelPackage,
                databaseMs = databaseMs,
                denseMs = denseMs,
                bm25Ms = bm25Ms,
                totalMs = elapsedMs(totalStartedAt)
            )
            return@withContext emptyList()
        }
        val fusionStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
        val normalizedDense = minMax(relevantIndexes.map(denseScores::get))
        val normalizedBm25 = minMax(relevantIndexes.map(bm25Scores::get))
        val normalizedDenseByIndex = relevantIndexes.zip(normalizedDense).toMap()
        val normalizedBm25ByIndex = relevantIndexes.zip(normalizedBm25).toMap()
        val finalScores = relevantIndexes.indices.associate { position ->
            relevantIndexes[position] to (
                modelPackage.denseWeight * normalizedDense[position] +
                    modelPackage.bm25Weight * normalizedBm25[position]
                )
        }
        val ranked = relevantIndexes.sortedByDescending { index ->
            requireNotNull(finalScores[index])
        }
        val fusionMs = elapsedMs(fusionStartedAt)
        val memesById = memeDao.getByIds(ranked.map { indexed[it].first.memeId }).associateBy { it.id }
        val searchId = UUID.randomUUID().toString()
        val impressionAtMs = System.currentTimeMillis()
        val results = ranked.mapIndexedNotNull { position, index ->
            val meme = memesById[indexed[index].first.memeId] ?: return@mapIndexedNotNull null
            MemeSearchResult(
                meme = mapper.toDomain(meme),
                searchId = searchId,
                rank = position + 1,
                impressionAtMs = impressionAtMs,
                score = requireNotNull(finalScores[index]),
                textScore = denseScores[index],
                bm25Score = bm25Scores[index]
            )
        }
        lightLog.record("search_impression", JSONObject()
            .put("search_id", searchId)
            .put("query_text", query)
            .put("model_release_tag", modelPackage.releaseTag)
            .put("relevance_filter", relevanceFilterJson())
            .put("latency_ms", JSONObject().put("database_search", databaseMs).put("model", JSONObject().put("text_encoder", encodeMs).put("visual_text_encoder", JSONObject.NULL).put("reranker", JSONObject.NULL)).put("scoring", JSONObject().put("dense", denseMs).put("bm25", bm25Ms).put("fusion", fusionMs)).put("total", elapsedMs(totalStartedAt)))
            .put("candidates", JSONArray().apply {
                ranked.take(20).forEachIndexed { position, index ->
                    val document = indexed[index].first
                    val meme = memesById[document.memeId] ?: return@forEachIndexed
                    put(JSONObject().put("rank", position + 1).put("file_hash", meme.contentHash).put("file_name", meme.fileName).put("mime_type", meme.mimeType)
                        .put("candidate_input", JSONObject().put("description", document.description).put("tags", JSONArray(document.tags.split('\u001f').filter(String::isNotBlank))).put("indexed_text", documentText(document)))
                        .put("scores", JSONObject().put("text_raw", denseScores[index]).put("bm25_raw", bm25Scores[index]).put("text_normalized", requireNotNull(normalizedDenseByIndex[index])).put("bm25_normalized", requireNotNull(normalizedBm25ByIndex[index])).put("final", requireNotNull(finalScores[index]))))
                }
            }))
        results.also { results ->
            val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            AppLogger.ml { "search.done results=${results.size} elapsed_ms=%.2f".format(java.util.Locale.ROOT, elapsedMs) }
        }
    }

    private suspend fun indexDocuments(
        modelPackage: TextModelPackage,
        documents: List<SearchDocumentRow>,
        force: Boolean = false
    ): Int {
        val pending = documents.filter { document ->
            val text = documentText(document)
            val current = embeddingDao.getByMemeId(document.memeId)
            text.isNotBlank() && (
                force ||
                    current?.modelSha256 != modelPackage.modelSha256 ||
                    current.contentFingerprint != fingerprint(text)
                )
        }
        documents.filter { documentText(it).isBlank() }.forEach { embeddingDao.deleteByMemeId(it.memeId) }
        if (pending.isEmpty()) {
            AppLogger.ml { "index.skip documents=${documents.size}" }
            return 0
        }
        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        AppLogger.ml { "index.start pending=${pending.size}" }
        TextEmbeddingEngine.open(modelPackage).use { engine ->
            pending.forEach { document ->
                val text = documentText(document)
                val itemStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
                var modelMs: Long? = null
                var databaseWriteMs: Long? = null
                try {
                    val modelStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
                    val embedding = engine.encodePassage(text)
                    modelMs = elapsedMs(modelStartedAt)
                    val databaseStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
                    embeddingDao.upsert(
                        TextEmbeddingEntity(
                            memeId = document.memeId,
                            modelSha256 = modelPackage.modelSha256,
                            contentFingerprint = fingerprint(text),
                            embedding = EmbeddingCodec.encode(embedding),
                            indexedAtMillis = System.currentTimeMillis()
                        )
                    )
                    databaseWriteMs = elapsedMs(databaseStartedAt)
                    recordIndexingEvent(
                        memeId = document.memeId,
                        modelPackage = modelPackage,
                        modelMs = modelMs,
                        databaseWriteMs = databaseWriteMs,
                        totalMs = elapsedMs(itemStartedAt),
                        status = "done"
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    recordIndexingEvent(
                        memeId = document.memeId,
                        modelPackage = modelPackage,
                        modelMs = modelMs,
                        databaseWriteMs = databaseWriteMs,
                        totalMs = elapsedMs(itemStartedAt),
                        status = "failed",
                        errorType = error.javaClass.simpleName
                    )
                    throw error
                }
            }
        }
        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
        AppLogger.ml { "index.done indexed=${pending.size} elapsed_ms=%.2f".format(java.util.Locale.ROOT, elapsedMs) }
        return pending.size
    }

    private fun documentText(document: SearchDocumentRow): String =
        listOf(document.description.trim(), document.tags.replace('\u001f', ' ').trim()).filter(String::isNotBlank).joinToString("\n")

    private fun elapsedMs(startedAt: Long): Long = (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L

    private suspend fun recordEmptySearchImpression(
        query: String,
        modelPackage: TextModelPackage,
        databaseMs: Long,
        denseMs: Long?,
        bm25Ms: Long?,
        totalMs: Long
    ) {
        lightLog.record(
            "search_impression",
            JSONObject()
                .put("search_id", UUID.randomUUID().toString())
                .put("query_text", query)
                .put("model_release_tag", modelPackage.releaseTag)
                .put("relevance_filter", relevanceFilterJson())
                .put("latency_ms", JSONObject()
                    .put("database_search", databaseMs)
                    .put("model", JSONObject()
                        .put("text_encoder", JSONObject.NULL)
                        .put("visual_text_encoder", JSONObject.NULL)
                        .put("reranker", JSONObject.NULL))
                    .put("scoring", JSONObject()
                        .put("dense", denseMs ?: JSONObject.NULL)
                        .put("bm25", bm25Ms ?: JSONObject.NULL)
                        .put("fusion", JSONObject.NULL))
                    .put("total", totalMs))
                .put("candidates", JSONArray())
        )
    }

    private fun relevanceFilterJson() = JSONObject()
        .put("raw_cosine_gte", COSINE_THRESHOLD)
        .put("raw_bm25_gte", BM25_THRESHOLD)

    private suspend fun recordIndexingEvent(
        memeId: Long,
        modelPackage: TextModelPackage,
        modelMs: Long?,
        databaseWriteMs: Long?,
        totalMs: Long,
        status: String,
        errorType: String? = null
    ) {
        val meme = memeDao.getById(memeId) ?: return
        lightLog.record(
            "indexing_event",
            JSONObject()
                .put("file_hash", meme.contentHash)
                .put("model_release_tag", modelPackage.releaseTag)
                .put("model_latency_ms", JSONObject()
                    .put("text_encoder", modelMs ?: JSONObject.NULL)
                    .put("visual_encoder", JSONObject.NULL)
                    .put("ocr_engine", JSONObject.NULL)
                    .put("asr_engine", JSONObject.NULL))
                .put("database_write_ms", databaseWriteMs ?: JSONObject.NULL)
                .put("total_ms", totalMs)
                .put("status", status)
                .put("error_type", errorType ?: JSONObject.NULL)
        )
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun minMax(scores: List<Float>): List<Float> {
        val min = scores.minOrNull() ?: return emptyList()
        val max = scores.maxOrNull() ?: return emptyList()
        if (max == min) return List(scores.size) { 0f }
        return scores.map { (it - min) / (max - min) }
    }

    private companion object {
        const val COSINE_THRESHOLD = 0.25f
        const val BM25_THRESHOLD = 2.0f
    }
}
