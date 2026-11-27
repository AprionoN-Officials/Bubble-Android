package com.bubble.screentools.update

import org.json.JSONArray
import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

data class GitHubRelease(
    val tagName: String,
    val version: SemVer,
    val title: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset>,
    val expectedChecksums: Map<String, String>
)

object GitHubReleaseParser {

    fun parseSingleRelease(jsonString: String): GitHubRelease? {
        return runCatching {
            parseReleaseObject(JSONObject(jsonString))
        }.getOrNull()
    }

    fun parseReleaseList(jsonString: String): List<GitHubRelease> {
        return runCatching {
            val arr = JSONArray(jsonString)
            val list = ArrayList<GitHubRelease>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseReleaseObject(obj)?.let { list.add(it) }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun parseReleaseObject(obj: JSONObject): GitHubRelease? {
        val tag = obj.optString("tag_name", "").trim()
        val version = SemVer.parse(tag) ?: return null
        val isDraft = obj.optBoolean("draft", false)
        if (isDraft) return null

        val isPre = obj.optBoolean("prerelease", false) || version.isPrerelease
        val title = obj.optString("name", tag).trim().ifEmpty { tag }
        val body = obj.optString("body", "").trim()
        val publishedAt = obj.optString("published_at", "").trim()
        val htmlUrl = obj.optString("html_url", "").trim()

        val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
        val assets = ArrayList<ReleaseAsset>(assetsArr.length())
        for (i in 0 until assetsArr.length()) {
            val a = assetsArr.optJSONObject(i) ?: continue
            val name = a.optString("name", "").trim()
            val url = a.optString("browser_download_url", "").trim()
            if (name.isNotEmpty() && url.startsWith("https://")) {
                assets.add(
                    ReleaseAsset(
                        name = name,
                        downloadUrl = url,
                        sizeBytes = a.optLong("size", 0L),
                        contentType = a.optString("content_type", "")
                    )
                )
            }
        }

        val checksums = extractSha256Checksums(body)
        return GitHubRelease(
            tagName = tag,
            version = version,
            title = title,
            changelog = body,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            isPrerelease = isPre,
            isDraft = isDraft,
            assets = assets,
            expectedChecksums = checksums
        )
    }

    /**
     * Parses official SHA-256 digests from release notes (formats like:
     * `<64-hex>  app-arm64-v8a-release.apk` or `app-arm64-v8a-release.apk: <64-hex>`).
     */
    fun extractSha256Checksums(text: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val hexFirst = Regex("([a-fA-F0-9]{64})\\s+\\*?([\\w.-]+\\.apk)")
        val fileFirst = Regex("([\\w.-]+\\.apk)\\s*[:=\\-]\\s*(?:sha256[:=\\s]*)?([a-fA-F0-9]{64})", RegexOption.IGNORE_CASE)

        for (m in hexFirst.findAll(text)) {
            map[m.groupValues[2].lowercase()] = m.groupValues[1].lowercase()
        }
        for (m in fileFirst.findAll(text)) {
            map[m.groupValues[1].lowercase()] = m.groupValues[2].lowercase()
        }
        return map
    }
}

object UpdateAssetSelector {

    /**
     * Selects the best matching APK release asset for the device's ordered [supportedAbis],
     * falling back to a universal APK if no ABI-specific APK is published.
     */
    fun selectBestApk(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) &&
                !it.name.contains("debug", ignoreCase = true)
        }
        if (apks.isEmpty()) return null

        // 1. Exact ABI priority match (e.g. arm64-v8a before armeabi-v7a)
        for (abi in supportedAbis) {
            val match = apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }

        // 2. Universal APK fallback
        val universal = apks.firstOrNull { name ->
            val lower = name.name.lowercase()
            lower.contains("universal") || (
                !lower.contains("arm64") &&
                    !lower.contains("armeabi") &&
                    !lower.contains("x86")
                )
        }
        return universal
    }
}
