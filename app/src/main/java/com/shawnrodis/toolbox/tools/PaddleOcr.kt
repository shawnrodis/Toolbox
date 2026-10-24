package com.shawnrodis.toolbox.tools

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * PaddleOCR (PP-OCRv6) pipeline for PDF Tools' local OCR engine — a
 * faithful Kotlin port of the TypeScript pipeline in `ppu-paddle-ocr`
 * 6.6.1 (the library the web edition loads), so both editions run the
 * same models with the same math and produce the same text:
 *
 *  - detection: resize (longest side capped by the "auto" rule) onto a
 *    32-multiple canvas, ImageNet mean/std tensor, then the package's
 *    canvas-native postprocess — probability map -> bytes -> 8-connected
 *    components (pixel-count area >= 20) -> boxes padded by 0.4/0.6 of
 *    their HEIGHT (this generation of the pipeline pads instead of
 *    DB-unclipping) -> scaled back to original coordinates.
 *  - recognition (default "per-line" strategy): detection boxes are
 *    grouped into lines; single-box lines are cropped flat (tall crops
 *    rotated 90° CCW), multi-box lines are merged onto one white canvas
 *    at a common height and the merged text is re-split to the boxes by
 *    CTC character positions; each crop is resized to height 48 and the
 *    tensor is the R channel only, (v / 127.5) - 1, replicated x3.
 *  - decode: CTC greedy (blank 0, dedupe, last dictionary index is the
 *    space class), then the package's gap-space injection and
 *    fullwidth/double-space refinement; results below 0.5 confidence
 *    are dropped (0.8 for symbol-only text).
 *
 * Everything here is pure Kotlin over IntArray ARGB pixels — no Android
 * classes — so the whole pipeline is unit-testable on the JVM with fake
 * models. The ONNX Runtime glue lives in PaddleOcrRuntime.kt.
 *
 * One deliberate approximation: image resampling is a standard
 * centre-aligned bilinear (the browser editions resample with canvas
 * drawImage, the Node path with OpenCV). The algorithms agree; the
 * interpolated pixels can differ in the last bit, which does not move
 * OCR results in practice.
 */

/** JS Math.round: halves round toward +infinity (Kotlin's roundToInt is banker's). */
fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

/** An axis-aligned box in image pixels (ppu's {x, y, width, height}). */
data class OcrBox(val x: Int, val y: Int, val width: Int, val height: Int)

/** One recognised text segment with its box and confidence (ppu's RecognitionResult). */
data class OcrLineResult(val text: String, val box: OcrBox, val confidence: Float)

