package ru.landilf.memeorganizer.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import ru.landilf.memeorganizer.BuildConfig
import java.io.File

/** Debug-only logs split by source so Logcat can filter ML work independently. */
object AppLogger {
    private const val APP_TAG = "MemeOrg/App"
    private const val ML_TAG = "MemeOrg/ML"

    private var store: DebugLogStore? = null

    fun initialize(context: Context) {
        if (BuildConfig.DEBUG && store == null) store = DebugLogStore(context.applicationContext)
    }

    fun app(message: () -> String) {
        if (BuildConfig.DEBUG) {
            val value = message()
            Log.d(APP_TAG, value)
            store?.append(channel = "app", level = "debug", message = value)
        }
    }

    fun ml(message: () -> String) {
        if (BuildConfig.DEBUG) {
            val value = message()
            Log.d(ML_TAG, value)
            store?.append(channel = "ml", level = "debug", message = value)
        }
    }

    fun mlError(error: Throwable, message: () -> String) {
        if (BuildConfig.DEBUG) {
            val value = message()
            Log.e(ML_TAG, value, error)
            store?.append(
                channel = "ml",
                level = "error",
                message = value,
                errorType = error.javaClass.simpleName
            )
        }
    }

    suspend fun clearPersistedEvents() {
        if (BuildConfig.DEBUG) store?.clear()
    }
}

internal class DebugLogStore(context: Context) {
    private val file = File(context.filesDir, "debug/events.jsonl")
    private val legacyFile = File(context.filesDir, "debug/events.onl")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    fun append(channel: String, level: String, message: String, errorType: String? = null) {
        scope.launch {
            writeLock.withLock {
                file.parentFile?.mkdirs()
                if (file.length() > MAX_LOG_SIZE_BYTES) file.writeText("")
                val record = JSONObject()
                    .put("record_type", "debug_event")
                    .put("timestamp_ms", System.currentTimeMillis())
                    .put("channel", channel)
                    .put("level", level)
                    .put("message", message)
                if (errorType != null) record.put("error_type", errorType)
                file.appendText(record.toString() + "\n")
            }
        }
    }

    suspend fun clear() {
        writeLock.withLock {
            file.delete()
            legacyFile.delete()
        }
    }

    companion object {
        const val MAX_LOG_SIZE_BYTES = 2L * 1024L * 1024L
    }
}
