package com.bubble.screentools.lens

import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchEngineType
import com.bubble.screentools.model.SearchImageMethod
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * Registry & persistence manager for built-in and custom Visual Search and Text Search engines.
 * Strictly separates Image Search handoff methods from Text Query URL templates and validates URLs.
 */
class SearchEngineRegistry(private val preferences: BubblePreferences) {

    fun getAllEngines(): List<SearchEngineItem> {
        val stored = preferences.searchEnginesJson?.let { parseJson(it) }
        if (stored.isNullOrEmpty()) {
            return BUILT_IN_ENGINES
        }
        // Merge any newly added built-ins so upgrades never lose new engines
        val byId = stored.associateBy { it.id }.toMutableMap()
        var nextOrder = (stored.maxOfOrNull { it.order } ?: 0) + 1
        for (builtIn in BUILT_IN_ENGINES) {
            if (!byId.containsKey(builtIn.id)) {
                byId[builtIn.id] = builtIn.copy(order = nextOrder++)
            }
        }
        return byId.values.sortedBy { it.order }
    }

    fun getVisualEngines(enabledOnly: Boolean = false): List<SearchEngineItem> {
        return getAllEngines()
            .filter { it.type == SearchEngineType.VISUAL && (!enabledOnly || it.enabled) }
            .sortedBy { it.order }
    }

    fun getTextEngines(enabledOnly: Boolean = false): List<SearchEngineItem> {
        return getAllEngines()
            .filter { it.type == SearchEngineType.TEXT && (!enabledOnly || it.enabled) }
            .sortedBy { it.order }
    }

    fun getActiveVisualEngine(): SearchEngineItem {
        val enabled = getVisualEngines(enabledOnly = true).ifEmpty { getVisualEngines(enabledOnly = false) }
        val preferredId = if (preferences.rememberLastSearchEngine) {
            preferences.lastUsedVisualEngineId
        } else {
            preferences.defaultVisualEngineId
        }
        return enabled.firstOrNull { it.id == preferredId }
            ?: enabled.firstOrNull { it.id == preferences.defaultVisualEngineId }
            ?: enabled.first()
    }

    fun getActiveTextEngine(): SearchEngineItem {
        val enabled = getTextEngines(enabledOnly = true).ifEmpty { getTextEngines(enabledOnly = false) }
        return enabled.firstOrNull { it.id == preferences.defaultTextEngineId }
            ?: enabled.first()
    }

    fun setEngineEnabled(id: String, enabled: Boolean): Boolean {
        val all = getAllEngines().toMutableList()
        val target = all.firstOrNull { it.id == id } ?: return false
        if (!enabled) {
            val sameTypeEnabled = all.count { it.type == target.type && it.enabled }
            if (sameTypeEnabled <= 1) return false // Always keep at least one engine of each type active
        }
        val updated = all.map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveAll(updated)
        return true
    }

    fun moveEngine(id: String, moveUp: Boolean): Boolean {
        val all = getAllEngines().toMutableList()
        val target = all.firstOrNull { it.id == id } ?: return false
        val sameType = all.filter { it.type == target.type }.sortedBy { it.order }.toMutableList()
        val idx = sameType.indexOfFirst { it.id == id }
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (idx < 0 || swapIdx !in sameType.indices) return false

        val tmp = sameType[idx]
        sameType[idx] = sameType[swapIdx]
        sameType[swapIdx] = tmp

        val reorderedSameType = sameType.mapIndexed { index, item -> item.copy(order = index) }
        val others = all.filter { it.type != target.type }
        saveAll(reorderedSameType + others)
        return true
    }

    fun reorderEngine(id: String, toIndex: Int): Boolean {
        val all = getAllEngines().toMutableList()
        val target = all.firstOrNull { it.id == id } ?: return false
        val sameType = all.filter { it.type == target.type }.sortedBy { it.order }.toMutableList()
        val fromIdx = sameType.indexOfFirst { it.id == id }
        val clampedTo = toIndex.coerceIn(0, sameType.lastIndex)
        if (fromIdx < 0 || fromIdx == clampedTo) return false

        val moved = sameType.removeAt(fromIdx)
        sameType.add(clampedTo, moved)

        val reorderedSameType = sameType.mapIndexed { index, item -> item.copy(order = index) }
        val others = all.filter { it.type != target.type }
        saveAll(reorderedSameType + others)
        return true
    }

    fun addOrUpdateCustomEngine(item: SearchEngineItem): Result<SearchEngineItem> = runCatching {
        val validated = validateCustomEngine(item).getOrThrow()
        val all = getAllEngines().toMutableList()
        val existingIdx = all.indexOfFirst { it.id == validated.id }
        if (existingIdx >= 0) {
            all[existingIdx] = validated
        } else {
            val maxOrder = all.filter { it.type == validated.type }.maxOfOrNull { it.order } ?: 0
            all.add(validated.copy(order = maxOrder + 1, isBuiltIn = false))
        }
        saveAll(all)
        validated
    }