/** Splits a string into Unicode code points (JS [...text]). */
fun codePointsOf(s: String): List<String> {
    val out = ArrayList<String>(s.length)
    var i = 0
    while (i < s.length) {
        val cp = Character.codePointAt(s, i)
        out.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return out
}

private fun firstCodePoint(s: String): Int =
    if (s.isEmpty()) -1 else Character.codePointAt(s, 0)

/** JS /\s/ on a single code point. */
private fun isJsWhitespace(cp: Int): Boolean =
    cp == 0xFEFF || Character.isWhitespace(cp) || Character.isSpaceChar(cp)

/** JS /\p{L}\p{N}/u membership for one code point. */
private fun isLetterOrDigit(cp: Int): Boolean {
    if (cp < 0) return false
    if (Character.isLetter(cp)) return true
    return when (Character.getType(cp)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
        Character.LETTER_NUMBER.toInt(),
        Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }
}

fun containsLetterOrDigit(s: String): Boolean {
    var i = 0
    while (i < s.length) {
        val cp = Character.codePointAt(s, i)
        if (isLetterOrDigit(cp)) return true
        i += Character.charCount(cp)
    }
    return false
}

/**
 * An image as raw ARGB pixels (row-major), with the small set of canvas
 * operations the pipeline needs. Out-of-bounds crop areas read as
 * transparent black, matching canvas behaviour.
 */
class PixelImage(val width: Int, val height: Int, val argb: IntArray) {

    init {
        require(argb.size == width * height) { "pixel buffer size mismatch" }
    }

    /** Centre-aligned bilinear resize (canvas drawImage equivalent). */
    fun resized(newW: Int, newH: Int): PixelImage {
        require(newW > 0 && newH > 0) { "resize target must be positive" }
        if (newW == width && newH == height) return this
        val out = IntArray(newW * newH)
        val xRatio = width.toDouble() / newW
        val yRatio = height.toDouble() / newH
        for (dy in 0 until newH) {
            val srcY = ((dy + 0.5) * yRatio - 0.5).coerceIn(0.0, (height - 1).toDouble())
            val y0 = floor(srcY).toInt()
            val y1 = min(y0 + 1, height - 1)
            val fy = srcY - y0
            for (dx in 0 until newW) {
                val srcX = ((dx + 0.5) * xRatio - 0.5).coerceIn(0.0, (width - 1).toDouble())
                val x0 = floor(srcX).toInt()
                val x1 = min(x0 + 1, width - 1)
                val fx = srcX - x0
                val p00 = argb[y0 * width + x0]
                val p01 = argb[y0 * width + x1]
                val p10 = argb[y1 * width + x0]
                val p11 = argb[y1 * width + x1]
                var px = 0xFF shl 24
                for (shift in listOf(16, 8, 0)) {
                    val c00 = (p00 ushr shift) and 0xFF
                    val c01 = (p01 ushr shift) and 0xFF
                    val c10 = (p10 ushr shift) and 0xFF
                    val c11 = (p11 ushr shift) and 0xFF
                    val top = c00 * (1 - fx) + c01 * fx
                    val bot = c10 * (1 - fx) + c11 * fx
                    val v = jsRound(top * (1 - fy) + bot * fy).coerceIn(0, 255)
                    px = px or (v shl shift)
                }
                out[dy * newW + dx] = px
            }
        }
        return PixelImage(newW, newH, out)
    }

    /** Exact-pixel crop; areas outside the source stay 0 (transparent black). */
    fun crop(box: OcrBox): PixelImage {
        val out = IntArray(box.width * box.height)
        for (y in 0 until box.height) {
            val sy = box.y + y
            if (sy < 0 || sy >= height) continue
            for (x in 0 until box.width) {
                val sx = box.x + x
                if (sx < 0 || sx >= width) continue
                out[y * box.width + x] = argb[sy * width + sx]
            }
        }
        return PixelImage(box.width, box.height, out)
    }

    /** 90 degrees counter-clockwise (canvas translate+rotate(-90deg) in the source). */
    fun rotatedCcw(): PixelImage {
        val out = IntArray(width * height)
        val newW = height
        for (y2 in 0 until width) {
            for (x2 in 0 until height) {
                out[y2 * newW + x2] = argb[x2 * width + (width - 1 - y2)]
            }
        }
        return PixelImage(newW, width, out)
    }

    /** Copies this image into [dst] at (dx, dy), clipped to dst's bounds. */
    fun blitInto(dst: PixelImage, dx: Int, dy: Int) {
        for (y in 0 until height) {
            val ty = dy + y
            if (ty < 0 || ty >= dst.height) continue
            for (x in 0 until width) {
                val tx = dx + x
                if (tx < 0 || tx >= dst.width) continue
                dst.argb[ty * dst.width + tx] = argb[y * width + x]
            }
        }
    }

    companion object {
        fun white(w: Int, h: Int): PixelImage =
            PixelImage(w, h, IntArray(w * h) { 0xFFFFFFFF.toInt() })
    }
}

/** Box/resize geometry from the package's detection/box-geometry.ts. */
object PaddleGeometry {

    data class Resize(val width: Int, val height: Int, val ratio: Double)

    /** resolveMaxSideLength("auto", longest): min(1920, max(960, round(longest*0.75/32)*32)). */
    fun resolveMaxSideLength(longestSide: Int): Int =
        min(1920, max(960, jsRound(longestSide * 0.75 / 32.0) * 32))

    /** calculateResizeDimensions: downscale only, ratio preserved, JS rounding. */
    fun calculateResizeDimensions(w: Int, h: Int, maxSideLength: Int): Resize {
        if (max(w, h) > maxSideLength) {
            val ratio = maxSideLength.toDouble() / max(h, w)
            return Resize(jsRound(w * ratio), jsRound(h * ratio), ratio)
        }
        return Resize(w, h, 1.0)
    }

    /** scaleBox from the recognition service (width/height floor at 1). */
    fun scaleBox(box: OcrBox, ratio: Double): OcrBox = OcrBox(
        jsRound(box.x * ratio),
        jsRound(box.y * ratio),
        max(1, jsRound(box.width * ratio)),
        max(1, jsRound(box.height * ratio))
    )
}

/** Detection preprocess output (tensor + geometry for the postprocess). */
data class DetPreprocessed(
    val tensor: FloatArray,
    val tensorWidth: Int,
    val tensorHeight: Int,
    val ratio: Double
)

/**
 * Detection preprocess: resize per the auto rule onto a 32-multiple
 * canvas (image top-left, rest black) and build the CHW float tensor
 * with ImageNet mean/std, exactly as the package's imageToTensor.
 */
fun detectionTensor(image: PixelImage): DetPreprocessed {
    val longest = max(image.width, image.height)
    val maxSide = PaddleGeometry.resolveMaxSideLength(longest)
    val resize = PaddleGeometry.calculateResizeDimensions(image.width, image.height, maxSide)
    val tensorW = ceil(resize.width / 32.0).toInt() * 32
    val tensorH = ceil(resize.height / 32.0).toInt() * 32
    val resized = image.resized(resize.width, resize.height)
    val plane = tensorW * tensorH
    val tensor = FloatArray(3 * plane)
    val mean = doubleArrayOf(0.485, 0.456, 0.406)
    val std = doubleArrayOf(0.229, 0.224, 0.225)
    // Padding area = black pixels normalised: 0 * scale - mean/std.
    for (c in 0..2) {
        java.util.Arrays.fill(tensor, c * plane, (c + 1) * plane, (-(mean[c] / std[c])).toFloat())
    }
    for (y in 0 until resize.height) {
        for (x in 0 until resize.width) {
            val px = resized.argb[y * resize.width + x]
            val i = y * tensorW + x
            tensor[i] = (((px ushr 16) and 0xFF) * (1.0 / (255 * std[0])) - mean[0] / std[0]).toFloat()
            tensor[plane + i] = (((px ushr 8) and 0xFF) * (1.0 / (255 * std[1])) - mean[1] / std[1]).toFloat()
            tensor[2 * plane + i] = ((px and 0xFF) * (1.0 / (255 * std[2])) - mean[2] / std[2]).toFloat()
        }
    }
    return DetPreprocessed(tensor, tensorW, tensorH, resize.ratio)
}

/**
 * Detection postprocess, canvas-native flavour (what the web edition
 * runs): probability bytes (round(p*255) > 0) -> 8-connected components
 * -> keep pixel-count area >= 20 -> pad by height fractions -> scale to
 * original coordinates -> clamp -> keep width/height > 5.
 */
fun boxesFromProbabilityMap(
    prob: FloatArray,
    tensorW: Int,
    tensorH: Int,
    ratio: Double,
    origW: Int,
    origH: Int,
    minArea: Int = 20,
    paddingVertical: Double = 0.4,
    paddingHorizontal: Double = 0.6
): List<OcrBox> {
    val n = tensorW * tensorH
    val fg = BooleanArray(n) { i -> jsRound(prob[i].coerceIn(0f, 1f).toDouble() * 255.0) > 0 }
    val visited = BooleanArray(n)
    val boxes = mutableListOf<OcrBox>()
    val stack = ArrayDeque<Int>()
    for (start in 0 until n) {
        if (visited[start]) continue
        visited[start] = true
        if (!fg[start]) continue
        var minX = start % tensorW
        var maxX = minX
        var minY = start / tensorW
        var maxY = minY
        var area = 0
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val flat = stack.removeLast()
            area++
            val x = flat % tensorW
            val y = flat / tensorW
            if (x < minX) minX = x else if (x > maxX) maxX = x
            if (y < minY) minY = y else if (y > maxY) maxY = y
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || nx >= tensorW || ny < 0 || ny >= tensorH) continue
                    val nf = ny * tensorW + nx
                    if (visited[nf]) continue
                    visited[nf] = true
                    if (fg[nf]) stack.addLast(nf)
                }
            }
        }
        if (area < minArea) continue
        var x0 = minX
        var y0 = minY
        var x1 = maxX + 1
        var y1 = maxY + 1
        val bboxH = y1 - y0
        val vPad = jsRound(bboxH * paddingVertical)
        val hPad = jsRound(bboxH * paddingHorizontal)
        x0 = max(0, x0 - hPad)
        y0 = max(0, y0 - vPad)
        x1 = min(tensorW, x1 + hPad)
        y1 = min(tensorH, y1 + vPad)
        val scale = 1.0 / ratio
        val sx0 = max(0, jsRound(x0 * scale))
        val sy0 = max(0, jsRound(y0 * scale))
        var bw = jsRound(x1 * scale) - sx0
        var bh = jsRound(y1 * scale) - sy0
        if (sx0 + bw > origW) bw = origW - sx0
        if (sy0 + bh > origH) bh = origH - sy0
        if (bw > 5 && bh > 5) boxes.add(OcrBox(sx0, sy0, bw, bh))
    }
    return boxes
}

