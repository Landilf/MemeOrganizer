package ru.landilf.memeorganizer.core.logging

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import ru.landilf.memeorganizer.data.storage.dao.LightLogDao
import ru.landilf.memeorganizer.data.storage.entities.logging.LightLogEventEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LightLogStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val events: LightLogDao
) {
    private val sessionId = UUID.randomUUID().toString()
    private val healthFile = AtomicFile(java.io.File(context.filesDir, "logging/health.json"))
    private val writeLock = Mutex()

    suspend fun record(recordType: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val timestamp = System.currentTimeMillis()
            val event = payload
                .put("schema_version", 1)
                .put("record_type", recordType)
                .put("session_id", sessionId)
                .put("timestamp_ms", timestamp)
            val health = readHealth().apply { created++ }
            try {
                events.insert(LightLogEventEntity(timestampMs = timestamp, recordType = recordType, payloadJson = event.toString()))
                health.pruned += events.trimTo(MAX_EVENTS)
                health.persisted++
                health.lastSuccessAtMs = timestamp
                health.lastErrorType = null
                persistHealthSafely(health)
            } catch (error: Exception) {
                health.dropped++
                health.lastErrorType = error.javaClass.simpleName
                health.lastErrorAtMs = timestamp
                persistHealthSafely(health)
                AppLogger.app { "light_log.write_failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    suspend fun healthJson(): JSONObject = withContext(Dispatchers.IO) {
        writeLock.withLock { readHealth().toJson() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writeLock.withLock {
            events.clear()
            healthFile.baseFile.delete()
        }
    }

    private fun readHealth(): Health = try {
        if (!healthFile.baseFile.isFile) Health() else Health.fromJson(JSONObject(healthFile.readFully().toString(Charsets.UTF_8)))
    } catch (_: Exception) { Health() }

    private fun writeHealth(health: Health) {
        healthFile.baseFile.parentFile?.mkdirs()
        val output = healthFile.startWrite()
        try { output.write(health.toJson().toString().toByteArray()); healthFile.finishWrite(output) }
        catch (error: Exception) { healthFile.failWrite(output); throw error }
    }

    private fun persistHealthSafely(health: Health) {
        runCatching { writeHealth(health) }
            .onFailure { AppLogger.app { "light_log.health_write_failed type=${it.javaClass.simpleName}" } }
    }

    private data class Health(
        var created: Long = 0,
        var persisted: Long = 0,
        var dropped: Long = 0,
        var pruned: Long = 0,
        var lastSuccessAtMs: Long? = null,
        var lastErrorType: String? = null,
        var lastErrorAtMs: Long? = null
    ) {
        fun toJson() = JSONObject()
            .put("events_created", created)
            .put("events_persisted", persisted)
            .put("events_dropped", dropped)
            .put("events_pruned", pruned)
            .put("last_success_at_ms", lastSuccessAtMs)
            .put("last_error_type", lastErrorType)
            .put("last_error_at_ms", lastErrorAtMs)

        companion object {
            fun fromJson(json: JSONObject) = Health(
                created = json.optLong("events_created"),
                persisted = json.optLong("events_persisted"),
                dropped = json.optLong("events_dropped"),
                pruned = json.optLong("events_pruned"),
                lastSuccessAtMs = json.optLong("last_success_at_ms").takeIf { it > 0 },
                lastErrorType = json.optString("last_error_type").takeIf { it.isNotBlank() },
                lastErrorAtMs = json.optLong("last_error_at_ms").takeIf { it > 0 }
            )
        }
    }

    private companion object { const val MAX_EVENTS = 10_000 }
}
