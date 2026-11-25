package com.bubble.screentools.qr

import java.text.NumberFormat
import java.util.Locale

/**
 * Pure Kotlin QR payload parser supporting:
 * - Indonesian QRIS / EMVCo payment payloads (TLV parser: merchant name, city, amount, provider)
 * - Payment deep links (UPI, DANA, GoPay/Gojek, OVO, ShopeePay, LinkAja, Crypto)
 * - Wi-Fi QR (WIFI:T:...;S:...;P:...;;)
 * - URLs, vCard / MECARD contacts, Email, Phone, SMS, Geo, and Plain Text.
 */
object QrCodeParser {

    private fun isIdLocale(): Boolean {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return lang == "id" || lang == "in"
    }

    sealed class ParsedQr {
        abstract val rawValue: String
        abstract val badgeLabel: String
        abstract val title: String
        abstract val subtitle: String

        data class Payment(
            override val rawValue: String,
            val scheme: String,
            val merchantName: String?,
            val merchantCity: String?,
            val formattedAmount: String?,
            val isDirectUri: Boolean
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "QR Pembayaran • $scheme" else "Payment QR • $scheme"
            override val title: String
                get() = merchantName?.takeIf { it.isNotBlank() }
                    ?: if (isIdLocale()) "Kode Pembayaran $scheme" else "$scheme Payment Code"
            override val subtitle: String = buildList {
                if (!formattedAmount.isNullOrBlank()) add(formattedAmount)
                if (!merchantCity.isNullOrBlank()) add(merchantCity)
                if (isEmpty()) add(rawValue.take(48))
            }.joinToString(" • ")
        }

        data class Wifi(
            override val rawValue: String,
            val ssid: String,
            val password: String,
            val securityType: String,
            val hidden: Boolean
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "QR Wi-Fi" else "Wi-Fi QR"
            override val title: String
                get() = ssid.ifBlank { if (isIdLocale()) "Jaringan Wi-Fi" else "Wi-Fi Network" }
            override val subtitle: String
                get() = buildString {
                    append(securityType)
                    if (password.isNotBlank()) {
                        append(if (isIdLocale()) " • Sandi: " else " • Password: ")
                        append(password)
                    } else {
                        append(if (isIdLocale()) " • Tanpa Sandi" else " • Open Network")
                    }
                }
        }

        data class Url(
            override val rawValue: String,
            val url: String,
            val host: String
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "Tautan / URL" else "Link / URL"
            override val title: String = host.ifBlank { url }
            override val subtitle: String = url
        }

        data class Contact(
            override val rawValue: String,
            val name: String,
            val phone: String?,
            val email: String?
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "Kontak" else "Contact"
            override val title: String
                get() = name.ifBlank { if (isIdLocale()) "Kartu Kontak" else "Contact Card" }
            override val subtitle: String = listOfNotNull(phone, email).joinToString(" • ")
        }

        data class Email(
            override val rawValue: String,
            val address: String,
            val subject: String?,
            val body: String?
        ) : ParsedQr() {
            override val badgeLabel: String = "Email"
            override val title: String = address
            override val subtitle: String
                get() = subject?.ifBlank { null } ?: if (isIdLocale()) "Kirim email" else "Send email"
        }

        data class Phone(
            override val rawValue: String,
            val number: String
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "Telepon" else "Phone"
            override val title: String = number
            override val subtitle: String
                get() = if (isIdLocale()) "Ketuk untuk menghubungi" else "Tap to call"
        }

        data class Sms(
            override val rawValue: String,
            val number: String,
            val message: String
        ) : ParsedQr() {
            override val badgeLabel: String = "SMS"
            override val title: String = number
            override val subtitle: String
                get() = message.ifBlank { if (isIdLocale()) "Kirim pesan SMS" else "Send SMS message" }
        }

        data class PlainText(
            override val rawValue: String
        ) : ParsedQr() {
            override val badgeLabel: String
                get() = if (isIdLocale()) "QR Teks" else "Text QR"
            override val title: String = rawValue.lines().firstOrNull()?.take(60).orEmpty()
            override val subtitle: String = rawValue.take(100)
        }
    }