/** CTC decoding + dictionary handling, ported from the package's recognition/ctc.ts. */
object Ctc {
    const val BLANK_INDEX = 0
    const val MIN_CROP_WIDTH = 8
    const val UNK_TOKEN = "<unk>"

    private const val GAP_QUANTA_CROSS_CLASS = 1.5
    private const val GAP_QUANTA_SAME_CLASS = 2.5
    private const val FULLWIDTH_OFFSET = 65248

    data class Decoded(val text: String, val confidence: Float, val positions: List<Double>)

    /** parseDictionary: split on \n (CRLF tolerated); a trailing newline yields a final "" entry. */
    fun parseDictionary(content: String): List<String> = content.split(Regex("\\r?\\n"))

    private fun dropTrailingBlanks(entries: List<String>): List<String> {
        var end = entries.size
        while (end > 0 && entries[end - 1] == "") end--
        return entries.subList(0, end)
    }

    /**
     * alignDictionaryToClasses: the dictionary must line up with the
     * model's class count (blank first, space slot last); shorter
     * dictionaries are re-based onto a prepended blank entry.
     */
    fun alignDictionaryToClasses(dict: List<String>, numClasses: Int): List<String> {
        if (dict.size == numClasses) return dict
        val entries = dropTrailingBlanks(dict)
        if (entries.size == numClasses) return entries
        val aligned = ArrayList<String>(entries.size + 2)
        aligned.add("")
        aligned.addAll(if (entries.firstOrNull() == "") entries.subList(1, entries.size) else entries)
        if (aligned.size == numClasses - 1) aligned.add("")
        return aligned
    }

