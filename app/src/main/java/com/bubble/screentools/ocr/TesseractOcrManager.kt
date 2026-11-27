package com.bubble.screentools.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import com.bubble.screentools.model.TextBlockResult
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tesseract 5 OCR Manager supporting user-imported & downloadable `.traineddata` models
 * stored privately in `filesDir/tessdata/` (no external storage permissions needed).
 */
class TesseractOcrManager(private val context: Context) {

    data class InstalledTessModel(
        val code: String,
        val fileName: String,
        val sizeBytes: Long
    )

    data class QuickDownloadModel(
        val code: String,
        val label: String
    )

    private val tessLock = Any()
    private var tessApi: TessBaseAPI? = null
    private var loadedLang: String? = null

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        cropOffsetLeft: Float,
        cropOffsetTop: Float,
        scaleFactor: Float,
        langCode: String,
        scaleY: Float = scaleFactor
    ): List<TextBlockResult> = withContext(Dispatchers.Default) {
        val cleanLang = langCode.trim()
        if (cleanLang.isEmpty()) return@withContext emptyList()
        if (!hasAllRequestedModels(context, cleanLang)) return@withContext emptyList()
        if (bitmap.isRecycled) return@withContext emptyList()

        val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        try {
            synchronized(tessLock) {
                if (safeBitmap.isRecycled) return@synchronized emptyList()
                val api = getOrInitApiLocked(cleanLang) ?: return@synchronized emptyList()
                api.setImage(safeBitmap)
                // Trigger recognition
                val fullText = api.utF8Text?.trim().orEmpty()
                if (fullText.isEmpty()) return@synchronized emptyList()

                val results = mutableListOf<TextBlockResult>()
                val iterator = api.resultIterator
                if (iterator != null) {
                    try {
                        iterator.begin()
                        do {
                            val lineText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                                ?.trim()
                                .orEmpty()
                            val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                            if (lineText.isNotEmpty() && box != null && box.width() > 2 && box.height() > 2) {
                                val mapped = RectF(
                                    cropOffsetLeft + box.left / scaleFactor,
                                    cropOffsetTop + box.top / scaleY,
                                    cropOffsetLeft + box.right / scaleFactor,
                                    cropOffsetTop + box.bottom / scaleY
                                )
                                results.add(
                                    TextBlockResult(
                                        text = lineText,
                                        boundingBox = mapped,
                                        fromAccessibility = false
                                    )
                                )
                            }
                        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                    } finally {
                        iterator.delete()
                    }
                }

                if (results.isEmpty() && fullText.isNotEmpty()) {
                    results.add(
                        TextBlockResult(
                            text = fullText,
                            boundingBox = RectF(
                                cropOffsetLeft,
                                cropOffsetTop,
                                cropOffsetLeft + safeBitmap.width / scaleFactor,
                                cropOffsetTop + safeBitmap.height / scaleY
                            ),
                            fromAccessibility = false
                        )
                    )
                }
                results
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            if (safeBitmap !== bitmap && !safeBitmap.isRecycled) {
                safeBitmap.recycle()
            }
        }
    }

    private fun getOrInitApiLocked(langCode: String): TessBaseAPI? {
        if (tessApi != null && loadedLang == langCode) {
            return tessApi
        }
        releaseLocked()
        val dataPath = getDataRootDir(context).absolutePath
        val api = TessBaseAPI()
        val ok = runCatching { api.init(dataPath, langCode) }.getOrDefault(false)
        if (!ok) {
            runCatching { api.recycle() }
            return null
        }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        tessApi = api
        loadedLang = langCode
        return api
    }

    private fun releaseLocked() {
        runCatching { tessApi?.recycle() }
        tessApi = null
        loadedLang = null
    }

    fun release() {
        synchronized(tessLock) {
            releaseLocked()
        }
    }

    companion object {
        private const val TESSDATA_DIR = "tessdata"
        private const val TESSDATA_FAST_BASE_URL =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"

        val QUICK_DOWNLOAD_MODELS: List<QuickDownloadModel> = listOf(
            QuickDownloadModel("jpn", "Japanese / Jepang (jpn)"),
            QuickDownloadModel("kor", "Korean / Korea (kor)"),
            QuickDownloadModel("chi_sim", "Chinese Simplified / Mandarin (chi_sim)"),
            QuickDownloadModel("chi_tra", "Chinese Traditional (chi_tra)"),
            QuickDownloadModel("ara", "Arabic / Arab (ara)"),
            QuickDownloadModel("tha", "Thai / Thailand (tha)"),
            QuickDownloadModel("vie", "Vietnamese / Vietnam (vie)"),
            QuickDownloadModel("rus", "Russian / Rusia (rus)"),
            QuickDownloadModel("ind", "Indonesian / Indonesia (ind)"),
            QuickDownloadModel("eng", "English / Inggris (eng)")
        )

        fun getDataRootDir(context: Context): File {
            val root = File(context.filesDir, "tesseract")
            File(root, TESSDATA_DIR).mkdirs()
            return root
        }

        fun getTessdataDir(context: Context): File {
            return File(getDataRootDir(context), TESSDATA_DIR).apply { mkdirs() }
        }

        fun listInstalledModels(context: Context): List<InstalledTessModel> {
            val dir = getTessdataDir(context)
            return dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".traineddata", ignoreCase = true) && it.length() > 1024 }
                ?.map { file ->
                    InstalledTessModel(
                        code = file.name.removeSuffix(".traineddata"),
                        fileName = file.name,
                        sizeBytes = file.length()
                    )
                }
                ?.sortedBy { it.code }
                ?: emptyList()
        }

        fun hasAllRequestedModels(context: Context, langSpec: String): Boolean {
            val dir = getTessdataDir(context)
            val codes = langSpec.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            if (codes.isEmpty()) return false
            return codes.all { code ->
                val f = File(dir, "$code.traineddata")
                f.exists() && f.length() > 1024
            }
        }

        fun deleteModel(context: Context, code: String): Boolean {
            val file = File(getTessdataDir(context), "$code.traineddata")
            return !file.exists() || file.delete()
        }

        const val MIN_TRAINEDDATA_BYTES = 16 * 1024L // 16 KB minimum for a valid Tesseract model
        const val MAX_TRAINEDDATA_BYTES = 150 * 1024 * 1024L // 150 MB safety cap

        /**
         * Validates a candidate `.traineddata` byte header & size (pure-Kotlin, unit-testable).
         * Rejects empty/oversized files and misnamed text/JSON/HTML/CSV files.
         */
        fun validateTrainedDataPayload(fileName: String, sizeBytes: Long, headerBytes: ByteArray): Result<String> =
            runCatching {
                require(fileName.endsWith(".traineddata", ignoreCase = true)) {
                    "File must have .traineddata extension (example: jpn.traineddata)."
                }
                val rawCode = fileName.removeSuffix(".traineddata").trim()
                val safeCode = rawCode.replace(Regex("[^a-zA-Z0-9_-]"), "")
                require(safeCode.length in 2..24) {
                    "Invalid language identifier '$rawCode'. Use standard codes like jpn, kor, chi_sim."
                }
                require(sizeBytes >= MIN_TRAINEDDATA_BYTES) {
                    "File .traineddata is too small or corrupted (${sizeBytes} B)."
                }
                require(sizeBytes <= MAX_TRAINEDDATA_BYTES) {
                    "File .traineddata exceeds maximum allowed size (150 MB)."
                }
                // Reject obvious plain text / JSON / HTML / CSV files renamed to .traineddata
                val headText = String(headerBytes.take(64).toByteArray(), Charsets.UTF_8).trimStart()
                if (headText.startsWith("{") || headText.startsWith("[") ||
                    headText.startsWith("<html", ignoreCase = true) ||
                    headText.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    headText.startsWith("source,target", ignoreCase = true)
                ) {
                    throw IllegalArgumentException(
                        "File appears to be JSON/HTML/CSV text rather than a binary Tesseract .traineddata model."
                    )
                }
                safeCode
            }

        suspend fun importFromUri(
            context: Context,
            uri: Uri,
            allowOverwrite: Boolean = true
        ): Result<String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = resolveFileName(context, uri)
                        ?: throw IllegalArgumentException("Unable to read file name.")
                    if (!displayName.endsWith(".traineddata", ignoreCase = true)) {
                        throw IllegalArgumentException(
                            "File must be a Tesseract .traineddata OCR model (e.g., jpn.traineddata)."
                        )
                    }
                    val safeFileName = displayName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                    val dest = File(getTessdataDir(context), safeFileName)
                    if (!allowOverwrite && dest.exists()) {
                        throw IllegalStateException("OCR model '$safeFileName' is already installed.")
                    }
                    val temp = File(getTessdataDir(context), "$safeFileName.tmp")

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_TRAINEDDATA_BYTES) {
                                    throw IllegalArgumentException("File exceeds 150 MB limit.")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: throw IllegalStateException("Failed to open file via Storage Access Framework.")

                    val header = temp.inputStream().use { stream ->
                        val buf = ByteArray(64)
                        val count = stream.read(buf).coerceAtLeast(0)
                        buf.copyOf(count)
                    }
                    val validatedCode = validateTrainedDataPayload(safeFileName, temp.length(), header)
                        .getOrElse { err ->
                            temp.delete()
                            throw err
                        }

                    val finalDest = File(getTessdataDir(context), "$validatedCode.traineddata")
                    if (finalDest.exists()) finalDest.delete()
                    if (!temp.renameTo(finalDest)) {
                        temp.copyTo(finalDest, overwrite = true)
                        temp.delete()
                    }
                    validatedCode
                }
            }

        suspend fun downloadOfficialModel(context: Context, code: String): Result<String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val safeCode = code.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
                    require(safeCode.isNotEmpty()) { "Kode bahasa tidak valid." }

                    val url = URL("$TESSDATA_FAST_BASE_URL$safeCode.traineddata")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 45_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Bubble-Android/1.1")
                    }

                    val responseCode = conn.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        throw IllegalStateException("Unduhan gagal (HTTP $responseCode).")
                    }

                    val dest = File(getTessdataDir(context), "$safeCode.traineddata")
                    val temp = File(getTessdataDir(context), "$safeCode.traineddata.tmp")
                    try {
                        conn.inputStream.use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output, bufferSize = 16 * 1024)
                            }
                        }
                        if (temp.length() < 1024) {
                            throw IllegalStateException("File unduhan tidak lengkap.")
                        }
                        if (dest.exists()) dest.delete()
                        if (!temp.renameTo(dest)) {
                            temp.copyTo(dest, overwrite = true)
                            temp.delete()
                        }
                        safeCode
                    } finally {
                        if (temp.exists()) temp.delete()
                        conn.disconnect()
                    }
                }
            }

        private fun resolveFileName(context: Context, uri: Uri): String? {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
            return uri.lastPathSegment?.substringAfterLast('/')
        }
    }
}
