package com.shawnrodis.toolbox.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.saveFileToDownloads
import com.shawnrodis.toolbox.ui.toast
import com.shawnrodis.toolbox.ui.ToolCardHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Square Pic — faithful port of the source's tab-squarepic + sq* functions.
 * State is memory-only in the source (nothing persisted), and so here.
 * Defaults from the source state block: mode 'blur', blur 18, solid
 * '#0f172a', gradient '#1e3a8a' -> '#7c3aed' vertical, fit = true, zoom 1,
 * pan (0,0) in 720-space, border off / width 12 / colour '#ffffff'.
 * Preview canvas is SQ_PREVIEW=720 units; export renders at SQ_EXPORT=2048.
 *
 * Control order follows the markup exactly: New photo (header), drop zone /
 * editor, Background fill (Blur/Solid/Gradient segmented), the mode's option
 * row (blur slider 0-40 · solid colour + 6 swatches · gradient colours +
 * angle), Photo (Fit whole photo / Fill & crop), Zoom (1-3, step 0.05),
 * Border (toggle + width 0-40 + colour), Export 2048x2048 PNG + clear,
 * and the "Lossless PNG · saved as <file>" line.
 *
 * Documented platform mappings:
 *  - <input type="color"> has no Compose equivalent; colours are edited via
 *    the source's own swatch presets plus a hex field (same values).
 *  - Canvas ctx.filter blur is approximated by a cover-fit downscale/
 *    upscale pass (strength mapped from the same slider value).
 *  - The web download becomes: save straight to the Downloads folder
 *    (MediaStore.Downloads; legacy public Downloads dir on API <= 28) with
 *    an "Exported <name>" toast — no share sheet (v1.0.6, at the owner's
 *    request; previously it saved to Pictures and opened the share sheet).
 */

private val SQ_SWATCHES = listOf(
    "#000000", "#ffffff", "#0f172a", "#7c2d12", "#14532d", "#831843"
)

private fun parseHexColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

private fun colorToHex(c: Color): String = String.format(
    Locale.US, "#%02X%02X%02X",
    (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt()
)

/** Loads a bitmap honouring EXIF orientation (source: createImageBitmap 'from-image'). */
private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return null
    val orientation = try {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}

private fun displayName(context: Context, uri: Uri): String {
    var name = "image"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { name = it }
        }
    } catch (e: Exception) { /* fall through with default */ }
    return name
}

/**
 * Blur approximation for the cover background: the cover-fit photo is
 * rendered small, then upscaled with filtering — the small size is driven
 * by the blur radius exactly as the source's px slider maps to strength.
 */
fun makeBlurredCover(src: Bitmap, radiusPx: Double, targetS: Int): Bitmap {
    val small = (targetS / (1.0 + radiusPx * 0.9)).toInt().coerceIn(8, targetS)
    val work = Bitmap.createBitmap(small, small, Bitmap.Config.ARGB_8888)
    val c = Canvas(work)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val r = sqBackgroundCoverRect(src.width, src.height, small)
    c.drawBitmap(
        src, null,
        RectF(r.left.toFloat(), r.top.toFloat(), (r.left + r.width).toFloat(), (r.top + r.height).toFloat()),
        paint
    )
    return Bitmap.createScaledBitmap(work, targetS, targetS, true)
}