    private fun charClass(s: String): Int {
        val cp = firstCodePoint(s)
        if (cp < 0) return 2
        if (Character.isLetter(cp)) return 0
        return when (Character.getType(cp)) {
            Character.DECIMAL_DIGIT_NUMBER.toInt(),
            Character.LETTER_NUMBER.toInt(),
            Character.OTHER_NUMBER.toInt() -> 1
            else -> 2
        }
    }

    /**
     * injectGapSpaces: when consecutive characters sit much farther
     * apart than the median spacing (scaled by a same/cross character
     * class quantum), a space is inserted between them.
     */
    fun injectGapSpaces(chars: MutableList<String>, positions: MutableList<Double>) {
        if (chars.size < 4) return
        val deltas = ArrayList<Double>(positions.size)
        for (i in 1 until positions.size) deltas.add(positions[i] - positions[i - 1])
        if (deltas.isEmpty()) return
        val sorted = deltas.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0) return
        val quantum = sorted.firstOrNull { it > 0 } ?: 0.0
        if (quantum <= 0) return
        for (i in chars.size - 1 downTo 1) {
            val prev = positions[i - 1]
            val curr = positions[i]
            val k = if (charClass(chars[i]) == charClass(chars[i - 1])) {
                GAP_QUANTA_SAME_CLASS
            } else {
                GAP_QUANTA_CROSS_CLASS
            }
            if (curr - prev > median + k * quantum &&
                chars[i] != " " && chars[i - 1] != " " && chars[i] != chars[i - 1]
            ) {
                chars.add(i, " ")
                positions.add(i, (prev + curr) / 2)
            }
        }
    }

    private fun hasCjk(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = Character.codePointAt(s, i)
            if (cp in 0x2E80..0x9FFF || cp in 0xAC00..0xD7AF || cp in 0xF900..0xFAFF) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /**
     * refineDecodedChars: collapse double spaces; convert fullwidth
     * forms to halfwidth unless the text contains CJK.
     */
    fun refineDecodedChars(chars: MutableList<String>, positions: MutableList<Double>) {
        for (i in chars.size - 1 downTo 1) {
            if (chars[i] == " " && chars[i - 1] == " ") {
                chars.removeAt(i)
                positions.removeAt(i)
            }
        }
        if (hasCjk(chars.joinToString(""))) return
        for (i in chars.indices) {
            val cp = firstCodePoint(chars[i])
            if (cp in 65281..65374) {
                chars[i] = String(Character.toChars(cp - FULLWIDTH_OFFSET))
            } else if (cp == 12288) {
                chars[i] = " "
            }
        }
    }

    /**
     * ctcGreedyDecode: per-timestep argmax, blank/repeat removal, the
     * dictionary's LAST index emitted as a space; confidence is the mean
     * of the emitted characters' probabilities. Positions are character
     * centres as fractions of the sequence length.
     */
    fun ctcGreedyDecode(
        logits: FloatArray,
        sequenceLength: Int,
        numClasses: Int,
        charDict: List<String>,
        spaceRecovery: Boolean = false
    ): Decoded {
        val dictLen = charDict.size
        val lastDictIndex = dictLen - 1
        val emitted = ArrayList<String>()
        val positions = ArrayList<Double>()
        var lastCharIndex = -1
        var confidenceSum = 0.0
        var confidenceCount = 0
        for (t in 0 until sequenceLength) {
            val base = t * numClasses
            var maxProb = logits[base]
            var maxIndex = 0
            for (c in 1 until numClasses) {
                val p = logits[base + c]
                if (p > maxProb) {
                    maxProb = p
                    maxIndex = c
                }
            }
            if (maxIndex == BLANK_INDEX || maxIndex == lastCharIndex) {
                lastCharIndex = maxIndex
                continue
            }
            if (maxIndex in 0 until dictLen) {
                if (spaceRecovery && maxIndex != lastDictIndex &&
                    logits[base + lastDictIndex] > 0.001f &&
                    emitted.lastOrNull() != " "
                ) {
                    emitted.add(" ")
                    positions.add((t + 0.5) / sequenceLength)
                }
                val ch = charDict[maxIndex]
                if (maxIndex == lastDictIndex) {
                    if (ch != UNK_TOKEN) {
                        emitted.add(" ")
                        confidenceSum += maxProb
                        confidenceCount++
                        positions.add((t + 0.5) / sequenceLength)
                    }
                } else {
                    emitted.add(ch)
                    confidenceSum += maxProb
                    confidenceCount++
                    positions.add((t + 0.5) / sequenceLength)
                }
            }
            lastCharIndex = maxIndex
        }
        injectGapSpaces(emitted, positions)
        refineDecodedChars(emitted, positions)
        val confidence = if (confidenceCount > 0) (confidenceSum / confidenceCount).toFloat() else 0f
        return Decoded(emitted.joinToString(""), confidence, positions)
    }

    /** decodeLogitsRow: align the dictionary to the class count, then greedy-decode. */
    fun decodeLogitsRow(
        rowData: FloatArray,
        sequenceLength: Int,
        numClasses: Int,
        charactersDictionary: List<String>,
        spaceRecovery: Boolean = false
    ): Decoded {
        val dict = alignDictionaryToClasses(charactersDictionary, numClasses)
        return ctcGreedyDecode(rowData, sequenceLength, numClasses, dict, spaceRecovery)
    }
}

