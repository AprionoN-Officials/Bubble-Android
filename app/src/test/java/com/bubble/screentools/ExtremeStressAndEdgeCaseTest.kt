package com.bubble.screentools

import com.bubble.screentools.donation.DonationReminderManager
import com.bubble.screentools.donation.DonationReminderManager.AccessDecision
import com.bubble.screentools.donation.DonationReminderManager.GateDestination
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.lens.WebViewAdBlocker
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.ActionPanelConfig
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchEngineType
import com.bubble.screentools.model.SearchImageMethod
import com.bubble.screentools.model.ThemeConfig
import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationQualityMode
import com.bubble.screentools.qr.QrCodeParser
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.translation.TranslationContextProcessor
import com.bubble.screentools.translation.TranslationContextProcessor.SpatialTextBlock
import com.bubble.screentools.ui.overlay.ActionPanelView
import com.bubble.screentools.ui.overlay.SelectionGestureHandler
import com.bubble.screentools.ui.overlay.SelectionGestureHandler.BoundsF
import com.bubble.screentools.ui.overlay.SelectionGestureHandler.HandleType
import com.bubble.screentools.ui.overlay.SelectionPathBuilder
import com.bubble.screentools.update.GitHubReleaseParser
import com.bubble.screentools.update.ReleaseAsset
import com.bubble.screentools.update.SemVer
import com.bubble.screentools.update.UpdateAssetSelector
import com.bubble.screentools.util.ScreenLinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Extreme stress, fuzzing, and adversarial edge-case test suite covering all core Bubble v1.1.1
 * pure-Kotlin engines: geometry/selection, path math, link detection, Material 3 palette synthesis,
 * translation context reconstruction, donation reminder state machine, QR TLV/URI parsing,
 * GitHub updater SemVer/JSON/ABI selection, and Action Panel sanitizer.
 */
class ExtremeStressAndEdgeCaseTest {

    private class FakeDonationStorage : DonationReminderManager.DonationStorage {
        override var firstUsageTimestampMs: Long = 0L
        override var lastAcceptedTimestampMs: Long = 0L
    }

    @Test
    fun selectionGestureHandlerSurvivesTinyBoundsMinSizeOverflowAndNonFiniteInputs() {
        val extremeScenarios = listOf(
            // minSizePx larger than bounds (split-screen / foldable / small preview with 96dp min size)
            Triple(10f, 10f, 300f),
            Triple(50f, 50f, 288f),
            Triple(1f, 1f, 96f),
            // Zero and negative bounds (unmeasured View)
            Triple(0f, 0f, 42f),
            Triple(-100f, -50f, 42f),
            // Normal phone screen
            Triple(1080f, 2400f, 126f)
        )
        val deltas = listOf(
            -100_000f to -100_000f,
            100_000f to 100_000f,
            -100_000f to 100_000f,
            100_000f to -100_000f,
            0f to 0f,
            Float.NaN to 10f,
            10f to Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY to Float.NaN
        )

        for ((bw, bh, minSz) in extremeScenarios) {
            for (handle in HandleType.entries) {
                for ((dx, dy) in deltas) {
                    val rect = BoundsF(2f, 2f, 8f, 8f)
                    SelectionGestureHandler.applyResize(rect, handle, dx, dy, minSz, bw, bh)
                    assertTrue("left must stay finite", rect.left.isFinite())
                    assertTrue("top must stay finite", rect.top.isFinite())
                    assertTrue("right must stay finite", rect.right.isFinite())
                    assertTrue("bottom must stay finite", rect.bottom.isFinite())
                    if (bw > 0f && bh > 0f && dx.isFinite() && dy.isFinite()) {
                        assertTrue("left=$rect.left <= right=${rect.right}", rect.left <= rect.right + 0.01f)
                        assertTrue("top=${rect.top} <= bottom=${rect.bottom}", rect.top <= rect.bottom + 0.01f)
                    }
                }
            }
        }

        // detectHitHandle non-finite & degenerate rect guards
        val rect = BoundsF(10f, 10f, 200f, 200f)
        assertEquals(HandleType.NONE, SelectionGestureHandler.detectHitHandle(Float.NaN, 50f, rect, 24f))
        assertEquals(HandleType.NONE, SelectionGestureHandler.detectHitHandle(50f, Float.POSITIVE_INFINITY, rect, 24f))
        assertEquals(HandleType.NONE, SelectionGestureHandler.detectHitHandle(50f, 50f, rect, -5f))
        assertEquals(HandleType.NONE, SelectionGestureHandler.detectHitHandle(50f, 50f, BoundsF(100f, 100f, 10f, 10f), 24f))
    }