/** Full sqDraw() at an arbitrary target size, on an Android Canvas. */
fun renderSquareBitmap(
    src: Bitmap, s: Int, mode: String, blur: Double, solid: Color,
    gradC1: Color, gradC2: Color, gradAngle: String,
    fit: Boolean, zoom: Double, ox: Double, oy: Double,
    borderOn: Boolean, borderW: Double, borderColor: Color
): Bitmap {
    val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val iw = src.width
    val ih = src.height
    // 1) background fill (sqDraw step 1)
    when (mode) {
        "blur" -> {
            val radius = blur * (s.toDouble() / SQ_PREVIEW)
            if (radius > 0.0) {
                canvas.drawBitmap(makeBlurredCover(src, radius, s), 0f, 0f, paint)
            } else {
                val r = sqBackgroundCoverRect(iw, ih, s)
                canvas.drawBitmap(src, null, RectF(r.left.toFloat(), r.top.toFloat(), (r.left + r.width).toFloat(), (r.top + r.height).toFloat()), paint)
            }
        }
        "solid" -> canvas.drawColor(
            android.graphics.Color.rgb(
                (solid.red * 255).roundToInt(), (solid.green * 255).roundToInt(), (solid.blue * 255).roundToInt()
            )
        )
        else -> {
            val c1 = android.graphics.Color.rgb((gradC1.red * 255).roundToInt(), (gradC1.green * 255).roundToInt(), (gradC1.blue * 255).roundToInt())
            val c2 = android.graphics.Color.rgb((gradC2.red * 255).roundToInt(), (gradC2.green * 255).roundToInt(), (gradC2.blue * 255).roundToInt())
            val shader = when (gradAngle) {
                "horizontal" -> LinearGradient(0f, 0f, s.toFloat(), 0f, c1, c2, Shader.TileMode.CLAMP)
                "diagonal" -> LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), c1, c2, Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, 0f, 0f, s.toFloat(), c1, c2, Shader.TileMode.CLAMP)
            }
            val p = Paint().apply { this.shader = shader }
            canvas.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        }
    }
    // 2) foreground photo (sqDraw step 2, pan already clamped by caller)
    val fr = sqForegroundRect(iw, ih, s, fit, zoom, ox, oy)
    canvas.drawBitmap(
        src, null,
        RectF(fr.left.toFloat(), fr.top.toFloat(), (fr.left + fr.width).toFloat(), (fr.top + fr.height).toFloat()),
        paint
    )
    // 3) inner border (sqDraw step 3)
    if (borderOn && borderW > 0) {
        val bw = sqBorderWidthPx(borderW, s).toFloat()
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = bw
            color = android.graphics.Color.rgb(
                (borderColor.red * 255).roundToInt(), (borderColor.green * 255).roundToInt(), (borderColor.blue * 255).roundToInt()
            )
        }
        canvas.drawRect(bw / 2, bw / 2, s - bw, s - bw, bp)
    }
    return out
}

@Composable
fun SquarePicScreen(store: AppStore, onBack: () -> Unit) {
    // v1.0.1 (defect #4c): no Material TopAppBar — global header + Home FAB.
    // v1.0.2 (defect D): padding AFTER the scroll modifier = scrollable
    // content padding, so the last controls can scroll clear of the FAB.
    SquarePicContent(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)
    )
}