/** groupBoxesIntoLines from the package's recognition/line-grouping.ts. */
fun groupBoxesIntoLines(boxes: List<OcrBox>): List<List<OcrBox>> {
    if (boxes.isEmpty()) return emptyList()
    val sorted = boxes.sortedWith(compareBy<OcrBox> { it.y }.thenBy { it.x })
    val lines = mutableListOf<List<OcrBox>>()
    var currentLine = mutableListOf(sorted[0])
    var currentLineHeightSum = sorted[0].height
    var avgHeight = sorted[0].height.toDouble()
    for (i in 1 until sorted.size) {
        val current = sorted[i]
        val previous = sorted[i - 1]
        val verticalGap = abs(current.y - previous.y)
        if (verticalGap <= avgHeight * 0.5) {
            currentLine.add(current)
            currentLineHeightSum += current.height
            avgHeight = currentLineHeightSum.toDouble() / currentLine.size
        } else {
            currentLine.sortBy { it.x }
            lines.add(currentLine)
            currentLine = mutableListOf(current)
            currentLineHeightSum = current.height
            avgHeight = current.height.toDouble()
        }
    }
    if (currentLine.isNotEmpty()) {
        currentLine.sortBy { it.x }
        lines.add(currentLine)
    }
    return lines
}

private const val MAX_BOX_STRETCH = 4.0
private const val MAX_MERGED_WIDTH = 16384

