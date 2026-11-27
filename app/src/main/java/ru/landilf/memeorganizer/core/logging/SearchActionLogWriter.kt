package ru.landilf.memeorganizer.core.logging

import org.json.JSONObject
import javax.inject.Inject

class SearchActionLogWriter @Inject constructor(
    private val lightLog: LightLogStore
) {
    suspend fun record(
        searchId: String,
        actionType: String,
        impressionAtMs: Long,
        fileHash: String? = null,
        rank: Int? = null
    ) {
        val payload = JSONObject()
            .put("search_id", searchId)
            .put("action_type", actionType)
            .put("time_since_impression_ms", System.currentTimeMillis() - impressionAtMs)
        if (fileHash != null) payload.put("file_hash", fileHash)
        if (rank != null) payload.put("rank", rank)
        lightLog.record("search_action", payload)
    }
}
