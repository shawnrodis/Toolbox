package com.shawnrodis.toolbox.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared export helpers — native port of the source's shared export block:
 * __exportTimestamp, __stampedFilename, __exportShowHeader/HideHeader,
 * __captureElJPGBlob, __exportElToJPG, __exportElToPDF, exportAppToJPG/PDF,
 * exportToolToJPG/PDF, exportSplit/Tip/Tally*, shareAppToJPG, __shareTextFor
 * (the per-tool text builders live with their screens, which own the state),
 * and __copyShareText (the clipboard fallback — see ui.copyToClipboard).
 *
 * Native capture model: the web version screenshots the page body with
 * html2canvas after revealing the tool's hidden print header
 * (.print-visible). The native equivalent renders the tool's summary card
 * — print header title + the tool's current summary lines — to a bitmap on
 * a white background (html2canvas used backgroundColor '#ffffff' and JPEG
 * quality 0.92, both kept). From that one capture:
 *   JPG  -> saved to Pictures with the stamped filename, toast
 *           "<pretty> exported to JPG!"
 *   PDF  -> the bitmap laid into a PdfDocument page, saved to Downloads,
 *           toast "<pretty> exported to PDF!"
 *   Share-> the JPG via the Android share sheet (source: Web Share with
 *           {files, title: prettyName, text: prettyName}); when no share
 *           target exists, the tool's __shareTextFor text is copied to the
 *           clipboard, exactly the source's __copyShareText fallback.
 */

// ---- pure filename logic (JVM-testable) ---------------------------------

/** Source __exportTimestamp(): yyyy-MM-dd_HH-mm-ss, zero-padded. */
fun exportTimestamp(date: Date = Date()): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(date)

/** Source __stampedFilename(): insert _<timestamp> before the extension. */
fun stampedFilename(filename: String, date: Date = Date()): String {
    val i = filename.lastIndexOf('.')
    return (if (i > 0) filename.substring(0, i) else filename) +
        "_" + exportTimestamp(date) +
        (if (i > 0) filename.substring(i) else "")
}

// ---- capture -------------------------------------------------------------

/**
 * Renders the summary card: the print header (shown only for export, like
 * .print-visible) followed by the tool's summary lines, wrapped.
 */
fun renderSummaryBitmap(header: String, lines: List<String>): Bitmap {
    val width = 1080
    val pad = 56f
    val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 41, 59); textSize = 46f; isFakeBoldText = true
    }
    val paintBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(51, 65, 85); textSize = 36f
    }
    fun wrap(paint: Paint, text: String, maxW: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val out = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(t) > maxW && cur.isNotEmpty()) {
                out.add(cur); cur = w
            } else cur = t
        }
        if (cur.isNotEmpty()) out.add(cur)
        return if (out.isEmpty()) listOf("") else out
    }
    val maxW = width - pad * 2
    val titleLines = wrap(paintTitle, header, maxW)
    val bodyLines = lines.flatMap { wrap(paintBody, it, maxW) }
    val lineH = 52f
    val height = (pad * 2 + titleLines.size * (lineH + 10) + 18 +
        bodyLines.size * lineH + pad).toInt().coerceAtLeast(240)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    var y = pad + 40f
    titleLines.forEach { canvas.drawText(it, pad, y, paintTitle); y += lineH + 10 }
    y += 18f
    bodyLines.forEach { canvas.drawText(it, pad, y, paintBody); y += lineH }
    return bmp
}

// ---- delivery (downloadBlob / share equivalents) -------------------------

fun saveImageToPictures(context: Context, name: String, bytes: ByteArray, mime: String): Boolean =
    saveToMediaStore(context, name, mime, bytes, images = true)

fun saveFileToDownloads(context: Context, name: String, mime: String, bytes: ByteArray): Boolean =
    saveToMediaStore(context, name, mime, bytes, images = false)

/** MediaStore on API 29+; legacy public-directory write below that. */
private fun saveToMediaStore(
    context: Context, name: String, mime: String, bytes: ByteArray, images: Boolean
): Boolean {
    return try {
    if (Build.VERSION.SDK_INT >= 29) {
        val collection = if (images)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            )
        }
        val uri: Uri = context.contentResolver.insert(collection, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        true
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(
            if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        )
        if (!dir.exists()) dir.mkdirs()
        FileOutputStream(File(dir, name)).use { it.write(bytes) }
        true
    }
    } catch (e: Exception) {
        false
    }
}

