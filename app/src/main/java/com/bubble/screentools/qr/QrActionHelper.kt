package com.bubble.screentools.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.bubble.screentools.lens.TemporaryImageProvider
import kotlin.math.roundToInt

/**
 * Executes smart actions for auto-detected QR codes:
 * - Payment QR (QRIS / UPI / E-Wallets): uses Android's native "Pilih aplikasi untuk membuka"
 *   (ACTION_VIEW) for payment URIs, and provides a dedicated Payment App Picker (with real installed
 *   e-wallet & m-banking app icons, NOT Android Sharesheet!) + native "Buka dengan" for QRIS!
 * - Wi-Fi QR: Android 11+ native ACTION_WIFI_ADD_NETWORKS 1-tap connect + password copy.
 * - URL, Contact, Phone, Email, SMS, Plain Text.
 */
object QrActionHelper {

    data class ActionLabels(
        val primaryLabel: String,
        val secondaryLabel: String
    )

    data class InstalledPaymentApp(
        val packageName: String,
        val appName: String,
        val icon: Drawable?
    )

    // Curated list of popular Indonesian e-wallet & mobile banking packages for clean "Open with" picker
    private val KNOWN_PAYMENT_PACKAGES = listOf(
        "id.dana",
        "com.gojek.gopay",
        "com.gojek.app",
        "ovo.id",
        "com.shopee.id",
        "com.shopeepay.id",
        "com.telkom.mwallet",
        "com.bca",
        "com.bca.mybca.omni.android",
        "id.co.bri.brimo",
        "id.bmri.livin",
        "src.com.bni",
        "id.bni.wondr",
        "com.bsm.activity2",
        "id.co.bankbkemobile.digitalbank",
        "com.jago.digitalBanking",
        "com.bcadigital.blu",
        "com.btpn.dc",
        "com.cimbniaga.octo",
        "com.permata.mobile",
        "com.flip.id",
        "com.astrapay.app",
        "com.mnc.motionpay",
        "com.allobank.app",
        "com.bnc.finance",
        "com.kredivo.android"
    )

    private fun isIndonesianLocale(): Boolean {
        val lang = java.util.Locale.getDefault().language.lowercase()
        return lang == "id" || lang == "in"
    }

    private fun isIndonesian(context: Context): Boolean {
        val lang = context.resources.configuration.locales[0]?.language?.lowercase()
            ?: java.util.Locale.getDefault().language.lowercase()
        return lang == "id" || lang == "in"
    }

    fun getActionLabels(parsed: QrCodeParser.ParsedQr): ActionLabels {
        val id = isIndonesianLocale()
        return when (parsed) {
            is QrCodeParser.ParsedQr.Payment -> ActionLabels(
                primaryLabel = if (id) "Pilih Aplikasi" else "Open With App",
                secondaryLabel = if (parsed.isDirectUri) {
                    if (id) "Salin" else "Copy"
                } else {
                    if (id) "Simpan QR" else "Save QR"
                }
            )
            is QrCodeParser.ParsedQr.Wifi -> ActionLabels(
                primaryLabel = if (id) "Hubungkan Wi-Fi" else "Connect Wi-Fi",
                secondaryLabel = if (parsed.password.isNotBlank()) {
                    if (id) "Salin Sandi" else "Copy Password"
                } else {
                    if (id) "Salin SSID" else "Copy SSID"
                }
            )
            is QrCodeParser.ParsedQr.Url -> ActionLabels(
                primaryLabel = if (id) "Buka Link" else "Open Link",
                secondaryLabel = if (id) "Salin Link" else "Copy Link"
            )
            is QrCodeParser.ParsedQr.Contact -> ActionLabels(
                primaryLabel = if (id) "Simpan Kontak" else "Save Contact",
                secondaryLabel = if (id) "Salin" else "Copy"
            )
            is QrCodeParser.ParsedQr.Email -> ActionLabels(
                primaryLabel = if (id) "Kirim Email" else "Send Email",
                secondaryLabel = if (id) "Salin" else "Copy"
            )
            is QrCodeParser.ParsedQr.Phone -> ActionLabels(
                primaryLabel = if (id) "Hubungi" else "Call",
                secondaryLabel = if (id) "Salin Nomor" else "Copy Number"
            )
            is QrCodeParser.ParsedQr.Sms -> ActionLabels(
                primaryLabel = if (id) "Kirim SMS" else "Send SMS",
                secondaryLabel = if (id) "Salin" else "Copy"
            )
            is QrCodeParser.ParsedQr.PlainText -> ActionLabels(
                primaryLabel = if (id) "Salin Teks" else "Copy Text",
                secondaryLabel = if (id) "Cari di Web" else "Web Search"
            )
        }
    }