    @Test
    fun selectionPathBuilderHandlesNanInfinityExtremeCurvaturesAnd10kFreeformPoints() {
        val curvatures = listOf(-10f, 0f, 0.001f, 0.55f, 1f, 999f)
        for (c in curvatures) {
            val pts = SelectionPathBuilder.computeSuperellipsePoints(10f, 20f, 510f, 820f, c, steps = 72)
            assertEquals(72, pts.size)
            for ((x, y) in pts) {
                assertTrue(x.isFinite() && y.isFinite())
                assertTrue("x=$x out of bounds", x in 9.9f..510.1f)
                assertTrue("y=$y out of bounds", y in 19.9f..820.1f)
            }
        }

        // Invalid steps, inverted bounds, NaN bounds must return empty list cleanly
        assertTrue(SelectionPathBuilder.computeSuperellipsePoints(0f, 0f, 100f, 100f, 0.5f, steps = 0).isEmpty())
        assertTrue(SelectionPathBuilder.computeSuperellipsePoints(0f, 0f, 100f, 100f, 0.5f, steps = 7).isEmpty())
        assertTrue(SelectionPathBuilder.computeSuperellipsePoints(100f, 0f, 50f, 100f, 0.5f).isEmpty())
        assertTrue(SelectionPathBuilder.computeSuperellipsePoints(Float.NaN, 0f, 100f, 100f, 0.5f).isEmpty())
        assertTrue(SelectionPathBuilder.computeSuperellipsePoints(0f, 0f, 100f, 100f, Float.NaN).isEmpty())

        // Freeform bounds with NaN / Infinity mixed in
        val allNanBounds = SelectionPathBuilder.computePointsBounds(
            listOf(Float.NaN to 10f, 20f to Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY to Float.NaN)
        )
        assertEquals(0f, allNanBounds.left, 0.001f)
        assertEquals(0f, allNanBounds.right, 0.001f)

        val mixedBounds = SelectionPathBuilder.computePointsBounds(
            listOf(Float.NaN to 0f, 15f to 25f, 300f to Float.POSITIVE_INFINITY, 180f to 420f)
        )
        assertEquals(15f, mixedBounds.left, 0.01f)
        assertEquals(25f, mixedBounds.top, 0.01f)
        assertEquals(180f, mixedBounds.right, 0.01f)
        assertEquals(420f, mixedBounds.bottom, 0.01f)

        // 10,000 freeform points stress test
        val hugePoints = (0 until 10_000).map { i -> (i % 1080).toFloat() to ((i * 7) % 2400).toFloat() }
        val start = System.nanoTime()
        val hugeBounds = SelectionPathBuilder.computePointsBounds(hugePoints)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        assertEquals(0f, hugeBounds.left, 0.01f)
        assertEquals(1079f, hugeBounds.right, 0.01f)
        assertTrue("10k points bounds took too long: ${elapsedMs}ms", elapsedMs < 250L)
    }

