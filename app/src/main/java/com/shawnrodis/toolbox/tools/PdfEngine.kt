package com.shawnrodis.toolbox.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * PDF manipulation engine for PDF Tools.
 *
 * Library choice (documented per the Phase 3 brief): PdfBox-Android
 * (tom-roush, Apache PDFBox port) does everything the source does with
 * pdf-lib — create documents, copy/import pages (merge, split, OCR text
 * layer), set page rotation, embed PNG/JPG images, draw invisible text —
 * while the framework PdfRenderer replaces pdf.js for rendering page
 * bitmaps (previews and PDF-to-images at the source's scale 2.0).
 * android.graphics.pdf.PdfDocument is only used for the JPG/PDF summary
 * exports, where no page surgery is needed.
 */
object PdfEngine {

    @Volatile
    private var initialised = false

    fun init(context: Context) {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialised = true
                }
            }
        }
    }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open the selected file.")

    // ---- rendering (pdf.js equivalent) ----------------------------------

    fun pageCount(context: Context, uri: Uri): Int =
        openRenderer(context, uri).use { it.pageCount }

    /**
     * Renders one page at the given viewport scale, exactly the source's
     * page.getViewport({scale}) semantics (points * scale = pixels), on a
     * white background like a PDF viewer.
     */
    fun renderPage(context: Context, uri: Uri, pageIndex: Int, scale: Double): Bitmap =
        openRenderer(context, uri).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }

    private fun openRenderer(context: Context, uri: Uri): PdfRenderer {
        val pfd: ParcelFileDescriptor = context.contentResolver
            .openFileDescriptor(uri, "r") ?: throw IllegalStateException("Could not open the selected file.")
        return PdfRenderer(pfd)
    }

    // ---- pdf-lib equivalents ---------------------------------------------

    /** processMerge(): every page of every queued file, in queue order. */
    fun merge(context: Context, uris: List<Uri>): ByteArray {
        init(context)
        val merger = PDFMergerUtility()
        val out = ByteArrayOutputStream()
        merger.destinationStream = out
        uris.forEach { uri ->
            merger.addSource(ByteArrayInputStream(readBytes(context, uri)))
        }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        return out.toByteArray()
    }

    /** processSplit(): the selected 0-based indices, in selection order. */
    fun split(srcBytes: ByteArray, indices: List<Int>): ByteArray {
        PDDocument.load(srcBytes).use { src ->
            val out = PDDocument()
            for (i in indices) {
                if (i in 0 until src.numberOfPages) out.importPage(src.getPage(i))
            }
            val bos = ByteArrayOutputStream()
            out.save(bos)
            out.close()
            return bos.toByteArray()
        }
    }

    /** processRotate(): each page's rotation += the chosen angle (mod 360). */
    fun rotate(srcBytes: ByteArray, angle: Int): ByteArray {
        PDDocument.load(srcBytes).use { pdf ->
            for (page in pdf.pages) {
                page.rotation = (page.rotation + angle) % 360
            }
            val bos = ByteArrayOutputStream()
            pdf.save(bos)
            return bos.toByteArray()
        }
    }

    /**
     * processImagesToPdf(): one page per image, page sized to the image's
     * pixel dimensions, image drawn at (0,0) at natural size. The source
     * branches embedPng vs embedJpg by MIME type; PDImageXObject
     * .createFromByteArray sniffs the stream the same way.
     */
    fun imagesToPdf(images: List<ByteArray>): ByteArray {
        val doc = PDDocument()
        for (bytes in images) {
            val img = PDImageXObject.createFromByteArray(doc, bytes, "image")
            val page = PDPage(PDRectangle(img.width.toFloat(), img.height.toFloat()))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(img, 0f, 0f, img.width.toFloat(), img.height.toFloat())
            }
        }
        val bos = ByteArrayOutputStream()
        doc.save(bos)
        doc.close()
        return bos.toByteArray()
    }

    // ---- searchable OCR ----------------------------------------------------

    /**
     * Draws one invisible text run on a page, mirroring the source's
     * page.drawText(t, {opacity: 0}) with per-call try/catch: glyphs the
     * font cannot encode are skipped silently, never failing the page.
     */
    private fun drawInvisibleText(page: PDPage, doc: PDDocument, x: Float, y: Float, size: Float, text: String) {
        try {
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, size)
                // Rendering mode 3 (neither fill nor stroke) = invisible
                // text in this fork; that is PDFBox's INVISIBLE mode.
                cs.setRenderingMode(RenderingMode.NEITHER)
                cs.newLineAtOffset(x, y)
                cs.showText(text)
                cs.endText()
            }
        } catch (e: Exception) {
            // Source swallows per-word draw errors the same way.
        }
    }

    /**
     * The OCR text-layer maths from processSearchableOCR, shared by both
     * engines. Local (word boxes): x0 = bbox.x0 * (W/vpW),
     * y = H - bbox.y1 * (H/vpH), size = max(5, bboxHeight * (H/vpH)).
     * AI (lines): x = 40, spacing = (H-80)/max(1, lines),
     * y = H-40 - idx*spacing, size = min(12, max(6, spacing*0.7)).
     */
    private fun addWordLayer(
        doc: PDDocument, page: PDPage, words: List<OcrWord>, vpW: Int, vpH: Int
    ) {
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        for (w in words) {
            val t = sanitizeOcrText(w.text).trim()
            if (t.isEmpty()) continue
            val x0 = w.x0 * (width / vpW)
            val y1 = height - (w.y1 * (height / vpH))
            val fontSize = maxOf(5f, (w.y1 - w.y0) * (height / vpH))
            if (x0.isFinite() && y1.isFinite() && fontSize.isFinite()) {
                drawInvisibleText(page, doc, x0, y1, fontSize, t)
            }
        }
    }

    private fun addLineLayer(doc: PDDocument, page: PDPage, text: String) {
        val height = page.mediaBox.height
        val lines = text.split('\n').filter { it.trim().isNotEmpty() }
        val spacing = (height - 80) / maxOf(1, lines.size)
        lines.forEachIndexed { idx, l ->
            val t = sanitizeOcrText(l).trim()
            if (t.isNotEmpty()) {
                val size = minOf(12f, maxOf(6f, (spacing * 0.7).toFloat()))
                drawInvisibleText(page, doc, 40f, height - 40 - idx * spacing, size, t)
            }
        }
    }

    /** OCR engine selector value from the source: "tesseract" | "ai". */
    suspend fun searchablePdfFromPdf(
        context: Context, uri: Uri, engine: String, geminiKeys: List<String>
    ): ByteArray {
        init(context)
        val srcBytes = readBytes(context, uri)
        val count = pageCount(context, uri)
        val out = PDDocument()
        PDDocument.load(srcBytes).use { src ->
            for (i in 0 until count) {
                val page = out.importPage(src.getPage(i))
                // Source renders at viewport scale 2.0 before OCR.
                val bmp = renderPage(context, uri, i, 2.0)
                if (engine == "ai") {
                    val text = callAiVisionOcrWithFailover(bitmapToBase64Png(bmp), geminiKeys)
                    if (text.isNotEmpty()) addLineLayer(out, page, text)
                } else {
                    val words = recognizeWordsLocal(bmp)
                    addWordLayer(out, page, words, bmp.width, bmp.height)
                }
            }
        }
        val bos = ByteArrayOutputStream()
        out.save(bos)
        out.close()
        return bos.toByteArray()
    }

    /**
     * OCR of a single image input. NOTE (source quirk): in the HTML,
     * processSearchableOCR only handles PDF inputs — an image input falls
     * through and downloads an EMPTY "searchable_document.pdf". The native
     * port completes the intended flow instead: the image becomes page 1
     * (sized to the image, as in Images to PDF) with the same invisible
     * text layer the PDF path would add.
     */
    suspend fun searchablePdfFromImage(
        context: Context, imageBytes: ByteArray, bitmap: Bitmap,
        engine: String, geminiKeys: List<String>
    ): ByteArray {
        init(context)
        val doc = PDDocument()
        val img = PDImageXObject.createFromByteArray(doc, imageBytes, "image")
        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(img, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        }
        if (engine == "ai") {
            val text = callAiVisionOcrWithFailover(bitmapToBase64Png(bitmap), geminiKeys)
            if (text.isNotEmpty()) addLineLayer(doc, page, text)
        } else {
            val words = recognizeWordsLocal(bitmap)
            addWordLayer(doc, page, words, bitmap.width, bitmap.height)
        }
        val bos = ByteArrayOutputStream()
        doc.save(bos)
        doc.close()
        return bos.toByteArray()
    }
}