/**
 * mergeLineCrop: draw every box of a line stretched to the line's
 * common height on one white canvas (gap = 0.4 x height between
 * segments), returning the merged image and each segment's width
 * including its trailing gap.
 */
fun mergeLineCrop(source: PixelImage, lineBoxes: List<OcrBox>): Pair<PixelImage, List<Int>> {
    val minY = lineBoxes.minOf { it.y }
    val maxBottom = lineBoxes.maxOf { it.y + it.height }
    val commonHeight = maxBottom - minY
    var gap = max(1, jsRound(commonHeight * 0.4))
    var widths = lineBoxes.map { box ->
        max(1, jsRound(box.width * min(commonHeight.toDouble() / box.height, MAX_BOX_STRETCH)))
    }
    var totalWidth = widths.sum() + gap * (lineBoxes.size - 1)
    if (totalWidth > MAX_MERGED_WIDTH) {
        val shrink = MAX_MERGED_WIDTH.toDouble() / totalWidth
        widths = widths.map { max(1, jsRound(it * shrink)) }
        gap = max(1, floor(gap * shrink).toInt())
        totalWidth = widths.sum() + gap * (lineBoxes.size - 1)
    }
    val merged = PixelImage.white(totalWidth, commonHeight)
    var offsetX = 0
    val cropWidths = ArrayList<Int>(lineBoxes.size)
    lineBoxes.forEachIndexed { i, box ->
        val stretched = source.crop(box).resized(widths[i], commonHeight)
        stretched.blitInto(merged, offsetX, 0)
        val trailingGap = if (i < lineBoxes.size - 1) gap else 0
        cropWidths.add(widths[i] + trailingGap)
        offsetX += widths[i] + trailingGap
    }
    return merged to cropWidths
}

private const val CUT_SNAP_RANGE = 4

/** splitBatchTextByWidths: distribute merged text over segments by width, snapping cuts to spaces. */
fun splitBatchTextByWidths(text: String, cropWidths: List<Int>): List<String> {
    if (cropWidths.size == 1) return listOf(text)
    val chars = codePointsOf(text)
    if (chars.isEmpty()) return List(cropWidths.size) { "" }
    val totalWidth = cropWidths.sum()
    val charWidth = totalWidth.toDouble() / chars.size
    val result = ArrayList<String>(cropWidths.size)
    var charIdx = 0
    for (i in cropWidths.indices) {
        if (i == cropWidths.size - 1) {
            result.add(chars.subList(charIdx, chars.size).joinToString(""))
            break
        }
        val ideal = min(charIdx + jsRound(cropWidths[i] / charWidth), chars.size)
        var cut = ideal
        var skipSpace = false
        var d = 0
        while (d <= CUT_SNAP_RANGE && !skipSpace) {
            for (cand in listOf(ideal - d, ideal + d)) {
                val ch = chars.getOrNull(cand)
                if (cand > charIdx && cand < chars.size && ch != null &&
                    isJsWhitespace(firstCodePoint(ch))
                ) {
                    cut = cand
                    skipSpace = true
                    break
                }
            }
            d++
        }
        result.add(chars.subList(charIdx, cut).joinToString(""))
        charIdx = if (skipSpace) cut + 1 else cut
    }
    return result
}