    @Test
    fun screenLinkDetectorHandlesBracketedQueriesIpv4UnicodeDuplicatesAndAdversarialPayloads() {
        // 1. Bracketed PHP / e-commerce query params
        val shopText = "Check product: https://shop.example.com/items?filter[color]=blue&sort[0]=asc."
        val shopLinks = ScreenLinkDetector.extractLinks(shopText)
        assertEquals(1, shopLinks.size)
        assertEquals("https://shop.example.com/items?filter[color]=blue&sort[0]=asc", shopLinks[0].normalizedUrl)
        assertEquals("shop.example.com", shopLinks[0].host)

        // 2. Explicit IPv4 URLs allowed, bare IPv4s rejected
        val ipText = "Router admin: http://192.168.1.1:8080/login or https://10.0.0.1/status (bare 192.168.1.1 and 1.2.3.4 ignored)"
        val ipLinks = ScreenLinkDetector.extractLinks(ipText)
        assertEquals(2, ipLinks.size)
        assertEquals("http://192.168.1.1:8080/login", ipLinks[0].normalizedUrl)
        assertEquals("https://10.0.0.1/status", ipLinks[1].normalizedUrl)

        // 3. Uppercase scheme + balanced parentheses + trailing punctuation
        val upperText = "Read (HTTPS://EN.WIKIPEDIA.ORG/wiki/Kotlin_(programming_language)), now!"
        val upperLinks = ScreenLinkDetector.extractLinks(upperText)
        assertEquals(1, upperLinks.size)
        assertTrue(upperLinks[0].normalizedUrl.startsWith("https://"))
        assertTrue(upperLinks[0].normalizedUrl.endsWith("(programming_language)"))

        // 4. Duplicate URLs: extractLinks deduplicates while extractAllLinkOccurrences preserves both spans
        val dupText = "First: https://trakteer.id/apriononurhakim and again https://trakteer.id/apriononurhakim!"
        val deduped = ScreenLinkDetector.extractLinks(dupText)
        val allOccurrences = ScreenLinkDetector.extractAllLinkOccurrences(dupText)
        assertEquals(1, deduped.size)
        assertEquals(2, allOccurrences.size)
        for (occ in allOccurrences) {
            assertEquals(occ.displayUrl, dupText.substring(occ.startIndex, occ.endIndex))
        }
        assertTrue(ScreenLinkDetector.isPrimarilyLink("https://trakteer.id/apriononurhakim\nhttps://trakteer.id/apriononurhakim"))
        assertFalse(ScreenLinkDetector.isPrimarilyLink("Here is a long explanation about why Bubble is completely free and open source for everyone: https://github.com/AprionoN-Officials/Bubble-Android"))

        // 5. Adversarial & false-positive rejection
        val adversarial = """
            javascript:alert(1)
            javascript:https://evil.com/xss
            file:///etc/passwd
            file:///sdcard/github.com
            intent://scan/#Intent;scheme=zxing;end
            content://com.bubble.provider/test.com
            data:text/html,https://evil.com
            vbscript:msgbox.com
            email: admin@github.com, test.user@sub.example.org
            files: screenshot.png, config.json, model.traineddata, Bubble-v1.1.1.apk, backup.zip, index.html
            versions: v1.1.1, 2.0.15, 192.168.0.1, 10.0.0.255
        """.trimIndent()
        val advLinks = ScreenLinkDetector.extractLinks(adversarial)
        assertTrue("Expected 0 links from adversarial payload, got: $advLinks", advLinks.isEmpty())

        // 6. 50KB OCR flood performance (guards against regex catastrophic backtracking / ReDoS)
        val bigText = buildString(55_000) {
            repeat(400) { i ->
                append("Paragraph $i with normal Indonesian and English screen text, numbers 1.2.3, email user$i@test.com, ")
                if (i % 10 == 0) {
                    append("and link https://example.com/page/$i?filter[a]=$i ")
                }
                append("plus trailing punctuation.....!!!!!\n")
            }
        }
        val t0 = System.nanoTime()
        val bigLinks = ScreenLinkDetector.extractLinks(bigText)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
        assertEquals(40, bigLinks.size)
        assertTrue("50KB link scan took too long (possible ReDoS): ${elapsedMs}ms", elapsedMs < 600L)
    }