    /**
     * Queries installed e-wallet & mobile banking apps on the device for the clean
     * "Pilih aplikasi untuk membuka" payment picker (instead of cluttered Android Sharesheet).
     */
    fun getInstalledPaymentApps(context: Context): List<InstalledPaymentApp> {
        val pm = context.packageManager
        val found = mutableListOf<InstalledPaymentApp>()
        for (pkg in KNOWN_PAYMENT_PACKAGES) {
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull() ?: continue

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
            found.add(InstalledPaymentApp(packageName = pkg, appName = label, icon = icon))
        }
        return found
    }

    /**
     * Opens a specific chosen payment app for a QRIS code:
     * 1. Tries sending the clean white-padded QRIS image directly to that app if it supports image handoff.
     * 2. Otherwise saves the crisp QRIS image to Gallery (Pictures/BubbleQR) and opens the payment app directly!
     */
    fun openQrisInSpecificApp(
        context: Context,
        app: InstalledPaymentApp,
        detected: DetectedQrResult,
        sourceSnapshot: Bitmap?
    ): Boolean {
        val qrBitmap = createCleanQrBitmap(detected, sourceSnapshot)
        if (qrBitmap != null) {
            val uri = TemporaryImageProvider.saveTemporaryCrop(context, qrBitmap)
            if (uri != null) {
                // Check if this specific payment app accepts image handoff directly
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setPackage(app.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val canReceiveDirect = runCatching {
                    val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.queryIntentActivities(
                            sendIntent,
                            PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.queryIntentActivities(sendIntent, 0)
                    }
                    matches.isNotEmpty()
                }.getOrDefault(false)

                if (canReceiveDirect) {
                    val launched = runCatching {
                        context.startActivity(sendIntent)
                        true
                    }.getOrDefault(false)
                    if (!qrBitmap.isRecycled) qrBitmap.recycle()
                    if (launched) return true
                }
            }
            if (!qrBitmap.isRecycled) qrBitmap.recycle()
        }

        // Save crisp QRIS image to Gallery so user can pick it immediately in the app's QRIS scanner
        if (sourceSnapshot != null) {
            saveQrImageToGallery(context, detected, sourceSnapshot)
        }
        copyToClipboard(context, "Kode QRIS", detected.rawValue, silent = true)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            Toast.makeText(
                context,
                "QRIS disimpan di Galeri (BubbleQR). Membuka ${app.appName}...",
                Toast.LENGTH_LONG
            ).show()
            return runCatching {
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        }
        return false
    }

    /**
     * Triggers Android's native "Pilih aplikasi untuk membuka" (ACTION_VIEW) dialog — NOT Sharesheet!
     */
    fun openQrWithAndroidOpenWith(
        context: Context,
        detected: DetectedQrResult,
        sourceSnapshot: Bitmap?
    ): Boolean {
        val chooserTitle = if (isIndonesian(context)) "Pilih aplikasi untuk membuka" else "Open with"
        val parsed = detected.parsed
        if (parsed is QrCodeParser.ParsedQr.Payment && parsed.isDirectUri) {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(parsed.rawValue)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(chooser)
                true
            }.getOrDefault(false)
        }

        val qrBitmap = createCleanQrBitmap(detected, sourceSnapshot) ?: return false
        val uri = try {
            TemporaryImageProvider.saveTemporaryCrop(context, qrBitmap)
        } finally {
            if (!qrBitmap.isRecycled) qrBitmap.recycle()
        } ?: return false

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /**
     * Executes primary action for non-QRIS QR types (or direct payment URIs via Android's native Open With).
     * Returns true if the overlay should dismiss after launching an external app.
     */
    fun executePrimaryAction(
        context: Context,
        detected: DetectedQrResult,
        sourceSnapshot: Bitmap?
    ): Boolean {
        return when (val parsed = detected.parsed) {
            is QrCodeParser.ParsedQr.Payment -> openQrWithAndroidOpenWith(context, detected, sourceSnapshot)
            is QrCodeParser.ParsedQr.Wifi -> handleWifiConnect(context, parsed)
            is QrCodeParser.ParsedQr.Url -> {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(parsed.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooserTitle = if (isIndonesian(context)) "Pilih aplikasi untuk membuka" else "Open with"
                val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    context.startActivity(chooser)
                    true
                }.getOrElse { launchExternal(context, viewIntent) }
            }
            is QrCodeParser.ParsedQr.Contact -> handleSaveContact(context, parsed)
            is QrCodeParser.ParsedQr.Email -> {
                val uri = Uri.parse("mailto:${parsed.address}")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    if (!parsed.subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, parsed.subject)
                    if (!parsed.body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, parsed.body)
                }
                launchExternal(context, intent)
            }
            is QrCodeParser.ParsedQr.Phone -> launchExternal(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parsed.number}")))
            is QrCodeParser.ParsedQr.Sms -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${parsed.number}")).apply {
                    if (parsed.message.isNotBlank()) putExtra("sms_body", parsed.message)
                }
                launchExternal(context, intent)
            }
            is QrCodeParser.ParsedQr.PlainText -> {
                copyToClipboard(context, "QR Teks", parsed.rawValue)
                false
            }
        }
    }

    fun executeSecondaryAction(
        context: Context,
        detected: DetectedQrResult,
        sourceSnapshot: Bitmap?
    ): Boolean {
        return when (val parsed = detected.parsed) {
            is QrCodeParser.ParsedQr.Payment -> {
                if (parsed.isDirectUri || sourceSnapshot == null) {
                    copyToClipboard(context, "Kode Pembayaran", parsed.rawValue)
                } else {
                    val saved = saveQrImageToGallery(context, detected, sourceSnapshot)
                    if (saved) {
                        Toast.makeText(
                            context,
                            "Gambar QRIS disimpan ke Galeri (Pictures/BubbleQR).",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        copyToClipboard(context, "Kode QRIS", parsed.rawValue)
                    }
                }
                false
            }
            is QrCodeParser.ParsedQr.Wifi -> {
                val text = parsed.password.ifBlank { parsed.ssid }
                copyToClipboard(context, "Wi-Fi", text)
                false
            }
            is QrCodeParser.ParsedQr.PlainText -> {
                val searchUrl = "https://www.google.com/search?q=${Uri.encode(parsed.rawValue)}"
                launchExternal(context, Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
            }
            else -> {
                copyToClipboard(context, parsed.badgeLabel, parsed.rawValue)
                false
            }
        }
    }

    private fun handleWifiConnect(context: Context, wifi: QrCodeParser.ParsedQr.Wifi): Boolean {
        if (wifi.password.isNotBlank()) {
            copyToClipboard(context, "Password Wi-Fi ${wifi.ssid}", wifi.password, silent = true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.ssid.isNotBlank()) {
            val builder = WifiNetworkSuggestion.Builder()
                .setSsid(wifi.ssid)
                .setIsHiddenSsid(wifi.hidden)

            if (wifi.password.isNotBlank()) {
                if (wifi.securityType.contains("WPA3", ignoreCase = true) ||
                    wifi.securityType.contains("SAE", ignoreCase = true)
                ) {
                    builder.setWpa3Passphrase(wifi.password)
                } else {
                    builder.setWpa2Passphrase(wifi.password)
                }
            }

            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                putParcelableArrayListExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST,
                    arrayListOf(builder.build())
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }

        Toast.makeText(
            context,
            "Sandi Wi-Fi '${wifi.ssid}' disalin. Membuka pengaturan Wi-Fi...",
            Toast.LENGTH_LONG
        ).show()
        return launchExternal(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun handleSaveContact(context: Context, contact: QrCodeParser.ParsedQr.Contact): Boolean {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (contact.name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            if (!contact.phone.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
            if (!contact.email.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
        }
        return launchExternal(context, intent)
    }

    private fun createCleanQrBitmap(detected: DetectedQrResult, source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null
        val safeSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        return try {
            val box = detected.boundingBox
            val cropRect = if (box != null && box.width() > 20f && box.height() > 20f) {
                val pad = (maxOf(box.width(), box.height()) * 0.14f).roundToInt()
                val left = (box.left.roundToInt() - pad).coerceIn(0, safeSource.width - 1)
                val top = (box.top.roundToInt() - pad).coerceIn(0, safeSource.height - 1)
                val right = (box.right.roundToInt() + pad).coerceIn(left + 1, safeSource.width)
                val bottom = (box.bottom.roundToInt() + pad).coerceIn(top + 1, safeSource.height)
                Rect(left, top, right, bottom)
            } else {
                Rect(0, 0, safeSource.width, safeSource.height)
            }

            val cropped = Bitmap.createBitmap(
                safeSource,
                cropRect.left,
                cropRect.top,
                cropRect.width().coerceAtLeast(1),
                cropRect.height().coerceAtLeast(1)
            )
            val borderPx = 40
            val padded = Bitmap.createBitmap(
                cropped.width + borderPx * 2,
                cropped.height + borderPx * 2,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(padded)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(cropped, borderPx.toFloat(), borderPx.toFloat(), null)
            cropped.recycle()
            padded
        } catch (_: Throwable) {
            null
        } finally {
            if (safeSource !== source && !safeSource.isRecycled) {
                safeSource.recycle()
            }
        }
    }

    private fun saveQrImageToGallery(
        context: Context,
        detected: DetectedQrResult,
        sourceSnapshot: Bitmap
    ): Boolean {
        val qrBitmap = createCleanQrBitmap(detected, sourceSnapshot) ?: return false
        return try {
            val filename = "Bubble_QRIS_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BubbleQR")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            if (!qrBitmap.isRecycled) qrBitmap.recycle()
        }
    }

    private fun launchExternal(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.onFailure {
            val msg = if (isIndonesian(context)) {
                "Tidak ada aplikasi yang dapat membuka tautan ini."
            } else {
                "No app is available to open this link."
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }.getOrDefault(false)
    }

    fun copyToClipboard(
        context: Context,
        label: String,
        text: String,
        silent: Boolean = false
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        if (!silent) {
            Toast.makeText(context, "$label disalin ke clipboard.", Toast.LENGTH_SHORT).show()
        }
    }
}