/**
 * splitTextByPositions: assign each decoded character to the segment
 * whose cumulative width fraction contains its CTC position; falls
 * back to width-based splitting when positions don't line up.
 */
fun splitTextByPositions(text: String, positions: List<Double>, segmentWidths: List<Int>): List<String> {
    val chars = codePointsOf(text)
    if (positions.size != chars.size || segmentWidths.isEmpty()) {
        return splitBatchTextByWidths(text, segmentWidths)
    }
    val totalWidth = segmentWidths.sum().toDouble()
    val result = Array(segmentWidths.size) { StringBuilder() }
    var seg = 0
    var segEnd = segmentWidths[0] / totalWidth
    for (i in chars.indices) {
        val pos = positions[i]
        while (pos >= segEnd && seg < segmentWidths.size - 1) {
            seg++
            segEnd += segmentWidths[seg] / totalWidth
        }
        result[seg].append(chars[i])
    }
    return result.map { it.toString() }
}

/** rotateTallCropIfNeeded: crops at least 1.5x taller than wide rotate 90° CCW. */
fun rotateTallCropIfNeeded(crop: PixelImage): PixelImage {
    if (crop.height.toDouble() / crop.width < 1.5) return crop
    return crop.rotatedCcw()
}

/** sortByReadingOrder from the package's recognition strategies. */
fun sortByReadingOrder(results: List<OcrLineResult>): List<OcrLineResult> =
    results.sortedWith(Comparator { a, b ->
        if (abs(a.box.y - b.box.y) < (a.box.height + b.box.height) / 4.0) {
            a.box.x - b.box.x
        } else {
            a.box.y - b.box.y
        }
    })

/** groupResultsByLine from the package's recognition/line-grouping.ts. */
fun groupResultsByLine(results: List<OcrLineResult>): List<List<OcrLineResult>> {
    if (results.isEmpty()) return emptyList()
    val lines = mutableListOf<List<OcrLineResult>>()
    var currentLine = mutableListOf<OcrLineResult>()
    var currentY = results[0].box.y
    var avgHeight = results[0].box.height.toDouble()
    for (result in results) {
        if (abs(result.box.y - currentY) < avgHeight / 2) {
            currentLine.add(result)
            avgHeight = (avgHeight * (currentLine.size - 1) + result.box.height) / currentLine.size
        } else {
            currentLine.sortBy { it.box.x }
            lines.add(currentLine)
            currentLine = mutableListOf(result)
            currentY = result.box.y
            avgHeight = result.box.height.toDouble()
        }
    }
    if (currentLine.isNotEmpty()) {
        currentLine.sortBy { it.box.x }
        lines.add(currentLine)
    }
    return lines
}

/** Detection model: probability map (tensorW x tensorH, row-major) for a prepared tensor. */
fun interface PaddleDetModel {
    fun run(tensor: FloatArray, width: Int, height: Int): FloatArray
}

/** Recognition model output: raw logits for one crop. */
data class RecOutput(val data: FloatArray, val seqLen: Int, val numClasses: Int)

/** Recognition model: run one crop tensor (1 x 3 x 48 x width). */
fun interface PaddleRecModel {
    fun run(tensor: FloatArray, width: Int): RecOutput
}

/**
 * The PP-OCR pipeline with the package's default options:
 * minimumConfidence 0.5 (+0.3 for symbol-only text), per-line strategy,
 * crop source capped at 2000 px on the longest side, vertical-crop
 * rotation on, space recovery off.
 */
