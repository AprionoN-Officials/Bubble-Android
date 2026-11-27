package com.bubble.screentools

import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.ActionPanelConfig
import com.bubble.screentools.model.ActionPanelPosition
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchEngineType
import com.bubble.screentools.model.SearchImageMethod
import com.bubble.screentools.model.ThemePreset
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.overlay.ActionPanelView
import com.bubble.screentools.ui.overlay.SelectionPathBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests covering:
 * - Action Panel default 2-button invariant, >=1 active guard, and insets-clamped Custom Position math
 * - Selection Path Builder superellipse curvature & freeform bounds
 * - Search Engine Registry URL/template validation & Visual/Text separation
 * - Theme Palette Generator Light/Dark/AMOLED synthesis & WCAG contrast ratios
 */
class PersonalizationSystemsTest {

    @Test
    fun actionPanelDefaultStrictlyPreservesTwoOriginalButtonsAtBottom() {
        val defaultCfg = ActionPanelConfig()
        assertEquals(
            listOf(ActionPanelButtonType.TRANSLATE, ActionPanelButtonType.SEARCH_IMAGE),
            defaultCfg.enabledButtons
        )
        assertEquals(ActionPanelPosition.BOTTOM, defaultCfg.position)

        // Sanitizer never allows zero enabled buttons
        val sanitizedEmpty = defaultCfg.copy(enabledButtons = emptyList()).sanitized()
        assertEquals(1, sanitizedEmpty.enabledButtons.size)
        assertEquals(ActionPanelButtonType.TRANSLATE, sanitizedEmpty.enabledButtons.first())

        // Verify removed tools are absent, FULL_SCREEN is available, and default stays 2 buttons
        assertEquals(7, ActionPanelButtonType.entries.size)
        assertEquals(ActionPanelButtonType.FULL_SCREEN, ActionPanelButtonType.fromId("full_screen"))
        assertEquals(null, ActionPanelButtonType.fromId("read_aloud"))
        assertEquals(null, ActionPanelButtonType.fromId("color_picker"))
        assertEquals(null, ActionPanelButtonType.fromId("scan_qr"))
    }

    @Test
    fun actionPanelCustomPositionClampsSafelyWithinInsetsOnRotation() {
        // Attempt to place panel far off bottom-right corner
        val (x1, y1) = ActionPanelView.computeClampedCustomOffsets(
            relX = 1.0f,
            relY = 1.0f,
            containerWidth = 1080,
            containerHeight = 2400,
            panelWidth = 400,
            panelHeight = 120,
            insetLeft = 20,
            insetTop = 80,
            insetRight = 20,
            insetBottom = 100
        )
        assertEquals(660f, x1, 0.01f)
        assertEquals(2180f, y1, 0.01f)

        // Attempt to place panel over status bar top-left
        val (x2, y2) = ActionPanelView.computeClampedCustomOffsets(
            relX = 0.0f,
            relY = 0.0f,
            containerWidth = 2400,
            containerHeight = 1080,
            panelWidth = 400,
            panelHeight = 120,
            insetLeft = 40,
            insetTop = 60,
            insetRight = 40,
            insetBottom = 60
        )
        assertEquals(40f, x2, 0.01f)
        assertEquals(60f, y2, 0.01f)
    }

    @Test
    fun selectionPathBuilderComputesSuperellipseAndFreeformBoundsAccurately() {
        val pts = SelectionPathBuilder.computeSuperellipsePoints(
            left = 100f, top = 200f, right = 300f, bottom = 500f, curvatureLevel = 0.6f, steps = 64
        )
        assertEquals(64, pts.size)
        for ((x, y) in pts) {
            assertTrue(x in 99.9f..300.1f)
            assertTrue(y in 199.9f..500.1f)
        }

        val freeformBounds = SelectionPathBuilder.computePointsBounds(
            listOf(50f to 80f, 120f to 40f, 220f to 190f, 30f to 150f)
        )
        assertEquals(30f, freeformBounds.left, 0.01f)
        assertEquals(40f, freeformBounds.top, 0.01f)
        assertEquals(220f, freeformBounds.right, 0.01f)
        assertEquals(190f, freeformBounds.bottom, 0.01f)
    }

    @Test
    fun searchEngineRegistryValidatesUrlsAndSeparatesVisualAndTextEngines() {
        assertTrue(SearchEngineRegistry.isValidHttpUrl("https://saucenao.com/"))
        assertFalse(SearchEngineRegistry.isValidHttpUrl("javascript:alert(1)"))
        assertFalse(SearchEngineRegistry.isValidHttpUrl("not a url"))

        // Valid custom text search engine
        val validText = SearchEngineItem(
            id = "", name = "Kagi", type = SearchEngineType.TEXT,
            baseUrl = "https://kagi.com", textQueryTemplate = "https://kagi.com/search?q={query}"
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(validText).isSuccess)
        val builtUrl = SearchEngineRegistry.buildTextSearchUrl(validText, "hello world")
        assertEquals("https://kagi.com/search?q=hello+world", builtUrl)

        // Reject text engine missing {query}
        val badText = validText.copy(textQueryTemplate = "https://kagi.com/search")
        assertTrue(SearchEngineRegistry.validateCustomEngine(badText).isFailure)

        // Valid custom visual web engine vs invalid package
        val validVisual = SearchEngineItem(
            id = "", name = "Custom Reverse", type = SearchEngineType.VISUAL,
            baseUrl = "https://images.example.com/upload",
            imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(validVisual).isSuccess)

        val badPackage = validVisual.copy(
            imageMethod = SearchImageMethod.CUSTOM_INTENT_PACKAGE,
            targetPackage = "invalid package!"
        )
        assertTrue(SearchEngineRegistry.validateCustomEngine(badPackage).isFailure)
    }

    @Test
    fun themePaletteGeneratorProducesAccessibleContrastAndTrueAmoledBlack() {
        for (preset in ThemePreset.entries) {
            val seed = ThemePaletteGenerator.parseHexColor(preset.seedHex)
            assertNotNull(seed)

            val light = ThemePaletteGenerator.generate(seed!!, isDark = false, amoledBlack = false)
            val dark = ThemePaletteGenerator.generate(seed, isDark = true, amoledBlack = false)
            val amoled = ThemePaletteGenerator.generate(seed, isDark = true, amoledBlack = true)

            assertEquals(0xFF000000.toInt(), amoled.surface)
            assertTrue("Light onSurface contrast must be >= 7", ThemePaletteGenerator.contrastRatio(light.onSurface, light.surface) >= 7.0)
            assertTrue("Dark onSurface contrast must be >= 7", ThemePaletteGenerator.contrastRatio(dark.onSurface, dark.surface) >= 7.0)
            assertTrue("Light onPrimary contrast must be >= 4.5", ThemePaletteGenerator.contrastRatio(light.onPrimary, light.primary) >= 4.5)
            assertTrue("Dark onPrimary contrast must be >= 4.5", ThemePaletteGenerator.contrastRatio(dark.onPrimary, dark.primary) >= 4.5)
        }
    }
}
