package ru.landilf.memeorganizer.data.storage.repository

import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemeSearchRepositoryImpl @Inject constructor(
    private val installer: LocalModelInstaller,
    private val memeDao: MemeDao,
    private val embeddingDao: TextEmbeddingDao,
    private val mapper: MemeStorageMapper
) : MemeSearchRepository {
    override suspend fun prepare(): SearchPreparation = withContext(Dispatchers.IO) {
        val modelPackage = installer.activePackage() ?: return@withContext SearchPreparation(false, 0)
        SearchPreparation(true, indexDocuments(modelPackage, embeddingDao.searchDocuments()))
    }

    override suspend fun indexMeme(memeId: Long): Unit = withContext(Dispatchers.IO) {
        val modelPackage = installer.activePackage() ?: return@withContext
        val document = embeddingDao.searchDocuments().firstOrNull { it.memeId == memeId } ?: return@withContext
        indexDocuments(modelPackage, listOf(document))
    }

    override suspend fun search(query: String): List<MemeSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val modelPackage = installer.activePackage() ?: return@withContext emptyList()
        val documents = embeddingDao.searchDocuments()
        val embeddings = embeddingDao.getByModel(modelPackage.modelSha256).associateBy { it.memeId }
        val indexed = documents.mapNotNull { document -> embeddings[document.memeId]?.let { document to it } }
        if (indexed.isEmpty()) return@withContext emptyList()

        val queryEmbedding = TextEmbeddingEngine.open(modelPackage).use { it.encodeQuery(query) }
        val documentsText = indexed.map { documentText(it.first) }
        val denseScores = indexed.map { EmbeddingCodec.cosine(queryEmbedding, EmbeddingCodec.decode(it.second.embedding, modelPackage.dimension)) }
        val bm25Scores = SimpleBm25(documentsText).scores(query).toList()
        val normalizedDense = minMax(denseScores)
        val normalizedBm25 = minMax(bm25Scores)
        val ranked = indexed.indices.sortedByDescending { index ->
            modelPackage.denseWeight * normalizedDense[index] + modelPackage.bm25Weight * normalizedBm25[index]
        }
        val memesById = memeDao.getByIds(ranked.map { indexed[it].first.memeId }).associateBy { it.id }
        ranked.mapNotNull { index ->
            val meme = memesById[indexed[index].first.memeId] ?: return@mapNotNull null
            MemeSearchResult(
                meme = mapper.toDomain(meme),
                score = modelPackage.denseWeight * normalizedDense[index] + modelPackage.bm25Weight * normalizedBm25[index],
                textScore = denseScores[index],
                bm25Score = bm25Scores[index]
            )
        }
    }

    private suspend fun indexDocuments(modelPackage: TextModelPackage, documents: List<SearchDocumentRow>): Int {
        val pending = documents.filter { document ->
            val text = documentText(document)
            val current = embeddingDao.getByMemeId(document.memeId)
            text.isNotBlank() && (current?.modelSha256 != modelPackage.modelSha256 || current.contentFingerprint != fingerprint(text))
        }
        documents.filter { documentText(it).isBlank() }.forEach { embeddingDao.deleteByMemeId(it.memeId) }
        if (pending.isEmpty()) return 0
        TextEmbeddingEngine.open(modelPackage).use { engine ->
            pending.forEach { document ->
                val text = documentText(document)
                embeddingDao.upsert(
                    TextEmbeddingEntity(
                        memeId = document.memeId,
                        modelSha256 = modelPackage.modelSha256,
                        contentFingerprint = fingerprint(text),
                        embedding = EmbeddingCodec.encode(engine.encodePassage(text)),
                        indexedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
        return pending.size
    }

    private fun documentText(document: SearchDocumentRow): String =
        listOf(document.description.trim(), document.tags.trim()).filter(String::isNotBlank).joinToString("\n")

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun minMax(scores: List<Float>): List<Float> {
        val min = scores.minOrNull() ?: return emptyList()
        val max = scores.maxOrNull() ?: return emptyList()
        if (max == min) return List(scores.size) { 0f }
        return scores.map { (it - min) / (max - min) }
    }
}