    @Test
    fun themePaletteGeneratorSweepsAll360HuesAndEdgeColorsWithZeroNanOrContrastViolations() {
        val seeds = ArrayList<Int>(380)
        // Sweep all 360 degrees of hue at vivid saturation
        for (h in 0 until 360) {
            seeds.add(hsvToArgb(h.toFloat(), 0.9f, 0.9f))
        }
        // Extreme edge colors (black, white, primaries, transparent, mid-gray)
        seeds.addAll(
            listOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
                0xFFFFFF00.toInt(),
                0xFF00FFFF.toInt(),
                0xFFFF00FF.toInt(),
                0x00000000,
                0x80808080.toInt(),
                0xFF010101.toInt(),
                0xFFFEFEFE.toInt()
            )
        )

        for (seed in seeds) {
            val light = ThemePaletteGenerator.generate(seed, isDark = false, amoledBlack = false)
            val dark = ThemePaletteGenerator.generate(seed, isDark = true, amoledBlack = false)
            val amoled = ThemePaletteGenerator.generate(seed, isDark = true, amoledBlack = true)

            assertEquals(0xFF000000.toInt(), amoled.surface)
            assertEquals(0xFF, (light.primary ushr 24) and 0xFF)
            assertEquals(0xFF, (dark.primary ushr 24) and 0xFF)

            assertTrue(
                "Light onSurface contrast failed for ${ThemePaletteGenerator.formatHex(seed)}",
                ThemePaletteGenerator.contrastRatio(light.onSurface, light.surface) >= 7.0
            )
            assertTrue(
                "Dark onSurface contrast failed for ${ThemePaletteGenerator.formatHex(seed)}",
                ThemePaletteGenerator.contrastRatio(dark.onSurface, dark.surface) >= 7.0
            )
            assertTrue(
                "AMOLED onSurface contrast failed for ${ThemePaletteGenerator.formatHex(seed)}",
                ThemePaletteGenerator.contrastRatio(amoled.onSurface, amoled.surface) >= 12.0
            )
            assertTrue(
                "Light onPrimary contrast failed for ${ThemePaletteGenerator.formatHex(seed)}",
                ThemePaletteGenerator.contrastRatio(light.onPrimary, light.primary) >= 4.5
            )
            assertTrue(
                "Dark onPrimary contrast failed for ${ThemePaletteGenerator.formatHex(seed)}",
                ThemePaletteGenerator.contrastRatio(dark.onPrimary, dark.primary) >= 4.5
            )
        }

        // ThemeConfig default applyToSelection must be true so Appearance scope switch works bidirectionally
        assertTrue(ThemeConfig().applyToSelection)

