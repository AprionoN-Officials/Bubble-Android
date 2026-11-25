package com.bubble.screentools

import com.bubble.screentools.capture.BitmapCropHelper
import com.bubble.screentools.qr.QrCodeParser
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.ui.overlay.SelectionGestureHandler
import com.bubble.screentools.ui.overlay.SelectionGestureHandler.BoundsF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleCoreLogicTest {

    @Test
    fun detectHitHandleIdentifiesMoveAndCorners() {
        val rect = BoundsF(100f, 100f, 300f, 400f)
        val corner = SelectionGestureHandler.detectHitHandle(300f, 400f, rect, 28f)
        assertEquals(SelectionGestureHandler.HandleType.BOTTOM_RIGHT, corner)

        val inside = SelectionGestureHandler.detectHitHandle(200f, 250f, rect, 28f)
        assertEquals(SelectionGestureHandler.HandleType.INSIDE_MOVE, inside)

        val outside = SelectionGestureHandler.detectHitHandle(20f, 20f, rect, 28f)
        assertEquals(SelectionGestureHandler.HandleType.NONE, outside)
    }

    @Test
    fun applyResizeClampsToBoundsAndMinimumSize() {
        val rect = BoundsF(100f, 100f, 200f, 200f)
        SelectionGestureHandler.applyResize(
            rect = rect,
            handle = SelectionGestureHandler.HandleType.BOTTOM_RIGHT,
            dx = -150f,
            dy = 500f,
            minSizePx = 40f,
            boundsWidth = 500f,
            boundsHeight = 500f
        )
        assertEquals(140f, rect.right, 0.01f)
        assertEquals(500f, rect.bottom, 0.01f)
    }

    @Test
    fun computeScaleFactorDownscalesOnlyWhenExceedingMaxDimension() {
        assertEquals(1f, BitmapCropHelper.computeScaleFactor(800, 600, 1280), 0.001f)
        assertEquals(0.5f, BitmapCropHelper.computeScaleFactor(2560, 1200, 1280), 0.001f)
    }

    @Test
    fun swapLanguagesHandlesExplicitAndAutoModes() {
        assertEquals("id" to "en", SupportedLanguages.swap("en", "id"))
        assertEquals("ja" to "id", SupportedLanguages.swap("id", "ja"))
        assertEquals("id" to "ja", SupportedLanguages.swap("auto", "id", lastDetectedSourceCode = "ja"))
        assertEquals("id" to "en", SupportedLanguages.swap("auto", "id", lastDetectedSourceCode = null))
        assertEquals("en" to "id", SupportedLanguages.swap("auto", "en", lastDetectedSourceCode = null))
    }

    @Test
    fun qrCodeParserParsesIndonesianQrisEmvCoPayload() {
        // Minimal valid EMVCo QRIS TLV payload with Merchant Name, City, Currency IDR (360), Amount 25000
        val qris = "00020101021226180014ID.CO.QRIS.WWW53033605405250005802ID5911WARUNG KOPI6007JAKARTA6304ABCD"
        val parsed = QrCodeParser.parse(qris)
        assertTrue(parsed is QrCodeParser.ParsedQr.Payment)
        val payment = parsed as QrCodeParser.ParsedQr.Payment
        assertEquals("QRIS", payment.scheme)
        assertEquals("WARUNG KOPI", payment.merchantName)
        assertEquals("JAKARTA", payment.merchantCity)
        assertTrue(payment.formattedAmount!!.contains("25"))
        assertEquals(false, payment.isDirectUri)
    }

    @Test
    fun qrCodeParserParsesWifiAndPaymentUri() {
        val wifi = QrCodeParser.parse("WIFI:T:WPA;S:Kopi\\;Gratis;P:rahasia123;H:false;;")
        assertTrue(wifi is QrCodeParser.ParsedQr.Wifi)
        val w = wifi as QrCodeParser.ParsedQr.Wifi
        assertEquals("Kopi;Gratis", w.ssid)
        assertEquals("rahasia123", w.password)
        assertEquals("WPA", w.securityType)

        val upi = QrCodeParser.parse("upi://pay?pa=toko@upi&pn=Toko%20Jaya&am=15000")
        assertTrue(upi is QrCodeParser.ParsedQr.Payment)
        val p = upi as QrCodeParser.ParsedQr.Payment
        assertEquals("UPI", p.scheme)
        assertEquals("Toko Jaya", p.merchantName)
        assertEquals(true, p.isDirectUri)
    }

    @Test
    fun localeHelperUsesSingleNonDuplicateLanguageTags() {
        assertEquals("id", com.bubble.screentools.util.LocaleHelper.resolveLanguageTag(com.bubble.screentools.util.LocaleHelper.LANG_INDONESIAN))
        assertEquals("en", com.bubble.screentools.util.LocaleHelper.resolveLanguageTag(com.bubble.screentools.util.LocaleHelper.LANG_ENGLISH))
        assertEquals("", com.bubble.screentools.util.LocaleHelper.resolveLanguageTag(com.bubble.screentools.util.LocaleHelper.LANG_SYSTEM))
    }
}
