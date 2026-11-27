package com.bubble.screentools

import com.bubble.screentools.util.ScreenLinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [ScreenLinkDetector]:
 * - Explicit http(s) URL extraction & trailing punctuation cleanup
 * - Bare public-TLD domain normalization to https://
 * - Strict rejection of unsafe schemes (javascript:, file:, intent:, content:)
 * - Strict rejection of false positives (emails, filenames, software versions)
 * - Smart Select Text primary-link detection
 */
class ScreenLinkDetectorTest {

    @Test
    fun detectsExplicitHttpsAndHttpUrlsAndTrimsTrailingPunctuation() {
        val text = "Download at https://github.com/AprionoN-Officials/Bubble-Android, or visit https://en.wikipedia.org/wiki/Android_(operating_system)!"
        val links = ScreenLinkDetector.extractLinks(text)
        assertEquals(2, links.size)
        assertEquals("https://github.com/AprionoN-Officials/Bubble-Android", links[0].normalizedUrl)
        assertEquals("github.com", links[0].host)
        assertEquals("https://en.wikipedia.org/wiki/Android_(operating_system)", links[1].normalizedUrl)
        assertEquals("en.wikipedia.org", links[1].host)
    }

    @Test
    fun detectsValidBareDomainsAndNormalizesToHttps() {
        val text = "Support at trakteer.id/apriononurhakim or www.youtube.com/watch?v=abc123"
        val links = ScreenLinkDetector.extractLinks(text)
        assertEquals(2, links.size)
        assertEquals("https://trakteer.id/apriononurhakim", links[0].normalizedUrl)
        assertEquals("trakteer.id", links[0].host)
        assertEquals("https://www.youtube.com/watch?v=abc123", links[1].normalizedUrl)
    }

    @Test
    fun rejectsUnsafeSchemesEmailsFilenamesAndVersions() {
        val dangerousAndNoise = """
            javascript:alert(1)
            file:///sdcard/secret.txt
            intent://scan/#Intent;scheme=zxing;end
            content://com.bubble.provider/image.png
            Contact support@example.com for help.
            Bubble v1.1.0 saved photo.png, jpn.traineddata, and Bubble.apk.
        """.trimIndent()

        val links = ScreenLinkDetector.extractLinks(dangerousAndNoise)
        assertTrue("Expected no links from unsafe schemes or noise, got: $links", links.isEmpty())
    }

    @Test
    fun detectsShortSocialDomainsLikeXComOnFacebookWhileRejectingAmbiguousTokens() {
        val fbPost = "Breaking News\nX.COM • 2j\nWatch clip on fb.watch or s.id/bubble or t.co and m.me"
        val links = ScreenLinkDetector.extractLinks(fbPost)
        val hosts = links.map { it.host }
        assertTrue("Expected x.com in $hosts", "x.com" in hosts)
        assertTrue("Expected fb.watch in $hosts", "fb.watch" in hosts)
        assertTrue("Expected s.id in $hosts", "s.id" in hosts)
        assertTrue("Expected t.co in $hosts", "t.co" in hosts)
        assertTrue("Expected m.me in $hosts", "m.me" in hosts)

        // Unknown 1-letter ambiguous token like a.to must still be rejected
        assertTrue(ScreenLinkDetector.extractLinks("Move from a.to b quickly").isEmpty())
    }

    @Test
    fun deduplicatesRepeatedUrlsAndDistinguishesPrimaryLinksFromProse() {
        val dupText = "https://github.com/AprionoN-Officials/Bubble-Android and https://github.com/AprionoN-Officials/Bubble-Android/"
        assertEquals(1, ScreenLinkDetector.extractLinks(dupText).size)

        assertTrue(ScreenLinkDetector.isPrimarilyLink("https://trakteer.id/apriononurhakim"))
        assertTrue(ScreenLinkDetector.isPrimarilyLink("  github.com/AprionoN-Officials  "))
        assertFalse(ScreenLinkDetector.isPrimarilyLink("Please read the full release notes and changelog at github.com/AprionoN-Officials before updating your device."))
        assertFalse(ScreenLinkDetector.containsLink("Just normal text without any links."))
    }
}
