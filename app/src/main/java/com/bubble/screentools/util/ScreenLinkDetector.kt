package com.bubble.screentools.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.bubble.screentools.R
import com.bubble.screentools.model.TextBlockResult
import java.net.URI

/**
 * Pure-Kotlin safe URL & link detector for on-screen OCR / Accessibility text blocks.
 * - Detects explicit http:// and https:// URLs as well as bare domains with known public TLDs.
 * - Rejects unsafe schemes (javascript:, file:, intent:, content:, data:), email addresses,
 *   software versions (v1.1.0), and local filenames (photo.png, archive.zip).
 * - Launches links safely with FLAG_ACTIVITY_NEW_TASK (compatible with both Assistant Activity
 *   and Accessibility WindowManager Overlay modes).
 */
object ScreenLinkDetector {

    data class DetectedLink(
        val displayUrl: String,
        val normalizedUrl: String,
        val host: String,
        val startIndex: Int,
        val endIndex: Int
    )

    private val UNSAFE_SCHEMES = setOf(
        "javascript:", "file:", "intent:", "content:", "data:", "vbscript:", "about:"
    )

    private val BLOCKED_FILE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico",
        "pdf", "apk", "aab", "zip", "rar", "7z", "tar", "gz",
        "json", "csv", "txt", "xml", "yml", "yaml", "md", "log",
        "traineddata", "mp3", "mp4", "mkv", "wav", "ogg", "avi",
        "kt", "java", "py", "js", "ts", "css", "html", "htm", "exe", "dll", "so", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    )

    private val VALID_PUBLIC_TLDS = setOf(
        "com", "org", "net", "io", "id", "co", "edu", "gov", "dev", "app", "ai", "gg", "me",
        "tv", "xyz", "info", "biz", "uk", "jp", "kr", "de", "fr", "cn", "sg", "my", "au", "ca",
        "us", "eu", "be", "ly", "to", "cc", "sh", "so", "site", "online", "store", "tech", "cloud",
        "news", "page", "link", "live", "pro", "in", "ru", "br", "es", "it", "nl", "pl", "se",
        "ch", "at", "tw", "hk", "vn", "th", "ph", "nz", "mx", "ar", "cl", "pe", "tr", "ua",
        "moe", "wiki", "blog", "shop", "club", "fun", "lol", "top", "vip", "world", "space",
        "design", "studio", "games", "game", "art", "music", "video", "media", "press",
        "ac", "go", "or", "sch", "web", "watch", "bio", "fm", "click", "today", "asia",
        "bz", "gl", "gd", "cx", "vc", "im", "is", "la", "li", "nu", "ws", "cz", "dk", "fi",
        "no", "pt", "ro", "sk", "hu", "gr", "il", "za", "ae", "sa", "eg", "ng", "ke", "pk",
        "bd", "lk", "gy", "mp", "re"
    )

    /**
     * Well-known short/1-letter bare domains frequently rendered in social apps (e.g. Facebook cards
     * showing "X.COM", "T.CO", "FB.WATCH", "S.ID", "M.ME") without scheme or path.
     */
    private val KNOWN_SHORT_DOMAINS = setOf(
        "x.com", "t.co", "g.co", "a.co", "q.com", "m.me", "s.id", "v.gd", "is.gd",
        "fb.me", "fb.gg", "fb.watch", "wa.me", "youtu.be", "bit.ly", "lnkd.in",
        "rb.gy", "ow.ly", "j.mp", "g.page", "t.me"
    )

    // Matches explicit http(s):// URLs OR bare domain.tld(/path)? tokens
    private val URL_CANDIDATE_REGEX = Regex(
        pattern = """(?i)(?:https?://[^\s<>"'`]+)|(?<![@\w/])(?:www\.)?[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+\.[a-z]{2,15}(?::\d{2,5})?(?:/[^\s<>"'`]*)?|(?<![@\w/])[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.[a-z]{2,15}(?::\d{2,5})?(?:/[^\s<>"'`]*)?"""
    )

    fun extractLinks(text: String): List<DetectedLink> {
        val all = extractAllLinkOccurrences(text)
        if (all.isEmpty()) return emptyList()
        val found = ArrayList<DetectedLink>(all.size)
        val seen = HashSet<String>(all.size)
        for (link in all) {
            val dedupeKey = link.normalizedUrl.lowercase().trimEnd('/')
            if (seen.add(dedupeKey)) {
                found.add(link)
            }
        }
        return found
    }

    /**
     * Returns every safe URL occurrence in [text] (without deduplication) with exact character offsets
     * so rich text views can highlight and make repeated URLs clickable.
     */
    fun extractAllLinkOccurrences(text: String): List<DetectedLink> {
        if (text.isBlank()) return emptyList()
        val found = ArrayList<DetectedLink>()

        for (match in URL_CANDIDATE_REGEX.findAll(text)) {
            val rawStart = match.range.first
            val rawToken = match.value

            // Inspect the contiguous non-whitespace prefix right before the match to reject
            // emails with subdomains (e.g. user@sub.example.org) and unsafe URI schemes
            // including data:text/html,https://... or javascript:https://...
            var tokenStart = rawStart
            while (tokenStart > 0) {
                val ch = text[tokenStart - 1]
                if (ch.isWhitespace() || ch == '(' || ch == '[' || ch == '{' || ch == '<' || ch == '"' || ch == '\'') {
                    break
                }
                tokenStart--
            }
            val tokenPrefix = text.substring(tokenStart, rawStart).lowercase()
            if (tokenPrefix.contains('@')) continue
            if (rawToken.contains('@') && !rawToken.startsWith("http://", true) && !rawToken.startsWith("https://", true)) {
                continue
            }
            if (UNSAFE_SCHEMES.any { tokenPrefix.startsWith(it) || tokenPrefix.contains(it) }) {
                continue
            }

            val cleaned = trimTrailingPunctuation(rawToken)
            if (cleaned.length < 4) continue

            val hasScheme = cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true)

            val normalized = if (hasScheme) {
                val schemeEnd = cleaned.indexOf("://")
                cleaned.substring(0, schemeEnd).lowercase() + cleaned.substring(schemeEnd)
            } else {
                "https://$cleaned"
            }

            val parsedUri = parseSafeUri(normalized) ?: continue
            val scheme = parsedUri.scheme?.lowercase() ?: continue
            if (scheme != "http" && scheme != "https") continue

            val host = parsedUri.host?.lowercase()?.trimEnd('.') ?: continue
            if (host.isBlank() || !host.contains('.')) continue
            if (host.startsWith('.') || host.contains("..")) continue

            val isIpv4 = isValidIpv4Host(host)
            if (isIpv4) {
                // Only accept IPv4 hosts when an explicit http:// or https:// scheme was present
                if (!hasScheme) continue
            } else {
                val labels = host.split('.')
                val tld = labels.lastOrNull().orEmpty()
                if (tld.length < 2 || !tld.all { it in 'a'..'z' }) continue

                if (!hasScheme) {
                    // Reject common local filenames (photo.png, data.json, model.traineddata)
                    if (tld in BLOCKED_FILE_EXTENSIONS) continue
                    // Reject version-like tokens (e.g. v1.1.0 or 2.0.com where first label is digits-only and no path/www)
                    if (labels.all { it.all { ch -> ch.isDigit() } }) continue
                    // Require recognized TLD for bare domains to prevent OCR prose false positives
                    if (tld !in VALID_PUBLIC_TLDS) continue
                    // Reject very short ambiguous bare tokens like "a.to" unless it's a known short domain (e.g. x.com, t.co, s.id)
                    val baseDomain = labels.dropLast(1).lastOrNull().orEmpty()
                    if (baseDomain.length < 2 &&
                        host !in KNOWN_SHORT_DOMAINS &&
                        !cleaned.startsWith("www.", true) &&
                        !cleaned.contains('/')
                    ) {
                        continue
                    }
                }
            }

            found.add(
                DetectedLink(
                    displayUrl = cleaned,
                    normalizedUrl = normalized,
                    host = host,
                    startIndex = rawStart,
                    endIndex = rawStart + cleaned.length
                )
            )
        }
        return found
    }

    fun extractLinksFromBlocks(blocks: List<TextBlockResult>): List<DetectedLink> {
        if (blocks.isEmpty()) return emptyList()
        val result = ArrayList<DetectedLink>()
        val seen = HashSet<String>()
        for (block in blocks) {
            for (link in extractLinks(block.text)) {
                val key = link.normalizedUrl.lowercase().trimEnd('/')
                if (seen.add(key)) {
                    result.add(link)
                }
            }
        }
        return result
    }

    fun containsLink(text: String): Boolean = extractLinks(text).isNotEmpty()

    /**
     * Returns true when the text consists almost entirely of detected link(s)
     * (used to adapt Select Text action labels cleanly to "Copy Link").
     */
    fun isPrimarilyLink(text: String): Boolean {
        val compact = text.trim()
        if (compact.isEmpty()) return false
        val links = extractAllLinkOccurrences(compact)
        if (links.isEmpty()) return false
        val linkChars = links.sumOf { it.displayUrl.length }
        val nonWsChars = compact.count { !it.isWhitespace() }.coerceAtLeast(1)
        return linkChars.toFloat() / nonWsChars.toFloat() >= 0.72f
    }

    fun openLink(context: Context, normalizedUrl: String): Boolean {
        val trimmed = normalizedUrl.trim()
        val parsed = parseSafeUri(trimmed) ?: return false
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(
            viewIntent,
            context.getString(R.string.overlay_pick_app_open_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(viewIntent)
                true
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.overlay_no_app_for_link),
                    Toast.LENGTH_SHORT
                ).show()
            }.getOrDefault(false)
        }
    }

    private fun parseSafeUri(url: String): URI? {
        runCatching { URI(url) }.getOrNull()?.let { return it }
        // Encode brackets/braces/pipes that commonly appear unencoded in e-commerce/PHP query params
        val escaped = buildString(url.length + 12) {
            for (ch in url) {
                when (ch) {
                    '[' -> append("%5B")
                    ']' -> append("%5D")
                    '{' -> append("%7B")
                    '}' -> append("%7D")
                    '|' -> append("%7C")
                    '^' -> append("%5E")
                    '\\' -> append("%5C")
                    else -> append(ch)
                }
            }
        }
        return runCatching { URI(escaped) }.getOrNull()
    }

    private fun isValidIpv4Host(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        return octets.all { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return@all false
            val num = part.toIntOrNull() ?: return@all false
            num in 0..255
        }
    }

    private fun trimTrailingPunctuation(token: String): String {
        var result = token
        while (result.isNotEmpty()) {
            val last = result.last()
            val shouldTrim = when (last) {
                '.', ',', ';', ':', '!', '?', '"', '\'', '>' -> true
                ')' -> result.count { it == ')' } > result.count { it == '(' }
                ']' -> result.count { it == ']' } > result.count { it == '[' }
                '}' -> result.count { it == '}' } > result.count { it == '{' }
                else -> false
            }
            if (shouldTrim) {
                result = result.dropLast(1)
            } else {
                break
            }
        }
        return result
    }
}
