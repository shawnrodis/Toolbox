package com.shawnrodis.toolbox.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.cacheSharedFile
import com.shawnrodis.toolbox.ui.scrollContentBottomPadding
import com.shawnrodis.toolbox.ui.saveFileToDownloads
import com.shawnrodis.toolbox.ui.saveImageToPictures
import com.shawnrodis.toolbox.ui.shareUri
import com.shawnrodis.toolbox.ui.toast
import com.shawnrodis.toolbox.ui.ToolCardHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * PDF Tools — faithful port of the source's tab-pdftools + pdf-star and
 * process functions. Layout follows the markup order: header (title, subtitle,
 * preview-scale slider 0.5-3.0 persisted as pdf_suite_preview_scale),
 * privacy badge, tool dropdown (pdf-tool-dropdown), current tool
 * title/description, drop zone ("Drag & Drop files here" — drag & drop is
 * a desktop gesture; on Android the Browse Files button and the zone both
 * open the system picker), File Queue with Clear All, Tool Controls,
 * Run Task (pdfProcessTask), status badge, and the Live Interactive
 * Preview pane (updateLivePreview + the per-tool render*LivePreview
 * functions, with PdfRenderer standing in for pdf.js).
 *
 * Output delivery mirrors downloadBlob(): finished files are saved to
 * Downloads and offered through the share sheet.
 */

data class PdfToolDef(val id: String, val name: String, val desc: String)

/** The source's `tools` array, verbatim (ids, names, descriptions). */
val PDF_TOOLS: List<PdfToolDef> = listOf(
    PdfToolDef("merge", "Merge PDF Files", "Combine multiple PDF files into a single unified document."),
    PdfToolDef("split", "Split / Extract", "Click page thumbnails or enter ranges to extract pages."),
    PdfToolDef("rotate", "Rotate PDF", "Rotate PDF pages with real-time visual rotation."),
    PdfToolDef("toimage", "PDF to Images", "Convert PDF pages into high-resolution PNG or JPG."),
    PdfToolDef("imgtopdf", "Images to PDF", "Combine photos into a downloadable PDF."),
    PdfToolDef("ocr", "AI Vision OCR", "Convert scanned PDFs into searchable PDFs using Local or AI Vision.")
)

data class QueuedFile(val uri: Uri, val name: String, val size: Long, val mime: String) {
    fun isPdf(): Boolean = mime == "application/pdf" || name.lowercase(Locale.US).endsWith(".pdf")
    fun isImage(): Boolean = mime.startsWith("image/")
}

private fun queryFile(context: Context, uri: Uri): QueuedFile {
    var name = "file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0) size = c.getLong(si)
            }
        }
    } catch (e: Exception) { /* defaults */ }
    val mime = context.contentResolver.getType(uri)
        ?: when {
            name.lowercase(Locale.US).endsWith(".pdf") -> "application/pdf"
            name.lowercase(Locale.US).endsWith(".png") -> "image/png"
            name.lowercase(Locale.US).endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    return QueuedFile(uri, name, size, mime)
}

/** Renders a PDF page off the main thread, recomputed when inputs change. */
@Composable
private fun rememberPdfPageBitmap(context: Context, file: QueuedFile?, page: Int, scale: Double): Bitmap? {
    val bmp by produceState<Bitmap?>(initialValue = null, file?.uri, page, scale) {
        value = if (file == null || !file.isPdf()) null else try {
            withContext(Dispatchers.IO) { PdfEngine.renderPage(context, file.uri, page, scale) }
        } catch (e: Exception) {
            null
        }
    }
    return bmp
}

/** Decodes an image file off the main thread (preview use). */
@Composable
private fun rememberImageBitmap(context: Context, file: QueuedFile?): Bitmap? {
    val bmp by produceState<Bitmap?>(initialValue = null, file?.uri) {
        value = if (file == null || !file.isImage()) null else try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }
    }
    return bmp
}

@Composable
fun PdfToolsScreen(store: AppStore, onBack: () -> Unit) {
    // v1.0.1 (defect #4c): no Material TopAppBar — global header + Home FAB.
    PdfToolsContent(store, Modifier.fillMaxSize())
}