    fun deleteCustomEngine(id: String): Boolean {
        val all = getAllEngines()
        val target = all.firstOrNull { it.id == id } ?: return false
        if (target.isBuiltIn) return false
        val updated = all.filterNot { it.id == id }
        saveAll(updated)
        if (preferences.defaultVisualEngineId == id) {
            preferences.defaultVisualEngineId = ID_GOOGLE_LENS
        }
        if (preferences.defaultTextEngineId == id) {
            preferences.defaultTextEngineId = ID_TEXT_GOOGLE
        }
        return true
    }

    fun resetToDefaults() {
        preferences.searchEnginesJson = null
        preferences.defaultVisualEngineId = ID_GOOGLE_LENS
        preferences.lastUsedVisualEngineId = ID_GOOGLE_LENS
        preferences.defaultTextEngineId = ID_TEXT_GOOGLE
    }

    private fun saveAll(items: List<SearchEngineItem>) {
        preferences.searchEnginesJson = serializeJson(items)
    }

    companion object {
        const val ID_GOOGLE_LENS = "visual_google_lens"
        const val ID_YANDEX = "visual_yandex"
        const val ID_TINEYE = "visual_tineye"
        const val ID_BING_VISUAL = "visual_bing"
        const val ID_SAUCENAO = "visual_saucenao"
        const val ID_TRACE_MOE = "visual_tracemoe"
        const val ID_OPEN_WITH = "visual_open_with"

        const val ID_TEXT_GOOGLE = "text_google"
        const val ID_TEXT_DUCKDUCKGO = "text_duckduckgo"
        const val ID_TEXT_BING = "text_bing"
        const val ID_TEXT_WIKIPEDIA = "text_wikipedia"

        val BUILT_IN_ENGINES: List<SearchEngineItem> = listOf(
            // Visual / Image Search Engines
            SearchEngineItem(
                id = ID_GOOGLE_LENS,
                name = "Google Lens",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://lens.google.com/",
                imageMethod = SearchImageMethod.GOOGLE_LENS_NATIVE,
                targetPackage = "com.google.ar.lens",
                mimeType = "image/png",
                enabled = true,
                isBuiltIn = true,
                order = 0
            ),
            SearchEngineItem(
                id = ID_YANDEX,
                name = "Yandex Images",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://yandex.com/images/",
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                mimeType = "image/jpeg",
                enabled = true,
                isBuiltIn = true,
                order = 1
            ),
            SearchEngineItem(
                id = ID_TINEYE,
                name = "TinEye",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://tineye.com/",
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                mimeType = "image/jpeg",
                enabled = true,
                isBuiltIn = true,
                order = 2
            ),
            SearchEngineItem(
                id = ID_BING_VISUAL,
                name = "Bing Visual Search",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://www.bing.com/visualsearch",
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                mimeType = "image/jpeg",
                enabled = true,
                isBuiltIn = true,
                order = 3
            ),
            SearchEngineItem(
                id = ID_SAUCENAO,
                name = "SauceNAO",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://saucenao.com/",
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                mimeType = "image/jpeg",
                enabled = true,
                isBuiltIn = true,
                order = 4
            ),
            SearchEngineItem(
                id = ID_TRACE_MOE,
                name = "trace.moe",
                type = SearchEngineType.VISUAL,
                baseUrl = "https://trace.moe/",
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                mimeType = "image/jpeg",
                enabled = true,
                isBuiltIn = true,
                order = 5
            ),
            SearchEngineItem(
                id = ID_OPEN_WITH,
                name = "Open With / Share",
                type = SearchEngineType.VISUAL,
                baseUrl = "",
                imageMethod = SearchImageMethod.ANDROID_OPEN_WITH,
                mimeType = "image/png",
                enabled = true,
                isBuiltIn = true,
                order = 6
            ),
            // Text Search Engines
            SearchEngineItem(
                id = ID_TEXT_GOOGLE,
                name = "Google Search",
                type = SearchEngineType.TEXT,
                baseUrl = "https://www.google.com/",
                textQueryTemplate = "https://www.google.com/search?q={query}",
                enabled = true,
                isBuiltIn = true,
                order = 0
            ),
            SearchEngineItem(
                id = ID_TEXT_DUCKDUCKGO,
                name = "DuckDuckGo",
                type = SearchEngineType.TEXT,
                baseUrl = "https://duckduckgo.com/",
                textQueryTemplate = "https://duckduckgo.com/?q={query}",
                enabled = true,
                isBuiltIn = true,
                order = 1
            ),
            SearchEngineItem(
                id = ID_TEXT_BING,
                name = "Bing",
                type = SearchEngineType.TEXT,
                baseUrl = "https://www.bing.com/",
                textQueryTemplate = "https://www.bing.com/search?q={query}",
                enabled = true,
                isBuiltIn = true,
                order = 2
            ),
            SearchEngineItem(
                id = ID_TEXT_WIKIPEDIA,
                name = "Wikipedia",
                type = SearchEngineType.TEXT,
                baseUrl = "https://en.wikipedia.org/",
                textQueryTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
                enabled = true,
                isBuiltIn = true,
                order = 3
            )
        )

        fun isValidHttpUrl(url: String): Boolean {
            if (url.isBlank()) return false
            return runCatching {
                val uri = URI(url.trim())
                val scheme = uri.scheme?.lowercase()
                (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }

        fun validateCustomEngine(item: SearchEngineItem): Result<SearchEngineItem> = runCatching {
            val name = item.name.trim()
            require(name.length in 2..48) { "Engine name must be 2–48 characters." }

            if (item.type == SearchEngineType.TEXT) {
                val tpl = item.textQueryTemplate.trim()
                require(tpl.contains("{query}")) {
                    "Text search template must include {query} placeholder (e.g., https://example.com/search?q={query})."
                }
                val probeUrl = tpl.replace("{query}", "test")
                require(isValidHttpUrl(probeUrl)) { "Invalid HTTP/HTTPS text search URL template." }
                return@runCatching item.copy(
                    id = item.id.ifBlank { "custom_text_${UUID.randomUUID().toString().take(8)}" },
                    name = name,
                    baseUrl = item.baseUrl.trim().ifBlank { probeUrl },
                    textQueryTemplate = tpl,
                    isBuiltIn = false
                )
            } else {
                when (item.imageMethod) {
                    SearchImageMethod.CUSTOM_INTENT_PACKAGE -> {
                        val pkg = item.targetPackage.trim()
                        require(pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$"))) {
                            "Invalid Android package name (example: com.example.app)."
                        }
                    }
                    SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
                    SearchImageMethod.BROWSER_URL_ONLY -> {
                        require(isValidHttpUrl(item.baseUrl)) {
                            "Valid HTTP/HTTPS Base URL is required for web visual search engines."
                        }
                    }
                    else -> Unit
                }
                return@runCatching item.copy(
                    id = item.id.ifBlank { "custom_visual_${UUID.randomUUID().toString().take(8)}" },
                    name = name,
                    baseUrl = item.baseUrl.trim(),
                    targetPackage = item.targetPackage.trim(),
                    maxImageSizeKb = item.maxImageSizeKb.coerceIn(128, 20480),
                    isBuiltIn = false
                )
            }
        }

        fun buildTextSearchUrl(engine: SearchEngineItem, query: String): String? {
            val template = engine.textQueryTemplate.trim()
            if (template.isEmpty()) return null
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val finalUrl = if (template.contains("{query}")) {
                template.replace("{query}", encoded)
            } else {
                "$template$encoded"
            }
            return if (isValidHttpUrl(finalUrl)) finalUrl else null
        }

        private fun serializeJson(items: List<SearchEngineItem>): String {
            val arr = JSONArray()
            for (e in items) {
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("type", e.type.name)
                    put("baseUrl", e.baseUrl)
                    put("textQueryTemplate", e.textQueryTemplate)
                    put("imageMethod", e.imageMethod.name)
                    put("targetPackage", e.targetPackage)
                    put("mimeType", e.mimeType)
                    put("maxImageSizeKb", e.maxImageSizeKb)
                    put("enabled", e.enabled)
                    put("isBuiltIn", e.isBuiltIn)
                    put("order", e.order)
                })
            }
            return arr.toString()
        }

        private fun parseJson(raw: String): List<SearchEngineItem> {
            return runCatching {
                val arr = JSONArray(raw)
                val out = ArrayList<SearchEngineItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val type = runCatching { SearchEngineType.valueOf(o.optString("type", "VISUAL")) }
                        .getOrDefault(SearchEngineType.VISUAL)
                    val method = runCatching {
                        SearchImageMethod.valueOf(o.optString("imageMethod", "WEB_DATA_TRANSFER_UPLOAD"))
                    }.getOrDefault(SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD)
                    out.add(
                        SearchEngineItem(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            type = type,
                            baseUrl = o.optString("baseUrl", ""),
                            textQueryTemplate = o.optString("textQueryTemplate", ""),
                            imageMethod = method,
                            targetPackage = o.optString("targetPackage", ""),
                            mimeType = o.optString("mimeType", "image/png"),
                            maxImageSizeKb = o.optInt("maxImageSizeKb", 5120),
                            enabled = o.optBoolean("enabled", true),
                            isBuiltIn = o.optBoolean("isBuiltIn", false),
                            order = o.optInt("order", i)
                        )
                    )
                }
                out
            }.getOrDefault(emptyList())
        }
    }
}