class PaddleOcrEngine(
    private val detModel: PaddleDetModel,
    private val recModel: PaddleRecModel,
    private val dictionary: List<String>
) {

    /** Recognises an image, returning line-level results in reading order (original-image pixels). */
    fun recognize(image: PixelImage): List<OcrLineResult> {
        val det = detectionTensor(image)
        val prob = detModel.run(det.tensor, det.tensorWidth, det.tensorHeight)
        val boxes = boxesFromProbabilityMap(
            prob, det.tensorWidth, det.tensorHeight, det.ratio, image.width, image.height
        )
        if (boxes.isEmpty()) return emptyList()

        // Recognition crops come from a source capped at 2000 px on its
        // longest side; boxes are scaled into that space and results
        // scaled back afterwards (buildCropCanvas in the source).
        val cropCalc = PaddleGeometry.calculateResizeDimensions(image.width, image.height, MAX_CROP_SOURCE_SIDE)
        val cropSource: PixelImage
        val cropBoxes: List<OcrBox>
        if (cropCalc.ratio == 1.0) {
            cropSource = image
            cropBoxes = boxes
        } else {
            cropSource = image.resized(cropCalc.width, cropCalc.height)
            cropBoxes = boxes.map { PaddleGeometry.scaleBox(it, cropCalc.ratio) }
        }
        val validBoxes = cropBoxes.filter { it.width > 0 && it.height > 0 }
        if (validBoxes.isEmpty()) return emptyList()

        // Per-line strategy.
        val results = ArrayList<OcrLineResult>()
        for (lineBoxes in groupBoxesIntoLines(validBoxes)) {
            if (lineBoxes.size == 1) {
                val box = lineBoxes[0]
                val decoded = recognizeCrop(rotateTallCropIfNeeded(cropSource.crop(box)))
                results.add(OcrLineResult(decoded.text, box, decoded.confidence))
            } else {
                val (merged, cropWidths) = mergeLineCrop(cropSource, lineBoxes)
                val decoded = recognizeCrop(merged)
                val pieces = splitTextByPositions(decoded.text, decoded.positions, cropWidths)
                lineBoxes.forEachIndexed { i, box ->
                    results.add(OcrLineResult((pieces.getOrNull(i) ?: "").trim(), box, decoded.confidence))
                }
            }
        }

        var sorted = sortByReadingOrder(results)
        if (cropCalc.ratio != 1.0) {
            sorted = sorted.map { it.copy(box = PaddleGeometry.scaleBox(it.box, 1.0 / cropCalc.ratio)) }
        }
        // Confidence gate: 0.5, or 0.8 for text with no letter or digit.
        return sorted.filter { r ->
            val bar = if (containsLetterOrDigit(r.text)) MIN_CONFIDENCE else min(1.0, MIN_CONFIDENCE + SYMBOL_CONFIDENCE_OFFSET)
            r.confidence >= bar
        }
    }

    /** One crop -> resize to height 48 (R channel tensor) -> rec model -> CTC decode. */
    private fun recognizeCrop(crop: PixelImage): Ctc.Decoded {
        val tensorWidth = max(Ctc.MIN_CROP_WIDTH, jsRound(REC_HEIGHT * (crop.width.toDouble() / crop.height)))
        val resized = crop.resized(tensorWidth, REC_HEIGHT)
        val plane = REC_HEIGHT * tensorWidth
        val tensor = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val r = (resized.argb[i] ushr 16) and 0xFF
            tensor[i] = (r / 127.5 - 1).toFloat()
        }
        System.arraycopy(tensor, 0, tensor, plane, plane)
        System.arraycopy(tensor, 0, tensor, 2 * plane, plane)
        val out = recModel.run(tensor, tensorWidth)
        return Ctc.decodeLogitsRow(out.data, out.seqLen, out.numClasses, dictionary)
    }

    companion object {
        const val REC_HEIGHT = 48
        const val MAX_CROP_SOURCE_SIDE = 2000
        const val MIN_CONFIDENCE = 0.5
        const val SYMBOL_CONFIDENCE_OFFSET = 0.3
    }
}