@Composable
fun PdfToolsContent(store: AppStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AppSettings())
    val scalePref by store.stringState("pdf_suite_preview_scale", "").collectAsState(initial = "")

    // Source state block: activeTool 'merge', uploadedFiles [], ocrEngine
    // 'tesseract', currentRotationAngle 90, selectedSplitPages empty set.
    var activeTool by remember { mutableStateOf("merge") }
    var queue by remember { mutableStateOf<List<QueuedFile>>(emptyList()) }
    var ocrEngine by remember { mutableStateOf("tesseract") }
    var rotationAngle by remember { mutableStateOf(90) }
    var splitSelection by remember { mutableStateOf<LinkedHashSet<Int>>(linkedSetOf()) }
    var splitRangeText by remember { mutableStateOf("") }
    var imageFormat by remember { mutableStateOf("image/png") }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var resultName by remember { mutableStateOf<String?>(null) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var previewScale by remember { mutableFloatStateOf(1.0f) }
    var scaleLoaded by remember { mutableStateOf(false) }
    val pageCounts = remember { mutableStateMapOf<String, Int>() }

    // loadSettings(): restore pdf_suite_preview_scale (default 1.0).
    LaunchedEffect(scalePref) {
        if (!scaleLoaded) {
            scalePref.toFloatOrNull()?.let { previewScale = it }
            scaleLoaded = true
        }
    }
    // Page counts for queued PDFs (needed by split + merge previews).
    LaunchedEffect(queue) {
        for (f in queue) {
            if (f.isPdf() && !pageCounts.containsKey(f.uri.toString())) {
                val n = try {
                    withContext(Dispatchers.IO) { PdfEngine.pageCount(context, f.uri) }
                } catch (e: Exception) {
                    0
                }
                pageCounts[f.uri.toString()] = n
            }
        }
    }

    val tool = PDF_TOOLS.find { it.id == activeTool } ?: PDF_TOOLS.first()
    val first = queue.firstOrNull()
    val splitTotal = first?.let { pageCounts[it.uri.toString()] } ?: 0

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // addFiles(): append, re-render queue + preview.
            queue = queue + uris.map { queryFile(context, it) }
        }
    }
    fun browse() {
        val types = when (activeTool) {
            "imgtopdf" -> arrayOf("image/*")
            "ocr" -> arrayOf("application/pdf", "image/*")
            else -> arrayOf("application/pdf")
        }
        picker.launch(types)
    }

    /** downloadBlob() equivalent: save to Downloads + arm the Share button. */
    suspend fun deliver(bytes: ByteArray, filename: String): Boolean {
        val saved = withContext(Dispatchers.IO) {
            saveFileToDownloads(context, filename, "application/pdf", bytes)
        }
        resultUri = withContext(Dispatchers.IO) { cacheSharedFile(context, filename, bytes) }
        resultName = filename
        return saved
    }

    /** pdfProcessTask(): dispatch on the active tool, badge follows. */
    fun runTask() {
        if (queue.isEmpty()) {
            toast(context, "Upload a file first.")
            return
        }
        running = true
        status = "Processing..."
        resultName = null
        resultUri = null
        scope.launch {
            try {
                when (activeTool) {
                    "merge" -> {
                        val bytes = withContext(Dispatchers.IO) {
                            PdfEngine.merge(context, queue.map { it.uri })
                        }
                        deliver(bytes, "merged_document.pdf")
                    }
                    "split" -> {
                        val f = first!!
                        val bytes = withContext(Dispatchers.IO) {
                            val src = PdfEngine.readBytes(context, f.uri)
                            PdfEngine.split(src, PageRanges.splitIndices(splitSelection, splitTotal))
                        }
                        deliver(bytes, "split_extracted.pdf")
                    }
                    "rotate" -> {
                        val f = first!!
                        val bytes = withContext(Dispatchers.IO) {
                            PdfEngine.rotate(PdfEngine.readBytes(context, f.uri), rotationAngle)
                        }
                        deliver(bytes, "rotated_document.pdf")
                    }
                    "toimage" -> {
                        val f = first!!
                        val total = pageCounts[f.uri.toString()] ?: 0
                        val ext = imageFormat.substringAfter('/')
                        var savedCount = 0
                        withContext(Dispatchers.IO) {
                            for (i in 0 until total) {
                                val bmp = PdfEngine.renderPage(context, f.uri, i, 2.0)
                                val bos = ByteArrayOutputStream()
                                if (imageFormat == "image/png")
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                else
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                                if (saveImageToPictures(context, "page_${i + 1}.$ext", bos.toByteArray(), imageFormat))
                                    savedCount++
                            }
                        }
                        resultName = "$savedCount page image(s) saved to Pictures"
                    }
                    "imgtopdf" -> {
                        val imageFiles = queue.filter { it.isImage() }
                        val bytes = withContext(Dispatchers.IO) {
                            PdfEngine.imagesToPdf(imageFiles.map { PdfEngine.readBytes(context, it.uri) })
                        }
                        deliver(bytes, "converted_images.pdf")
                    }
                    "ocr" -> {
                        val f = first!!
                        val keys = listOf(settings.geminiKey1, settings.geminiKey2)
                        val bytes = withContext(Dispatchers.IO) {
                            if (f.isPdf()) {
                                PdfEngine.searchablePdfFromPdf(context, f.uri, ocrEngine, keys)
                            } else {
                                val raw = PdfEngine.readBytes(context, f.uri)
                                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                                    ?: throw Exception("Could not decode the selected image.")
                                PdfEngine.searchablePdfFromImage(context, raw, bmp, ocrEngine, keys)
                            }
                        }
                        deliver(bytes, "searchable_document.pdf")
                    }
                }
                status = "Completed"
            } catch (e: Exception) {
                status = "Failed"
                toast(context, "Error: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    Column(
        modifier = modifier
            // v1.0.2 (defect D): bottom = FAB clearance in the mobile
            // shell (scrollContentBottomPadding), 8dp in windows.
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = scrollContentBottomPadding())
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // v1.0.1 (defect #4): the source's card header block (fa-file-pdf,
        // "PDF Tools", its 11px description; PDF Tools has no export trio
        // in the source). Below it, the suite's own subtitle row + the
        // preview-scale control.
        ToolCardHeader(
            faIcon = "fa-file-pdf",
            title = "PDF Tools",
            description = "Upload files to begin real-time interactive processing directly in your browser."
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Interactive Real-Time Preview Engine & Universal AI OCR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        "Privacy Mode (Files stay on device)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(170.dp)) {
                Text(
                    "Preview scale: ${String.format(Locale.US, "%.1fx", previewScale)}",
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = previewScale,
                    onValueChange = { previewScale = it },
                    onValueChangeFinished = {
                        // pdfSaveScaleValue(): persist + re-render preview.
                        scope.launch {
                            store.setStringState(
                                "pdf_suite_preview_scale",
                                String.format(Locale.US, "%.1f", previewScale)
                            )
                        }
                    },
                    valueRange = 0.5f..3.0f,
                    steps = 24
                )
            }
        }

        // Tool dropdown (pdf-tool-dropdown) + current tool title/desc.
        PickerField(
            label = "PDF Tools",
            options = PDF_TOOLS.map { it.name },
            selected = tool.name,
            onSelect = { name ->
                PDF_TOOLS.find { it.name == name }?.let { activeTool = it.id }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Column {
            Text(tool.name, style = MaterialTheme.typography.titleLarge)
            Text(tool.desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Drop zone (pdf-drop-zone): tap = Browse Files.
        Card(modifier = Modifier.fillMaxWidth().clickable { browse() }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text("Drag & Drop files here", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (activeTool) {
                        "imgtopdf" -> "Supports PNG, JPG, JPEG, WEBP images"
                        "ocr" -> "Supports PDF documents and image files"
                        else -> "Supports PDF documents"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                Button(onClick = { browse() }) { Text("Browse Files") }
            }
        }

        // File Queue (renderFileQueue)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "File Queue (${queue.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        // pdfClearFiles(): queue + split selection cleared.
                        queue = emptyList()
                        splitSelection = linkedSetOf()
                        splitRangeText = ""
                    }) { Text("Clear All") }
                }
                if (queue.isEmpty()) {
                    Text(
                        "No files uploaded yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                queue.forEachIndexed { idx, f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "(${String.format(Locale.US, "%.2f", f.size / 1024.0 / 1024.0)} MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeTool == "merge" || activeTool == "imgtopdf") {
                            TextButton(onClick = {
                                // pdfMoveFile(idx, -1)
                                if (idx > 0) queue = queue.toMutableList().also {
                                    val t = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = t
                                }
                            }) { Text("Up") }
                            TextButton(onClick = {
                                // pdfMoveFile(idx, +1)
                                if (idx < queue.size - 1) queue = queue.toMutableList().also {
                                    val t = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = t
                                }
                            }) { Text("Down") }
                        }
                        TextButton(onClick = {
                            // pdfRemoveFile(idx)
                            queue = queue.filterIndexed { i, _ -> i != idx }
                        }) { Text("Remove") }
                    }
                }
            }
        }

        // Tool Controls (renderToolOptions)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Tool Controls", style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    when (activeTool) {
                        "merge" -> Text(
                            "Upload PDF files. They will display below in merged sequential order in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        "split" -> Column {
                            OutlinedTextField(
                                value = splitRangeText,
                                onValueChange = { v ->
                                    splitRangeText = v
                                    // pdfOnSplitRangeInput(v)
                                    splitSelection = PageRanges.parseRangeInput(v, splitTotal)
                                },
                                label = { Text("Page Selection Range:") },
                                placeholder = { Text("e.g. 1-3, 5") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Tip: Click thumbnails directly in the preview pane to toggle pages!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        "rotate" -> Column {
                            Text("Live Rotate Control:", style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                listOf(90 to "90° Clockwise", 180 to "180° Flip", 270 to "270° CCW").forEach { (angle, label) ->
                                    FilterChip(
                                        selected = rotationAngle == angle,
                                        onClick = { rotationAngle = angle },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                        "toimage" -> PickerField(
                            label = "Target Image Format:",
                            options = listOf("PNG (High-Res Lossless)", "JPG (Standard Compressed)"),
                            selected = if (imageFormat == "image/png") "PNG (High-Res Lossless)" else "JPG (Standard Compressed)",
                            onSelect = { sel ->
                                imageFormat = if (sel.startsWith("PNG")) "image/png" else "image/jpeg"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        "imgtopdf" -> Text(
                            "Combine images into a PDF. Reorder files in the queue to change page sequence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        "ocr" -> Column {
                            Text("OCR Engine Selector:", style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = ocrEngine == "tesseract",
                                    onClick = { ocrEngine = "tesseract" },
                                    label = { Text("Local Engine · Tesseract.js (Offline)") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = ocrEngine == "ai",
                                    onClick = { ocrEngine = "ai" },
                                    label = { Text("AI Vision OCR · Cloud AI API Key") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (ocrEngine == "ai" && settings.geminiKey1.isBlank() && settings.geminiKey2.isBlank()) {
                                Text(
                                    "Add your Gemini API key in Settings to use AI Vision OCR — the native app has no built-in key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { runTask() },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                ) { Text(if (running) "Processing..." else "Run Task") }
                status?.let { st ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            st,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                resultName?.let { rn ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            if (activeTool == "ocr" && rn == "searchable_document.pdf")
                                "✓ Searchable PDF Ready — $rn saved to Downloads"
                            else "✓ $rn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (resultUri != null) {
                            OutlinedButton(onClick = {
                                shareUri(context, resultUri!!, "application/pdf", rn, null)
                            }) { Text("Share") }
                        }
                    }
                }
            }
        }

        // Live Interactive Preview (updateLivePreview + render*LivePreview)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Live Interactive Preview", style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                    if (queue.isEmpty()) {
                        Text(
                            "Upload a file to view live document structure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        when (activeTool) {
                            "merge" -> MergePreview(context, queue, pageCounts, previewScale)
                            "split" -> SplitPreview(
                                context, first, splitTotal, previewScale,
                                splitSelection
                            ) { page ->
                                // togglePageSelection(): set toggles; the range
                                // field is rewritten with the sorted selection.
                                val sel = LinkedHashSet(splitSelection)
                                PageRanges.toggle(sel, page)
                                splitSelection = sel
                                splitRangeText = PageRanges.rangeFieldText(sel)
                            }
                            "rotate" -> RotatePreview(context, first, previewScale, rotationAngle)
                            "imgtopdf" -> ImagesToPdfPreview(context, queue)
                            else -> GenericPreview(context, first, previewScale)
                        }
                    }
                }
            }
        }
    }
}

/** renderMergeLivePreview(): one row per PDF — first-page thumb, name, page count. */
@Composable
private fun MergePreview(
    context: Context, queue: List<QueuedFile>,
    pageCounts: Map<String, Int>, previewScale: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        queue.forEachIndexed { idx, f ->
            if (!f.isPdf()) return@forEachIndexed
            val thumb = rememberPdfPageBitmap(context, f, 0, 0.2 * previewScale)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    thumb?.let {
                        Image(
                            it.asImageBitmap(), contentDescription = f.name,
                            modifier = Modifier.heightIn(max = 120.dp)
                        )
                    }
                    Column {
                        Text("${idx + 1}. ${f.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${pageCounts[f.uri.toString()] ?: 0} Page(s) Document",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** renderSplitLivePreview(): grid of page thumbs; tap toggles selection. */
@Composable
private fun SplitPreview(
    context: Context, file: QueuedFile?, totalPages: Int,
    previewScale: Float, selection: Set<Int>, onToggle: (Int) -> Unit
) {
    if (file == null) return
    if (!file.isPdf()) {
        Text(
            "Split tool requires a PDF document.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        return
    }
    val cols = when {
        previewScale > 2.0f -> 2
        previewScale > 1.4f -> 3
        else -> 4
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..totalPages).chunked(cols).forEach { rowPages ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPages.forEach { page ->
                    val thumb = rememberPdfPageBitmap(context, file, page - 1, 0.25 * previewScale)
                    val selected = selection.contains(page)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                2.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggle(page) }
                            .padding(6.dp)
                    ) {
                        thumb?.let {
                            Image(it.asImageBitmap(), contentDescription = "Page $page")
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "Page $page",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                repeat(cols - rowPages.size) {
                    Column(modifier = Modifier.weight(1f)) { }
                }
            }
        }
    }
}

/** renderRotateLivePreview(): first page, CSS-rotated by the chosen angle. */
@Composable
private fun RotatePreview(context: Context, file: QueuedFile?, previewScale: Float, angle: Int) {
    if (file == null) return
    if (file.isImage()) {
        val bmp = rememberImageBitmap(context, file)
        bmp?.let {
            Image(
                it.asImageBitmap(), contentDescription = file.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (220 * previewScale).dp)
                    .graphicsLayer { rotationZ = angle.toFloat() }
            )
        }
        return
    }
    val bmp = rememberPdfPageBitmap(context, file, 0, 0.5 * previewScale)
    bmp?.let {
        Image(
            it.asImageBitmap(), contentDescription = file.name,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { rotationZ = angle.toFloat() }
        )
    }
}

/** renderImagesToPdfPreview(): image thumbs captioned "Page i". */
@Composable
private fun ImagesToPdfPreview(context: Context, queue: List<QueuedFile>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        queue.chunked(3).forEach { rowFiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowFiles.forEach { f ->
                    val idx = queue.indexOf(f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (f.isImage()) {
                            val bmp = rememberImageBitmap(context, f)
                            bmp?.let {
                                Image(
                                    it.asImageBitmap(), contentDescription = f.name,
                                    modifier = Modifier.heightIn(max = 120.dp)
                                )
                            }
                        }
                        Text(
                            "Page ${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                repeat(3 - rowFiles.size) { Column(modifier = Modifier.weight(1f)) { } }
            }
        }
    }
}

/** renderGenericPreview(): image directly, or page 1 of the PDF at 0.5 x scale. */
@Composable
private fun GenericPreview(context: Context, file: QueuedFile?, previewScale: Float) {
    if (file == null) return
    if (file.isImage()) {
        val bmp = rememberImageBitmap(context, file)
        bmp?.let {
            Image(
                it.asImageBitmap(), contentDescription = file.name,
                modifier = Modifier.fillMaxWidth().heightIn(max = (220 * previewScale).dp)
            )
        }
        return
    }
    if (file.isPdf()) {
        val bmp = rememberPdfPageBitmap(context, file, 0, 0.5 * previewScale)
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = file.name, modifier = Modifier.fillMaxWidth())
        }
    }
}