/** Writes bytes to the share cache and returns a FileProvider Uri. */
fun cacheSharedFile(context: Context, name: String, bytes: ByteArray): Uri? = try {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, name)
    FileOutputStream(f).use { it.write(bytes) }
    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
} catch (e: Exception) {
    null
}

/** ACTION_SEND share sheet; false when nothing can handle it. */
fun shareUri(context: Context, uri: Uri, mime: String, title: String, text: String?): Boolean =
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!text.isNullOrEmpty()) putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
        true
    } catch (e: Exception) {
        false
    }

// ---- the source's three operations ---------------------------------------

private fun bitmapJpegBytes(bmp: Bitmap): ByteArray {
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos) // source quality 0.92
    return bos.toByteArray()
}

private fun bitmapPdfBytes(bmp: Bitmap): ByteArray {
    val doc = PdfDocument()
    // A4 at 72dpi, image fit to page width (html2pdf's default target).
    val pageW = 595
    val pageH = 842
    val scale = pageW.toFloat() / bmp.width
    val scaledH = (bmp.height * scale).toInt().coerceAtMost(pageH)
    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
    val src = android.graphics.Rect(0, 0, bmp.width, (scaledH / scale).toInt())
    val dst = android.graphics.RectF(0f, 0f, pageW.toFloat(), scaledH.toFloat())
    page.canvas.drawBitmap(bmp, src, dst, null)
    doc.finishPage(page)
    val bos = ByteArrayOutputStream()
    doc.writeTo(bos)
    doc.close()
    return bos.toByteArray()
}

/** Source exportAppToJPG / exportToolToJPG / exportSplit|Tip|TallyToJPG. */
suspend fun exportToJpg(
    context: Context, fileBase: String, prettyName: String,
    header: String, lines: List<String>
): String = withContext(Dispatchers.IO) {
    try {
        val bmp = renderSummaryBitmap(header, lines)
        val name = stampedFilename("$fileBase.jpg")
        if (saveImageToPictures(context, name, bitmapJpegBytes(bmp), "image/jpeg"))
            "$prettyName exported to JPG!"
        else "Export failed."
    } catch (e: Exception) {
        "Export failed."
    }
}

/** Source exportAppToPDF / exportToolToPDF / exportSplit|Tip|TallyToPDF. */
suspend fun exportToPdf(
    context: Context, fileBase: String, prettyName: String,
    header: String, lines: List<String>
): String = withContext(Dispatchers.IO) {
    try {
        val bmp = renderSummaryBitmap(header, lines)
        val name = stampedFilename("$fileBase.pdf")
        if (saveFileToDownloads(context, name, "application/pdf", bitmapPdfBytes(bmp)))
            "$prettyName exported to PDF!"
        else "Export failed."
    } catch (e: Exception) {
        "Export failed."
    }
}

/**
 * Source shareAppToJPG(): share the JPG capture with title/text =
 * prettyName; if sharing is impossible, copy the __shareTextFor summary
 * to the clipboard ("Summary copied — paste it anywhere!").
 */
suspend fun shareAsJpg(
    context: Context, fileBase: String, prettyName: String,
    header: String, lines: List<String>, shareText: String
): String = withContext(Dispatchers.IO) {
    try {
        val bmp = renderSummaryBitmap(header, lines)
        val uri = cacheSharedFile(context, stampedFilename("$fileBase.jpg"), bitmapJpegBytes(bmp))
        val shared = uri != null && withContext(Dispatchers.Main) {
            shareUri(context, uri, "image/jpeg", prettyName, prettyName)
        }
        if (shared) {
            "$prettyName shared!"
        } else if (shareText.isNotEmpty()) {
            withContext(Dispatchers.Main) { copyToClipboard(context, shareText) }
            "Summary copied — paste it anywhere!"
        } else {
            "Sharing is not available on this device — use JPG export instead."
        }
    } catch (e: Exception) {
        "Share failed."
    }
}

/**
 * v1.0.1 (defect #4): the bottom Share/JPG/PDF text-pill trio (the old
 * ExportBar composable) is GONE — in the source those actions live in each
 * tool's main-card header block. The buttons now render in
 * [ToolCardHeader] (ui/ToolCardHeader.kt), wired to the same operations
 * above (shareAppToJPG / exportToolToJPG / exportToolToPDF semantics and
 * the Split/Tip/Tally dedicated file bases, which screens pass through).
 */