@Composable
fun SquarePicContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName by remember { mutableStateOf("image") }
    var mode by remember { mutableStateOf("blur") }
    var blur by remember { mutableFloatStateOf(18f) }
    var solidHex by remember { mutableStateOf("#0f172a") }
    var gradC1Hex by remember { mutableStateOf("#1e3a8a") }
    var gradC2Hex by remember { mutableStateOf("#7c3aed") }
    var gradAngle by remember { mutableStateOf("vertical") }
    var fit by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableDoubleStateOf(0.0) }
    var oy by remember { mutableDoubleStateOf(0.0) }
    var borderOn by remember { mutableStateOf(false) }
    var borderW by remember { mutableFloatStateOf(12f) }
    var borderColorHex by remember { mutableStateOf("#ffffff") }
    var exporting by remember { mutableStateOf(false) }

    val solid = parseHexColor(solidHex, Color(0xFF0F172A))
    val gradC1 = parseHexColor(gradC1Hex, Color(0xFF1E3A8A))
    val gradC2 = parseHexColor(gradC2Hex, Color(0xFF7C3AED))
    val borderColor = parseHexColor(borderColorHex, Color.White)

    val img = bitmap
    // Pan is clamped whenever it (or fit/zoom/image) changes — sqClampPan.
    fun clampedPan(): Pair<Double, Double> =
        if (img == null) ox to oy
        else sqClampPan(img.width, img.height, SQ_PREVIEW, fit, zoom.toDouble(), ox, oy)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { loadOrientedBitmap(context, uri) }
                if (loaded != null) {
                    bitmap = loaded
                    fileName = displayName(context, uri)
                    // sqLoadFile resets zoom + pan on every new photo.
                    zoom = 1f; ox = 0.0; oy = 0.0
                }
            }
        }
    }
    fun pickPhoto() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val fitState = rememberUpdatedState(fit)
    val zoomState = rememberUpdatedState(zoom)

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // v1.0.1 (defect #4): the source's card header block (fa-images,
        // "Square Pic" + description; no export trio in the source — the
        // tool's own Export PNG lives in its controls). "New photo" stays
        // as the header-row action when a photo is loaded.
        ToolCardHeader(
            faIcon = "fa-images",
            title = "Square Pic",
            description = "Square any photo — blur, color, or gradient fill"
        )
        if (img != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                // sq-change-btn "New photo"
                OutlinedButton(onClick = { pickPhoto() }) { Text("New photo") }
            }
        }

        if (img == null) {
            // sq-dropzone (drag & drop is desktop-only; tap = browse, as labelled)
            OutlinedButton(
                onClick = { pickPhoto() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                    Text("Drag & drop a photo here", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "or tap to browse your files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val (cox, coy) = clampedPan()
            val blurredBg = remember(img, blur) {
                if (blur > 0f) makeBlurredCover(img, blur.toDouble(), SQ_PREVIEW) else null
            }
            // v1.0.6 (defect: the preview was not the square): the Canvas used
            // to sit bare in the column. Its layout box WAS square
            // (fillMaxWidth + aspectRatio(1f)), but a Compose Canvas does NOT
            // clip its drawing to its bounds — while the source's sqDraw()
            // geometry deliberately draws the cover/fill rects past the edges
            // and the HTML <canvas> element clips them itself. The blurred
            // backdrop and the zoomed photo therefore painted outside the
            // square, over the hint texts and controls around the preview,
            // and the blur branch additionally drew the pre-composed square
            // blurred bitmap through the source-photo cover rect (a double
            // cover), so the preview composition differed from the export.
            // Now: a centred outer box, the source's 520 max width, ONE
            // square Canvas clipped to the source's rounded corners, an
            // explicit clipRect in the draw scope, and the blurred
            // background drawn 1:1 into the square exactly as the export
            // renderer (renderSquareBitmap) draws it.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
            Canvas(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(img) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val k = SQ_PREVIEW.toDouble() / size.width
                            ox += drag.x * k
                            oy += drag.y * k
                            val c = sqClampPan(
                                img.width, img.height, SQ_PREVIEW,
                                fitState.value, zoomState.value.toDouble(), ox, oy
                            )
                            ox = c.first; oy = c.second
                        }
                    }
            ) {
                // Confine ALL drawing to the square: the source geometry
                // intentionally overflows the canvas rect (cover/fill); the
                // HTML canvas clipped it, this Canvas must clip it itself.
                drawContext.canvas.save()
                drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
                val s = size.width
                val si = s.toInt().coerceAtLeast(1)
                val iw = img.width
                val ih = img.height
                // background
                when (mode) {
                    "blur" -> {
                        if (blurredBg != null) {
                            // makeBlurredCover() already cover-composes the
                            // photo into a square bitmap: draw it 1:1 into
                            // the preview square, exactly as the export does.
                            val bg = blurredBg.asImageBitmap()
                            drawImage(
                                bg,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bg.width, bg.height),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(si, si)
                            )
                        } else {
                            // blur = 0: the plain cover-fit photo, as in sqDraw().
                            val r = sqBackgroundCoverRect(iw, ih, si)
                            drawImage(
                                img.asImageBitmap(),
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(iw, ih),
                                dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                                dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt())
                            )
                        }
                    }
                    "solid" -> drawRect(solid, size = Size(s, s))
                    else -> {
                        val end = when (gradAngle) {
                            "horizontal" -> Offset(s, 0f)
                            "diagonal" -> Offset(s, s)
                            else -> Offset(0f, s)
                        }
                        drawRect(
                            Brush.linearGradient(listOf(gradC1, gradC2), Offset.Zero, end),
                            size = Size(s, s)
                        )
                    }
                }
                // foreground
                val fr = sqForegroundRect(iw, ih, si, fit, zoom.toDouble(), cox, coy)
                val fg = img.asImageBitmap()
                drawImage(
                    fg,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(fg.width, fg.height),
                    dstOffset = IntOffset(fr.left.roundToInt(), fr.top.roundToInt()),
                    dstSize = IntSize(fr.width.roundToInt(), fr.height.roundToInt())
                )
                // border
                if (borderOn && borderW > 0) {
                    val bw = sqBorderWidthPx(borderW.toDouble(), si).toFloat()
                    drawRect(
                        borderColor,
                        topLeft = Offset(bw / 2, bw / 2),
                        size = Size(s - bw, s - bw),
                        style = Stroke(width = bw)
                    )
                }
                drawContext.canvas.restore()
            }
            }
            Text(
                "Drag the photo to reposition · use the zoom slider below for finer control",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Loaded: $fileName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Background fill (sqPaintSeg mode buttons)
            Text("Background fill", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("blur" to "Blur", "solid" to "Solid", "gradient" to "Gradient").forEach { (m, label) ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            when (mode) {
                "blur" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blur", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = blur, onValueChange = { blur = it },
                        valueRange = 0f..40f, steps = 39,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${blur.roundToInt()}px", style = MaterialTheme.typography.bodySmall)
                }
                "solid" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HexColorField("Solid colour", solidHex) { solidHex = it }
                    // sqSetSwatch presets, in the source's order.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SQ_SWATCHES.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseHexColor(hex, Color.Black))
                                    .border(
                                        2.dp,
                                        if (solidHex.equals(hex, ignoreCase = true))
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { solidHex = hex }
                            )
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HexColorField("Colour 1", gradC1Hex, Modifier.weight(1f)) { gradC1Hex = it }
                        HexColorField("Colour 2", gradC2Hex, Modifier.weight(1f)) { gradC2Hex = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "vertical" to "Vertical ↓",
                            "horizontal" to "Horizontal →",
                            "diagonal" to "Diagonal ↘"
                        ).forEach { (a, label) ->
                            FilterChip(
                                selected = gradAngle == a,
                                onClick = { gradAngle = a },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Photo fit (sqSetFit)
            Text("Photo", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = fit, onClick = { fit = true },
                    label = { Text("Fit whole photo") }, modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !fit, onClick = { fit = false },
                    label = { Text("Fill & crop") }, modifier = Modifier.weight(1f)
                )
            }

            // Zoom (sqSetZoom)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = zoom, onValueChange = { zoom = sqClampZoom(it.toDouble()).toFloat() },
                    valueRange = 1f..3f, steps = 39,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format(Locale.US, "%.2fx", zoom), style = MaterialTheme.typography.bodySmall)
            }

            // Border (sqToggleBorder / sqSetBorderWidth / sqSetBorderColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = borderOn, onCheckedChange = { borderOn = it })
                Text("Border", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = borderW, onValueChange = { borderW = it },
                    valueRange = 0f..40f, steps = 39,
                    modifier = Modifier.weight(1f)
                )
                Text("${borderW.roundToInt()}px", style = MaterialTheme.typography.bodySmall)
            }
            HexColorField("Border colour", borderColorHex) { borderColorHex = it }

            // Export + clear (sqExport / sqClear)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !exporting,
                    onClick = {
                        val (ex, ey) = clampedPan()
                        exporting = true
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    val bmp = renderSquareBitmap(
                                        img, SQ_EXPORT, mode, blur.toDouble(), solid,
                                        gradC1, gradC2, gradAngle,
                                        fit, zoom.toDouble(), ex, ey,
                                        borderOn, borderW.toDouble(), borderColor
                                    )
                                    val bos = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                    bos.toByteArray()
                                }
                                val name = sqExportFilename(fileName)
                                // v1.0.6: straight to Downloads, no share sheet
                                // (the web build downloads the file; the owner
                                // asked for the same here).
                                val saved = withContext(Dispatchers.IO) {
                                    saveFileToDownloads(context, name, "image/png", bytes)
                                }
                                if (saved) {
                                    toast(context, "Exported $name")
                                } else {
                                    toast(context, "Export failed.")
                                }
                            } catch (e: Exception) {
                                toast(context, "Export failed.")
                            } finally {
                                exporting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (exporting) "Exporting…" else "Export 2048 × 2048 PNG") }
                OutlinedButton(onClick = {
                    // sqClear(): back to the drop zone, zoom/pan reset.
                    bitmap = null; fileName = "image"; zoom = 1f; ox = 0.0; oy = 0.0
                }) { Text("Clear") }
            }
            Text(
                "Lossless PNG · saved as ${sqExportFilename(fileName)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HexColorField(
    label: String,
    hex: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = hex,
        onValueChange = { v ->
            val cleaned = v.trim()
            onChange(if (cleaned.startsWith("#") || cleaned.isEmpty()) cleaned else "#$cleaned")
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}
