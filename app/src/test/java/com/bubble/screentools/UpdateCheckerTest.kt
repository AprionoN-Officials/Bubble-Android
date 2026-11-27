package com.bubble.screentools

import com.bubble.screentools.update.GitHubReleaseParser
import com.bubble.screentools.update.ReleaseAsset
import com.bubble.screentools.update.SemVer
import com.bubble.screentools.update.UpdateAssetSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for GitHub Automatic Update Checker:
 * SemVer, prerelease filtering, downgrade protection, release JSON parsing,
 * ABI asset selection, and checksum metadata extraction.
 */
class UpdateCheckerTest {

    @Test
    fun semVerParsesVPrefixAndOrdersPrereleasesCorrectly() {
        val v100 = SemVer.parse("v1.0.0")!!
        val v110Alpha = SemVer.parse("v1.1.0-alpha.1")!!
        val v110Beta = SemVer.parse("1.1.0-beta.2")!!
        val v110Rc = SemVer.parse("v1.1.0-rc.1")!!
        val v110Stable = SemVer.parse("v1.1.0")!!
        val v111Stable = SemVer.parse("v1.1.1")!!
        val v200 = SemVer.parse("2.0.0")!!

        assertTrue(v100 < v110Alpha)
        assertTrue(v110Alpha < v110Beta)
        assertTrue(v110Beta < v110Rc)
        assertTrue(v110Rc < v110Stable)
        assertTrue(v110Stable < v111Stable)
        assertTrue(v111Stable < v200)
        assertNull(SemVer.parse("not-a-version"))
    }

    @Test
    fun updateDetectionHandlesOlderSameNewerAndPrerelease() {
        // Installed older -> update available
        assertTrue(SemVer.isUpdateAvailable("1.0.0", "v1.1.1", false, includePrerelease = false))
        // Installed same -> no update
        assertFalse(SemVer.isUpdateAvailable("1.1.1", "v1.1.1", false, includePrerelease = false))
        // Installed newer -> prevent downgrade
        assertFalse(SemVer.isUpdateAvailable("1.2.0", "v1.1.1", false, includePrerelease = true))
        // Prerelease excluded by default
        assertFalse(SemVer.isUpdateAvailable("1.1.1", "v1.2.0-beta.1", true, includePrerelease = false))
        // Prerelease accepted when opted in
        assertTrue(SemVer.isUpdateAvailable("1.1.1", "v1.2.0-beta.1", true, includePrerelease = true))
        // Installed prerelease upgrades cleanly to stable of same version
        assertTrue(SemVer.isUpdateAvailable("1.1.0-dev", "v1.1.1", false, includePrerelease = false))
    }

    @Test
    fun gitHubReleaseParserExtractsAssetsAndSha256Checksums() {
        val fakeHash = "a".repeat(64)
        val json = """
            {
              "tag_name": "v1.2.0",
              "name": "Bubble v1.2.0",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-26T10:00:00Z",
              "html_url": "https://github.com/AprionoN-Officials/Bubble-Android/releases/tag/v1.2.0",
              "body": "Personalization update.\n$fakeHash  app-arm64-v8a-release.apk",
              "assets": [
                {
                  "name": "app-arm64-v8a-release.apk",
                  "browser_download_url": "https://github.com/AprionoN-Officials/Bubble-Android/releases/download/v1.2.0/app-arm64-v8a-release.apk",
                  "size": 12900000,
                  "content_type": "application/vnd.android.package-archive"
                },
                {
                  "name": "app-armeabi-v7a-release.apk",
                  "browser_download_url": "https://github.com/AprionoN-Officials/Bubble-Android/releases/download/v1.2.0/app-armeabi-v7a-release.apk",
                  "size": 11800000,
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        val release = GitHubReleaseParser.parseSingleRelease(json)
        assertNotNull(release)
        assertEquals("v1.2.0", release!!.tagName)
        assertEquals(2, release.assets.size)
        assertEquals(fakeHash, release.expectedChecksums["app-arm64-v8a-release.apk"])

        // Invalid JSON fails safely
        assertNull(GitHubReleaseParser.parseSingleRelease("{broken json"))
    }

    @Test
    fun assetSelectorMatchesDeviceAbiAndSkipsDebugApks() {
        val assets = listOf(
            ReleaseAsset("app-arm64-v8a-debug.apk", "https://example.com/debug.apk", 100, "application/vnd.android.package-archive"),
            ReleaseAsset("app-armeabi-v7a-release.apk", "https://example.com/v7a.apk", 100, "application/vnd.android.package-archive"),
            ReleaseAsset("app-arm64-v8a-release.apk", "https://example.com/v8a.apk", 100, "application/vnd.android.package-archive")
        )
        val arm64 = UpdateAssetSelector.selectBestApk(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-arm64-v8a-release.apk", arm64?.name)

        val armv7 = UpdateAssetSelector.selectBestApk(assets, listOf("armeabi-v7a"))
        assertEquals("app-armeabi-v7a-release.apk", armv7?.name)

        val unsupportedX86 = UpdateAssetSelector.selectBestApk(assets, listOf("x86_64"))
        assertNull(unsupportedX86)
    }
}