        // parseHexColor adversarial inputs
        assertNull(ThemePaletteGenerator.parseHexColor(""))
        assertNull(ThemePaletteGenerator.parseHexColor("#12345"))
        assertNull(ThemePaletteGenerator.parseHexColor("#GGGGGG"))
        assertEquals(0xFF2196F3.toInt(), ThemePaletteGenerator.parseHexColor("#2196F3"))
        assertEquals(0x802196F3.toInt(), ThemePaletteGenerator.parseHexColor("#802196F3"))
    }

    @Test
    fun translationContextProcessorSurvives500BlocksDegenerateBoxesAndAllModeMatrix() {
        // Empty & whitespace-only inputs
        assertTrue(
            TranslationContextProcessor.processBlocks(
                emptyList(), TranslationQualityMode.NATURAL, TranslationContextProfile.NATURAL
            ).isEmpty()
        )
        assertTrue(
            TranslationContextProcessor.processBlocks(
                listOf(SpatialTextBlock("   \n\t  ", 0f, 0f, 100f, 20f)),
                TranslationQualityMode.NATURAL,
                TranslationContextProfile.GENERAL
            ).isEmpty()
        )

        // Degenerate zero-width, zero-height, inverted, and negative boxes
        val degenerate = listOf(
            SpatialTextBlock("[ HP ] +15% trans-", 100f, 100f, 100f, 100f),
            SpatialTextBlock("lation test", 200f, 120f, 50f, 110f),
            SpatialTextBlock("Negative box", -50f, -20f, -10f, -5f)
        )
        for (mode in TranslationQualityMode.entries) {
            for (profile in TranslationContextProfile.entries) {
                val out = TranslationContextProcessor.processBlocks(degenerate, mode, profile)
                assertTrue("Mode=$mode profile=$profile should preserve non-empty text", out.isNotEmpty())
            }
        }

        // 500-block OCR flood across all 12 (mode x profile) combinations
        val flood = (0 until 500).map { i ->
            val col = i % 2
            val row = i / 2
            val left = col * 520f + 20f
            val top = row * 28f + 10f
            SpatialTextBlock("Line $i Critica| text sample with word-", left, top, left + 460f, top + 22f)
        }
        val t0 = System.nanoTime()
        for (mode in TranslationQualityMode.entries) {
            for (profile in TranslationContextProfile.entries) {
                val res = TranslationContextProcessor.processBlocks(flood, mode, profile)
                assertTrue(res.isNotEmpty())
            }
        }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
        assertTrue("500-block x 12 matrix took too long: ${elapsedMs}ms", elapsedMs < 1500L)
    }

    @Test
    fun donationReminderSurvivesClockRollbackRapidTabSpamAnd100xDismisses() {
        val hourMs = 60L * 60L * 1000L
        var now = 1_750_000_000_000L
        val storage = FakeDonationStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        // 100x dismiss / back / donate spam must NEVER reset cooldown
        repeat(100) {
            assertTrue(manager.evaluateAccess(GateDestination.SEARCH_SETTINGS_MAIN) is AccessDecision.ShowReminder)
            manager.onDismissedOrBack()
            manager.onDonateClicked()
        }
        assertEquals(0L, storage.lastAcceptedTimestampMs)

        // Accept Continue once
        manager.onContinueAccepted()
        assertEquals(now, storage.lastAcceptedTimestampMs)

        // Rapid 100x tab switching across ALL destinations must remain allowed
        repeat(100) {
            for (dest in GateDestination.entries) {
                assertEquals(
                    "Destination $dest must not re-prompt during 72h cooldown",
                    AccessDecision.AllowImmediately,
                    manager.evaluateAccess(dest)
                )
            }
        }

        // Clock rollback (e.g., user or NTP moves clock back 10 hours) must not crash or return >72h remaining
        now -= 10 * hourMs
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION))
        val remaining = manager.getRemainingCooldownMs()
        assertTrue("Remaining cooldown must stay in [0, 72h], got $remaining", remaining in 0L..(72 * hourMs))

        // Advance back to original + 72h -> triggers cleanly again
        now += 82 * hourMs
        assertTrue(manager.evaluateAccess(GateDestination.ACTION_PANEL_CUSTOMIZATION) is AccessDecision.ShowReminder)
        // Overlay core trigger STILL never gated
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.OVERLAY_CORE_FEATURES))
    }

    @Test
    fun qrCodeParserFuzzingNeverThrowsOnMalformedTlvWifiVcardOr100kbGarbage() {
        val fuzzPayloads = listOf(
            "",
            "   ",
            "000201",
            "00020100000000000000000000",
            "00020199XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            "000201-5abc5911WARUNG KOPI6007JAKARTA",
            "0002015999TRUNCATED_MERCHANT_NAME_OVERFLOW",
            "0002015403NaN53033605802ID5904TEST",
            "WIFI:",
            "WIFI:;;;;;",
            "WIFI:S:My\\;Net\\:Name;P:pass\\\\word\\;1;T:WPA3;H:true;;",
            "WIFI:S:TrailingBackslash\\",
            "BEGIN:VCARD\nEND:VCARD",
            "BEGIN:VCARD\nFN:\nN:;;;;\nTEL:\nEMAIL:\nEND:VCARD",
            "MECARD:",
            "MECARD:N:;;;;;;;\\",
            "MATMSG:",
            "MATMSG:TO:;SUB:;BODY:;;",
            "mailto:",
            "mailto:test@example.com?subject=",
            "tel:",
            "sms:",
            "smsto:::",
            "upi://pay?===&&&pa=&am=",
            "dana://qr",
            "http://",
            "https://",
            "\u0000\u0001\u0002\uFFFF🚀🔥",
            "X".repeat(100_000)
        )

        for (payload in fuzzPayloads) {
            val parsed = QrCodeParser.parse(payload)
            assertNotNull(parsed)
            assertNotNull(parsed.badgeLabel)
            assertNotNull(parsed.title)
            assertNotNull(parsed.subtitle)
        }
    }

    @Test
    fun semVerGitHubParserAndSearchEngineRegistrySurviveAdversarialInputs() {
        // SemVer adversarial & overflow inputs
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("v999999999999999999999.0.0"))
        assertNull(SemVer.parse("v-1.0.0"))
        assertNull(SemVer.parse("1.2.3.4"))
        assertTrue(SemVer.parse("1.0.0-1")!! < SemVer.parse("1.0.0-2")!!)
        assertTrue(SemVer.parse("1.0.0-2")!! < SemVer.parse("1.0.0-10")!!)
        assertTrue(SemVer.parse("1.0.0-10")!! < SemVer.parse("1.0.0-alpha")!!)

        // GitHubReleaseParser: draft release ignored, http (non-https) asset ignored
        val draftJson = """{"tag_name":"v9.9.9","draft":true,"prerelease":false,"assets":[]}"""
        assertNull(GitHubReleaseParser.parseSingleRelease(draftJson))

        val insecureAssetJson = """
            {
              "tag_name": "v2.0.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name":"app-arm64-v8a-release.apk","browser_download_url":"http://evil.com/a.apk","size":1000},
                {"name":"Bubble-universal-release.apk","browser_download_url":"https://github.com/a/b/releases/download/v2.0.0/Bubble-universal-release.apk","size":12000000}
              ]
            }
        """.trimIndent()
        val parsedRelease = GitHubReleaseParser.parseSingleRelease(insecureAssetJson)
        assertNotNull(parsedRelease)
        assertEquals(1, parsedRelease!!.assets.size)
        val picked = UpdateAssetSelector.selectBestApk(parsedRelease.assets, listOf("arm64-v8a"))
        assertEquals("Bubble-universal-release.apk", picked?.name)
        assertTrue(GitHubReleaseParser.parseReleaseList("not-json-array").isEmpty())

        // SearchEngineRegistry adversarial URL & package validation
        val xssText = SearchEngineItem(
            id = "", name = "Bad", type = SearchEngineType.TEXT,
            baseUrl = "javascript:alert(1)", textQueryTemplate = "javascript:alert('{query}')"
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(xssText).isFailure)

        val fileVisual = SearchEngineItem(
            id = "", name = "BadFile", type = SearchEngineType.VISUAL,
            baseUrl = "file:///etc/passwd", imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(fileVisual).isFailure)

        val blankName = SearchEngineItem(
            id = "", name = "   ", type = SearchEngineType.VISUAL,
            baseUrl = "https://example.com", imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(blankName).isFailure)
    }

    @Test
    fun actionPanelSanitizerAndCustomOffsetsSurviveExtremeOverflowAndNanValues() {
        val insaneConfig = ActionPanelConfig(
            enabledButtons = listOf(
                ActionPanelButtonType.TRANSLATE,
                ActionPanelButtonType.TRANSLATE,
                ActionPanelButtonType.SAVE_IMAGE,
                ActionPanelButtonType.SAVE_IMAGE
            ),
            customPosX = -999f,
            customPosY = 999f,
            buttonSpacingDp = -50f,
            buttonPaddingHorizontalDp = 500f,
            buttonCornerRadiusDp = -10f,
            panelWidthPercent = 9999,
            overallScale = -2f,
            panelOpacity = 5f,
            horizontalMarginDp = -100f,
            verticalMarginDp = 999f
        ).sanitized()

        assertEquals(listOf(ActionPanelButtonType.TRANSLATE, ActionPanelButtonType.SAVE_IMAGE), insaneConfig.enabledButtons)
        assertEquals(0.05f, insaneConfig.customPosX, 0.001f)
        assertEquals(0.94f, insaneConfig.customPosY, 0.001f)
        assertEquals(0.75f, insaneConfig.overallScale, 0.001f)
        assertEquals(1.0f, insaneConfig.panelOpacity, 0.001f)
        assertEquals(100, insaneConfig.panelWidthPercent)

        // Panel wider than screen & NaN relative coordinates must still return finite safe offsets
        val (ox, oy) = ActionPanelView.computeClampedCustomOffsets(
            relX = Float.NaN,
            relY = Float.POSITIVE_INFINITY,
            containerWidth = 320,
            containerHeight = 480,
            panelWidth = 600,
            panelHeight = 900,
            insetLeft = 24,
            insetTop = 48,
            insetRight = 24,
            insetBottom = 48
        )
        assertTrue(ox.isFinite() && oy.isFinite())
        assertEquals(24f, ox, 0.01f)
        assertEquals(48f, oy, 0.01f)
    }

    @Test
    fun webViewAdBlockerBlocksAdNetworksAndPopupsWithoutBreakingSearchEnginesOrCaptcha() {
        val page = "https://yandex.com/images/"

        // Must block major ad/tracker/popunder networks
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://googleads.g.doubleclick.net/pagead/ads?id=1", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://cdn.taboola.com/libtrc/loader.js", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://widgets.outbrain.com/outbrain.js", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://an.yandex.ru/system/context.js", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://yabs.yandex.ru/count/123", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://cdn.popads.net/pop.js", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://syndication.exoclick.com/ads.php", page, false))
        assertTrue(WebViewAdBlocker.shouldBlockRequest("https://pl123.highcpmrevenuegate.com/redirect", page, true))

        // Must NEVER block legitimate visual search engines or CAPTCHA/Cloudflare verification
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://yandex.com/images/search?cbir_id=123", page, true))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://tineye.com/search/abc", "https://tineye.com/", true))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://saucenao.com/search.php", "https://saucenao.com/", true))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://trace.moe/", "https://trace.moe/", true))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://www.bing.com/visualsearch", "https://www.bing.com/", true))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://challenges.cloudflare.com/turnstile/v0/api.js", page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://js.hcaptcha.com/1/api.js", page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("https://www.google.com/recaptcha/api.js", page, false))

        // Must survive non-HTTP, empty, null, and malformed URLs cleanly
        assertFalse(WebViewAdBlocker.shouldBlockRequest(null, page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("", page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("data:image/png;base64,abc", page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("blob:https://yandex.com/1234", page, false))
        assertFalse(WebViewAdBlocker.shouldBlockRequest("not a valid url :::", page, false))
    }

    private fun hsvToArgb(hueDeg: Float, sat: Float, value: Float): Int {
        val c = value * sat
        val x = c * (1f - abs(((hueDeg / 60f) % 2f) - 1f))
        val m = value - c
        val (r1, g1, b1) = when {
            hueDeg < 60f -> Triple(c, x, 0f)
            hueDeg < 120f -> Triple(x, c, 0f)
            hueDeg < 180f -> Triple(0f, c, x)
            hueDeg < 240f -> Triple(0f, x, c)
            hueDeg < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
