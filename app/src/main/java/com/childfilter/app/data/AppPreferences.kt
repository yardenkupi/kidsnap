package com.childfilter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "child_filter_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val REFERENCE_EMBEDDING = stringPreferencesKey("reference_embedding")
        private val SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val KNOWN_GROUPS = stringSetPreferencesKey("known_groups")
        private val SELECTED_GROUPS = stringSetPreferencesKey("selected_groups")
        private val LAST_ACTIVE_GROUP = stringPreferencesKey("last_active_group")
        private val CHILDREN_JSON = stringPreferencesKey("children_json")
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val TOTAL_PROCESSED = stringPreferencesKey("total_processed")
        private val TOTAL_MATCHED = stringPreferencesKey("total_matched")
        private val LAST_MATCH_TIME = stringPreferencesKey("last_match_time")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
        private val ACTIVITY_LOG = stringPreferencesKey("activity_log")

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    // ── Dark mode ──

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled
        }
    }

    fun getDarkMode(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[DARK_MODE] ?: false
        }
    }

    // ── Notifications ──

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    fun getNotificationsEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[NOTIFICATIONS_ENABLED] ?: true
        }
    }

    // ── Tutorial ──

    suspend fun setHasSeenTutorial(seen: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HAS_SEEN_TUTORIAL] = seen
        }
    }

    fun hasSeenTutorial(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[HAS_SEEN_TUTORIAL] ?: false
        }
    }

    // ── Existing methods ──

    suspend fun saveEmbedding(embedding: FloatArray) {
        val serialized = embedding.joinToString(",")
        context.dataStore.edit { prefs ->
            prefs[REFERENCE_EMBEDDING] = serialized
        }
    }

    fun getEmbedding(): Flow<FloatArray?> {
        return context.dataStore.data.map { prefs ->
            prefs[REFERENCE_EMBEDDING]?.let { raw ->
                if (raw.isBlank()) null
                else raw.split(",").map { it.toFloat() }.toFloatArray()
            }
        }
    }

    suspend fun saveThreshold(threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[SIMILARITY_THRESHOLD] = threshold
        }
    }

    fun getThreshold(): Flow<Float> {
        return context.dataStore.data.map { prefs ->
            prefs[SIMILARITY_THRESHOLD] ?: 0.75f
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SERVICE_ENABLED] = enabled
        }
    }

    fun isServiceEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[SERVICE_ENABLED] ?: false
        }
    }

    // ── Group management ──

    suspend fun addKnownGroup(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KNOWN_GROUPS] ?: emptySet()
            prefs[KNOWN_GROUPS] = current + name
        }
    }

    fun getKnownGroups(): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[KNOWN_GROUPS] ?: emptySet()
        }
    }

    suspend fun saveSelectedGroups(groups: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_GROUPS] = groups
        }
    }

    fun getSelectedGroups(): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[SELECTED_GROUPS] ?: emptySet()
        }
    }

    suspend fun setLastActiveGroup(name: String, time: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_ACTIVE_GROUP] = "$name|$time"
        }
    }

    fun getLastActiveGroup(): Flow<Pair<String, Long>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[LAST_ACTIVE_GROUP]
            if (raw != null && raw.contains("|")) {
                val parts = raw.split("|", limit = 2)
                Pair(parts[0], parts[1].toLongOrDefault(0L))
            } else {
                Pair("", 0L)
            }
        }
    }

    // ── Children management ──

    suspend fun saveChildren(children: List<ChildProfile>) {
        val json = buildString {
            append("[")
            children.forEachIndexed { index, child ->
                if (index > 0) append(",")
                append("{")
                append("\"id\":\"${escapeJson(child.id)}\",")
                append("\"name\":\"${escapeJson(child.name)}\",")
                append("\"embedding\":\"${child.embedding.joinToString(",")}\",")
                append("\"photoUri\":${if (child.photoUri != null) "\"${escapeJson(child.photoUri)}\"" else "null"}")
                append("}")
            }
            append("]")
        }
        context.dataStore.edit { prefs ->
            prefs[CHILDREN_JSON] = json
        }
    }

    fun getChildren(): Flow<List<ChildProfile>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[CHILDREN_JSON] ?: return@map emptyList()
            parseChildrenJson(json)
        }
    }

    // ── Onboarding ──

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    fun hasCompletedOnboarding(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[HAS_COMPLETED_ONBOARDING] ?: false
        }
    }

    // ── Stats ──

    suspend fun incrementProcessed() {
        context.dataStore.edit { prefs ->
            val current = prefs[TOTAL_PROCESSED]?.toIntOrNull() ?: 0
            prefs[TOTAL_PROCESSED] = (current + 1).toString()
        }
    }

    suspend fun incrementMatched() {
        context.dataStore.edit { prefs ->
            val current = prefs[TOTAL_MATCHED]?.toIntOrNull() ?: 0
            prefs[TOTAL_MATCHED] = (current + 1).toString()
            prefs[LAST_MATCH_TIME] = System.currentTimeMillis().toString()
        }
    }

    fun getStats(): Flow<Triple<Int, Int, Long>> {
        return context.dataStore.data.map { prefs ->
            val processed = prefs[TOTAL_PROCESSED]?.toIntOrNull() ?: 0
            val matched = prefs[TOTAL_MATCHED]?.toIntOrNull() ?: 0
            val lastMatch = prefs[LAST_MATCH_TIME]?.toLongOrNull() ?: 0L
            Triple(processed, matched, lastMatch)
        }
    }

    suspend fun resetStats() {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_PROCESSED] = "0"
            prefs[TOTAL_MATCHED] = "0"
            prefs[LAST_MATCH_TIME] = "0"
        }
    }

    suspend fun clearChildren() {
        context.dataStore.edit { prefs ->
            prefs[CHILDREN_JSON] = "[]"
        }
    }

    /** Wipes every key — for unit tests only. */
    internal suspend fun clearAllForTest() {
        context.dataStore.edit { it.clear() }
    }

    // ── Activity Log ──

    suspend fun addLogEntry(entry: LogEntry) {
        addLogEntry(entry.type, entry.details, entry.confidence)
    }

    suspend fun addLogEntry(type: String, details: String, confidence: String? = null) {
        context.dataStore.edit { prefs ->
            val existing = prefs[ACTIVITY_LOG] ?: "[]"
            val array = try { JSONArray(existing) } catch (_: Exception) { JSONArray() }
            val obj = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("type", type)
                put("details", details)
                if (confidence != null) put("confidence", confidence)
            }
            // Prepend: build a new array with entry first
            val newArray = JSONArray()
            newArray.put(obj)
            val limit = minOf(array.length(), 49) // keep max 50 total
            for (i in 0 until limit) {
                newArray.put(array.getJSONObject(i))
            }
            prefs[ACTIVITY_LOG] = newArray.toString()
        }
    }

    fun getActivityLog(): Flow<List<LogEntry>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[ACTIVITY_LOG] ?: return@map emptyList()
            val array = try { JSONArray(raw) } catch (_: Exception) { return@map emptyList<LogEntry>() }
            val result = mutableListOf<LogEntry>()
            for (i in 0 until minOf(array.length(), 50)) {
                try {
                    val obj = array.getJSONObject(i)
                    result.add(
                        LogEntry(
                            timestamp = obj.getLong("timestamp"),
                            type = obj.getString("type"),
                            details = obj.getString("details"),
                            confidence = if (obj.has("confidence")) obj.getString("confidence") else null
                        )
                    )
                } catch (_: Exception) { /* skip malformed */ }
            }
            result
        }
    }

    suspend fun clearActivityLog() {
        context.dataStore.edit { prefs ->
            prefs[ACTIVITY_LOG] = "[]"
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun parseChildrenJson(json: String): List<ChildProfile> {
        val result = mutableListOf<ChildProfile>()
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed == "[]") return result

        // Remove outer brackets
        val inner = trimmed.substring(1, trimmed.length - 1)
        val objects = splitJsonObjects(inner)

        for (obj in objects) {
            try {
                val id = extractJsonString(obj, "id") ?: continue
                val name = extractJsonString(obj, "name") ?: continue
                val embeddingStr = extractJsonString(obj, "embedding") ?: continue
                val photoUri = extractJsonStringNullable(obj, "photoUri")
                val embedding = embeddingStr.split(",").map { it.trim().toFloat() }.toFloatArray()
                result.add(ChildProfile(id, name, embedding, photoUri))
            } catch (_: Exception) {
                // Skip malformed entries
            }
        }
        return result
    }

    private fun splitJsonObjects(inner: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in inner.indices) {
            when (inner[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(inner.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractJsonString(obj: String, key: String): String? {
        val pattern = "\"$key\":\""
        val start = obj.indexOf(pattern)
        if (start < 0) return null
        val valueStart = start + pattern.length
        val valueEnd = findUnescapedQuote(obj, valueStart)
        return if (valueEnd > valueStart) unescapeJson(obj.substring(valueStart, valueEnd)) else null
    }

    private fun extractJsonStringNullable(obj: String, key: String): String? {
        val nullPattern = "\"$key\":null"
        if (obj.contains(nullPattern)) return null
        return extractJsonString(obj, key)
    }

    private fun findUnescapedQuote(s: String, from: Int): Int {
        var i = from
        while (i < s.length) {
            if (s[i] == '"' && (i == 0 || s[i - 1] != '\\')) return i
            i++
        }
        return -1
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}

private fun String.toLongOrDefault(default: Long): Long {
    return try {
        this.toLong()
    } catch (_: NumberFormatException) {
        default
    }
}