    fun parse(raw: String): ParsedQr {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedQr.PlainText("")

        // 1. EMVCo / QRIS Indonesia (starts with 000201)
        if (trimmed.startsWith("000201") && trimmed.length >= 24) {
            parseEmvCoQris(trimmed)?.let { return it }
        }

        // 2. Payment deep-link URIs (UPI, Indonesian e-wallets, Crypto)
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("upi://") ||
            lower.startsWith("dana://") ||
            lower.startsWith("gojek://") ||
            lower.startsWith("ovo://") ||
            lower.startsWith("shopeepay://") ||
            lower.startsWith("linkaja://") ||
            lower.startsWith("bitcoin:") ||
            lower.startsWith("ethereum:")
        ) {
            return parsePaymentUri(trimmed)
        }

        // 3. Wi-Fi QR
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            parseWifi(trimmed)?.let { return it }
        }

        // 4. URL
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val host = trimmed.substringAfter("://").substringBefore('/').substringBefore('?')
            return ParsedQr.Url(rawValue = trimmed, url = trimmed, host = host)
        }

        // 5. Contact (vCard / MECARD)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            return parseVCard(trimmed)
        }
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            return parseMeCard(trimmed)
        }

        // 6. Email
        if (lower.startsWith("mailto:") || trimmed.startsWith("MATMSG:", ignoreCase = true)) {
            return parseEmail(trimmed)
        }

        // 7. Phone
        if (lower.startsWith("tel:")) {
            return ParsedQr.Phone(rawValue = trimmed, number = trimmed.substring(4).trim())
        }

        // 8. SMS
        if (lower.startsWith("smsto:") || lower.startsWith("sms:")) {
            val bodyPart = trimmed.substringAfter(':')
            val parts = bodyPart.split(':', limit = 2)
            return ParsedQr.Sms(
                rawValue = trimmed,
                number = parts.getOrNull(0)?.trim().orEmpty(),
                message = parts.getOrNull(1)?.trim().orEmpty()
            )
        }

        return ParsedQr.PlainText(trimmed)
    }

    private fun parseEmvCoQris(raw: String): ParsedQr.Payment? {
        val tags = parseTlv(raw)
        if (tags.isEmpty()) return null
        // Must have payload format indicator 00 == "01"
        if (tags["00"] != "01") return null

        val merchantName = tags["59"]?.trim()
        val merchantCity = tags["60"]?.trim()
        val currencyCode = tags["53"]?.trim()
        val amountRaw = tags["54"]?.trim()

        // Detect Indonesian QRIS or specific payment network from tags 26..51
        var scheme = "EMV QR"
        for (tagNum in 26..51) {
            val value = tags[tagNum.toString().padStart(2, '0')] ?: continue
            val upper = value.uppercase(Locale.ROOT)
            when {
                upper.contains("QRIS") || upper.contains("ID.CO.") || upper.contains("COM.GO-JEK") ||
                    upper.contains("ID.DANA") || upper.contains("ID.OVO") || upper.contains("ID.LINKAJA") -> {
                    scheme = "QRIS"
                    break
                }
                upper.contains("SG.PAYNOW") -> {
                    scheme = "PayNow"
                    break
                }
                upper.contains("MY.DUITNOW") -> {
                    scheme = "DuitNow"
                    break
                }
                upper.contains("A000000677010111") -> {
                    scheme = "PromptPay"
                    break
                }
            }
        }
        if (scheme == "EMV QR" && tags["58"]?.equals("ID", ignoreCase = true) == true) {
            scheme = "QRIS"
        }

        val formattedAmount = amountRaw?.toDoubleOrNull()?.let { amt ->
            if (currencyCode == "360" || scheme == "QRIS") {
                val nf = NumberFormat.getNumberInstance(Locale("in", "ID")).apply {
                    maximumFractionDigits = if (amt % 1.0 == 0.0) 0 else 2
                }
                "Rp ${nf.format(amt)}"
            } else {
                amountRaw
            }
        }

        return ParsedQr.Payment(
            rawValue = raw,
            scheme = scheme,
            merchantName = merchantName,
            merchantCity = merchantCity,
            formattedAmount = formattedAmount,
            isDirectUri = false
        )
    }

    private fun parseTlv(input: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var idx = 0
        while (idx + 4 <= input.length) {
            val tag = input.substring(idx, idx + 2)
            val lenStr = input.substring(idx + 2, idx + 4)
            val len = lenStr.toIntOrNull() ?: break
            val valueStart = idx + 4
            val valueEnd = valueStart + len
            if (len < 0 || valueEnd > input.length) break
            result[tag] = input.substring(valueStart, valueEnd)
            idx = valueEnd
        }
        return result
    }

    private fun parsePaymentUri(raw: String): ParsedQr.Payment {
        val scheme = raw.substringBefore(':').uppercase(Locale.ROOT)
        val query = raw.substringAfter('?', "")
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx).lowercase(Locale.ROOT) to part.substring(idx + 1)
        }.toMap()

        val merchant = params["pn"] ?: params["pa"] ?: params["merchant"]
        val amount = params["am"] ?: params["amount"]
        return ParsedQr.Payment(
            rawValue = raw,
            scheme = scheme,
            merchantName = merchant?.replace("%20", " "),
            merchantCity = null,
            formattedAmount = amount,
            isDirectUri = true
        )
    }

    private fun parseWifi(raw: String): ParsedQr.Wifi? {
        val content = raw.substring(5)
        val fields = splitEscaped(content, ';')
        var ssid = ""
        var password = ""
        var security = "WPA/WPA2"
        var hidden = false

        for (field in fields) {
            val colonIdx = field.indexOf(':')
            if (colonIdx <= 0) continue
            val key = field.substring(0, colonIdx).uppercase(Locale.ROOT)
            val value = unescapeWifiValue(field.substring(colonIdx + 1))
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> security = when (value.uppercase(Locale.ROOT)) {
                    "WPA", "WPA2", "WPA3", "SAE" -> value.uppercase(Locale.ROOT)
                    "WEP" -> "WEP"
                    "NOPASS", "" -> "Terbuka"
                    else -> value
                }
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }
        if (ssid.isBlank() && password.isBlank()) return null
        return ParsedQr.Wifi(
            rawValue = raw,
            ssid = ssid,
            password = password,
            securityType = security,
            hidden = hidden
        )
    }

    private fun splitEscaped(input: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                sb.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                if (sb.isNotEmpty()) {
                    parts.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())
        return parts
    }

    private fun unescapeWifiValue(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                sb.append(value[i + 1])
                i += 2
            } else {
                sb.append(value[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun parseVCard(raw: String): ParsedQr.Contact {
        var name = ""
        var phone: String? = null
        var email: String? = null
        for (line in raw.lines()) {
            val upper = line.uppercase(Locale.ROOT)
            when {
                upper.startsWith("FN:") -> name = line.substringAfter(':').trim()
                upper.startsWith("N:") && name.isBlank() -> name = line.substringAfter(':').replace(';', ' ').trim()
                upper.startsWith("TEL") && phone == null -> phone = line.substringAfter(':').trim()
                upper.startsWith("EMAIL") && email == null -> email = line.substringAfter(':').trim()
            }
        }
        return ParsedQr.Contact(rawValue = raw, name = name, phone = phone, email = email)
    }

    private fun parseMeCard(raw: String): ParsedQr.Contact {
        val fields = splitEscaped(raw.substring(7), ';')
        var name = ""
        var phone: String? = null
        var email: String? = null
        for (field in fields) {
            val idx = field.indexOf(':')
            if (idx <= 0) continue
            val k = field.substring(0, idx).uppercase(Locale.ROOT)
            val v = unescapeWifiValue(field.substring(idx + 1))
            when (k) {
                "N" -> name = v.replace(',', ' ').trim()
                "TEL" -> if (phone == null) phone = v
                "EMAIL" -> if (email == null) email = v
            }
        }
        return ParsedQr.Contact(rawValue = raw, name = name, phone = phone, email = email)
    }

    private fun parseEmail(raw: String): ParsedQr.Email {
        if (raw.startsWith("MATMSG:", ignoreCase = true)) {
            val fields = splitEscaped(raw.substring(7), ';')
            var to = ""
            var sub: String? = null
            var body: String? = null
            for (f in fields) {
                val idx = f.indexOf(':')
                if (idx <= 0) continue
                when (f.substring(0, idx).uppercase(Locale.ROOT)) {
                    "TO" -> to = f.substring(idx + 1)
                    "SUB" -> sub = f.substring(idx + 1)
                    "BODY" -> body = f.substring(idx + 1)
                }
            }
            return ParsedQr.Email(rawValue = raw, address = to, subject = sub, body = body)
        }
        val withoutScheme = raw.substringAfter(':')
        val address = withoutScheme.substringBefore('?')
        val query = withoutScheme.substringAfter('?', "")
        val subject = query.split('&').firstOrNull { it.startsWith("subject=", true) }?.substringAfter('=')
        return ParsedQr.Email(rawValue = raw, address = address, subject = subject, body = null)
    }
}
