package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.Ctc
import com.shawnrodis.toolbox.tools.OcrBox
import com.shawnrodis.toolbox.tools.OcrLineResult
import com.shawnrodis.toolbox.tools.PaddleDetModel
import com.shawnrodis.toolbox.tools.PaddleGeometry
import com.shawnrodis.toolbox.tools.PaddleOcrEngine
import com.shawnrodis.toolbox.tools.PaddleRecModel
import com.shawnrodis.toolbox.tools.PixelImage
import com.shawnrodis.toolbox.tools.RecOutput
import com.shawnrodis.toolbox.tools.boxesFromProbabilityMap
import com.shawnrodis.toolbox.tools.detectionTensor
import com.shawnrodis.toolbox.tools.groupBoxesIntoLines
import com.shawnrodis.toolbox.tools.groupResultsByLine
import com.shawnrodis.toolbox.tools.jsRound
import com.shawnrodis.toolbox.tools.mergeLineCrop
import com.shawnrodis.toolbox.tools.rotateTallCropIfNeeded
import com.shawnrodis.toolbox.tools.sortByReadingOrder
import com.shawnrodis.toolbox.tools.splitTextByPositions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3.0 — the PDF Tools local OCR engine is PaddleOCR (PP-OCRv6 tiny
 * over ONNX Runtime), a Kotlin port of ppu-paddle-ocr 6.6.1's pipeline
 * math (the library the web edition loads). These tests pin the pure
 * pieces against hand-computed expectations from the TypeScript:
 * JS rounding, resize rules, detection postprocess (connected
 * components + height-based padding), CTC greedy decode with its
 * gap-space and fullwidth refinements, dictionary alignment, line
 * grouping/merging/splitting, reading order, and the whole engine
 * driven by fake models (confidence gate included).
 */
class V130PaddleOcrTest {

    // ---------- helpers ----------

    /** Builds logits whose per-step argmax is [classes] with probability [prob]. */
    private fun logitsFor(classes: List<Int>, numClasses: Int, prob: Float = 0.9f): FloatArray {
        val data = FloatArray(classes.size * numClasses) { 0.01f }
        classes.forEachIndexed { t, c -> data[t * numClasses + c] = prob }
        return data
    }

    private fun probMap(w: Int, h: Int, rects: List<Pair<OcrBox, Float>>): FloatArray {
        val map = FloatArray(w * h)
        for ((r, p) in rects) {
            for (y in r.y until r.y + r.height) {
                for (x in r.x until r.x + r.width) {
                    map[y * w + x] = p
                }
            }
        }
        return map
    }

    // ---------- JS rounding / geometry ----------

    @Test
    fun jsRoundRoundsHalfTowardPositiveInfinity() {
        assertEquals(3, jsRound(2.5))
        assertEquals(4, jsRound(3.5))
        assertEquals(-2, jsRound(-2.5))
        assertEquals(0, jsRound(0.4))
        assertEquals(58, jsRound(58.125))
    }

    @Test
    fun resolveMaxSideLengthAutoRule() {
        assertEquals(960, PaddleGeometry.resolveMaxSideLength(1000))
        assertEquals(1856, PaddleGeometry.resolveMaxSideLength(2480))
        assertEquals(1920, PaddleGeometry.resolveMaxSideLength(4000))
    }

    @Test
    fun calculateResizeDimensionsDownscalesOnly() {
        val big = PaddleGeometry.calculateResizeDimensions(1700, 2200, 1856)
        assertEquals(1434, big.width)
        assertEquals(1856, big.height)
        val small = PaddleGeometry.calculateResizeDimensions(800, 600, 960)
        assertEquals(800, small.width)
        assertEquals(600, small.height)
        assertEquals(1.0, small.ratio, 0.0)
    }

    @Test
    fun detectionTensorDimsAndNormalization() {
        val white = PixelImage(64, 32, IntArray(64 * 32) { 0xFFFFFFFF.toInt() })
        val det = detectionTensor(white)
        assertEquals(64, det.tensorWidth)
        assertEquals(32, det.tensorHeight)
        assertEquals(1.0, det.ratio, 0.0)
        // R channel of a white pixel: 255*(1/(255*0.229)) - 0.485/0.229 = 2.2489...
        assertEquals(2.2489f, det.tensor[0], 0.001f)

        val black = PixelImage(64, 32, IntArray(64 * 32) { 0xFF000000.toInt() })
        assertEquals(-2.1179f, detectionTensor(black).tensor[0], 0.001f)

        val huge = PixelImage(2000, 1000, IntArray(2000 * 1000) { 0xFFFFFFFF.toInt() })
        val detHuge = detectionTensor(huge)
        assertEquals(1504, detHuge.tensorWidth)
        assertEquals(768, detHuge.tensorHeight)
        assertEquals(0.752, detHuge.ratio, 0.0001)
    }

    @Test
    fun bilinearResizeAveragesCheckerboard() {
        val img = PixelImage(
            2, 2,
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        )
        val one = img.resized(1, 1)
        val px = one.argb[0]
        assertEquals(128, (px ushr 16) and 0xFF)
        assertEquals(128, (px ushr 8) and 0xFF)
        assertEquals(128, px and 0xFF)
    }

    // ---------- dictionary ----------

    @Test
    fun parseDictionarySplitsAndKeepsTrailingBlank() {
        assertEquals(listOf("a", "b", ""), Ctc.parseDictionary("a\r\nb\n"))
        assertEquals(listOf(""), Ctc.parseDictionary(""))
    }

    @Test
    fun bundledDictionaryParsesToModelClassCount() {
        val file = File("src/main/assets/ocr/ppocrv6_tiny_dict.txt")
        assertTrue("dictionary asset must exist at ${file.path}", file.exists())
        val dict = Ctc.parseDictionary(file.readText(Charsets.UTF_8))
        // PP-OCRv6 tiny recognition model output classes = 6906; the
        // dictionary's first entry is the CTC blank and its last (from
        // the trailing newline) is the space slot.
        assertEquals(6906, dict.size)
        assertEquals("", dict.first())
        assertEquals("!", dict[1])
        assertEquals("", dict.last())
        assertEquals(dict, Ctc.alignDictionaryToClasses(dict, 6906))
    }

    @Test
    fun alignDictionaryRebasesShortDictionaries() {
        // Entries already blank-first but one short of the class count:
        // the space slot is appended.
        assertEquals(
            listOf("", "a", "b", ""),
            Ctc.alignDictionaryToClasses(listOf("", "a", "b"), 4)
        )
        // No leading blank: one is prepended, then the space slot appended.
        assertEquals(
            listOf("", "a", "b", "c", ""),
            Ctc.alignDictionaryToClasses(listOf("a", "b", "c"), 5)
        )
        // Trailing blanks are dropped first when that already lines up.
        assertEquals(
            listOf("", "a", "b"),
            Ctc.alignDictionaryToClasses(listOf("", "a", "b", ""), 3)
        )
    }

    // ---------- CTC decode ----------

    @Test
    fun ctcGreedyDecodeDedupesBlanksAndEmitsSpaceClass() {
        val dict = listOf("", "a", "b", "")
        // a a _ a b b _ <space>
        val logits = logitsFor(listOf(1, 1, 0, 1, 2, 2, 0, 3), 4)
        val decoded = Ctc.decodeLogitsRow(logits, 8, 4, dict)
        assertEquals("aab ", decoded.text)
        assertEquals(0.9f, decoded.confidence, 0.0001f)
        assertEquals(4, decoded.positions.size)
    }

    @Test
    fun ctcGreedyDecodeInjectsGapSpaces() {
        val dict = listOf("", "a", "b", "x", "y", "")
        // a at t=0, blanks, then b x y adjacent at t=10..12 of 20.
        val classes = MutableList(20) { 0 }
        classes[0] = 1
        classes[10] = 2
        classes[11] = 3
        classes[12] = 4
        val decoded = Ctc.decodeLogitsRow(logitsFor(classes, 6, 0.95f), 20, 6, dict)
        assertEquals("a bxy", decoded.text)
        assertEquals(0.95f, decoded.confidence, 0.0001f)
    }

    @Test
    fun ctcGreedyDecodeRefinesFullwidthAndDoubleSpaces() {
        val fwDict = listOf("", "\uFF21", "\uFF22", "")
        val fw = Ctc.decodeLogitsRow(logitsFor(listOf(1, 2), 4), 2, 4, fwDict)
        assertEquals("AB", fw.text)

        val dict = listOf("", "a", "")
        // <space> _ <space> a  ->  double space collapses to one.
        val decoded = Ctc.decodeLogitsRow(logitsFor(listOf(2, 0, 2, 1), 3), 4, 3, dict)
        assertEquals(" a", decoded.text)
    }

    @Test
    fun ctcGreedyDecodeSkipsUnkAtSpaceSlot() {
        val dict = listOf("", "a", Ctc.UNK_TOKEN)
        val decoded = Ctc.decodeLogitsRow(logitsFor(listOf(1, 2), 3), 2, 3, dict)
        assertEquals("a", decoded.text)
    }

    // ---------- detection postprocess ----------

    @Test
    fun probabilityMapComponentsPadByHeightAndFilterByArea() {
        val w = 64
        val h = 64
        val map = probMap(
            w, h,
            listOf(
                OcrBox(10, 10, 20, 10) to 0.9f,  // 200 px blob -> kept
                OcrBox(50, 50, 3, 3) to 0.9f,    // 9 px -> below min area 20
                OcrBox(38, 8, 6, 6) to 0.9f,     // 36 px -> kept
                OcrBox(5, 40, 5, 5) to 0.01f     // faint (byte 3 > 0) 25 px -> kept
            )
        )
        val boxes = boxesFromProbabilityMap(map, w, h, 1.0, w, h).toSet()
        assertEquals(
            setOf(
                OcrBox(4, 6, 32, 18),   // 20x10 blob, pads h=6 / v=4
                OcrBox(34, 6, 14, 10),  // 6x6 blob, pads h=4 / v=2
                OcrBox(2, 38, 11, 9)    // 5x5 blob, pads h=3 / v=2
            ),
            boxes
        )
    }

    @Test
    fun probabilityMapScalesBackToOriginalCoordinates() {
        val map = probMap(64, 64, listOf(OcrBox(10, 10, 20, 10) to 0.9f))
        val boxes = boxesFromProbabilityMap(map, 64, 64, 0.5, 128, 128)
        assertEquals(listOf(OcrBox(8, 12, 64, 36)), boxes)
    }

    @Test
    fun probabilityBelowByteThresholdIsBackground() {
        // round(0.001 * 255) = 0 -> background even for a large blob.
        val map = probMap(64, 64, listOf(OcrBox(10, 10, 20, 10) to 0.001f))
        assertTrue(boxesFromProbabilityMap(map, 64, 64, 1.0, 64, 64).isEmpty())
    }

    // ---------- line grouping / merging / splitting ----------

    @Test
    fun groupBoxesIntoLinesUsesPreviousBoxAndRunningAverage() {
        val a = OcrBox(0, 0, 100, 20)
        val b = OcrBox(120, 2, 80, 20)
        val e = OcrBox(0, 25, 50, 20)
        val c = OcrBox(0, 40, 90, 20)
        val d = OcrBox(110, 42, 60, 20)
        val lines = groupBoxesIntoLines(listOf(d, c, e, b, a))
        assertEquals(listOf(listOf(a, b), listOf(e), listOf(c, d)), lines)
    }

    @Test
    fun mergeLineCropGeometryAndSplitByPositions() {
        val source = PixelImage.white(200, 20)
        val boxes = listOf(OcrBox(0, 0, 40, 20), OcrBox(60, 0, 40, 20))
        val (merged, cropWidths) = mergeLineCrop(source, boxes)
        assertEquals(88, merged.width)   // 40 + gap 8 + 40
        assertEquals(20, merged.height)
        assertEquals(listOf(48, 40), cropWidths)

        val text = "hello world"
        val positions = (0 until text.length).map { (it + 0.5) / text.length }
        val pieces = splitTextByPositions(text, positions, cropWidths)
        assertEquals(listOf("hello ", "world"), pieces)
    }

    @Test
    fun rotateTallCropRotatesCounterClockwise() {
        // 2 wide x 4 tall, rows: a b / c d / e f / g h
        val px = intArrayOf(
            0xFF000001.toInt(), 0xFF000002.toInt(),
            0xFF000003.toInt(), 0xFF000004.toInt(),
            0xFF000005.toInt(), 0xFF000006.toInt(),
            0xFF000007.toInt(), 0xFF000008.toInt()
        )
        val rotated = rotateTallCropIfNeeded(PixelImage(2, 4, px))
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
        // CCW: top row becomes the left column read bottom-to-top.
        assertEquals(
            listOf(2, 4, 6, 8, 1, 3, 5, 7),
            rotated.argb.map { it and 0xFF }
        )
        // A wide crop is left alone.
        val wide = PixelImage(4, 2, IntArray(8) { 0xFFFFFFFF.toInt() })
        assertEquals(4, rotateTallCropIfNeeded(wide).width)
    }

    @Test
    fun sortByReadingOrderGroupsRowsBeforeColumns() {
        val b1 = OcrLineResult("b1", OcrBox(100, 0, 50, 20), 0.9f)
        val a1 = OcrLineResult("a1", OcrBox(0, 2, 50, 20), 0.9f)
        val c = OcrLineResult("c", OcrBox(0, 100, 50, 20), 0.9f)
        assertEquals(listOf(a1, b1, c), sortByReadingOrder(listOf(b1, a1, c)))
    }

    @Test
    fun groupResultsByLineJoinsSegmentsWithSpaces() {
        val results = listOf(
            OcrLineResult("Hello", OcrBox(0, 0, 50, 20), 0.9f),
            OcrLineResult("world", OcrBox(60, 1, 50, 20), 0.9f),
            OcrLineResult("Second", OcrBox(0, 40, 60, 20), 0.9f)
        )
        val lines = groupResultsByLine(results)
        assertEquals(2, lines.size)
        assertEquals(listOf("Hello", "world"), lines[0].map { it.text })
        assertEquals(listOf("Second"), lines[1].map { it.text })
    }

    // ---------- whole engine with fake models ----------

    private fun fakeDet(rects: List<OcrBox>): PaddleDetModel = PaddleDetModel { _, tw, th ->
        probMap(tw, th, rects.map { it to 0.99f })
    }

    private fun fakeRec(dict: List<String>, classes: List<Int>, prob: Float): PaddleRecModel =
        PaddleRecModel { _, _ ->
            RecOutput(logitsFor(classes, dict.size, prob), classes.size, dict.size)
        }

    @Test
    fun engineRecognizesSingleLineWithPaddedBox() {
        val dict = listOf("", "H", "i", "")
        val engine = PaddleOcrEngine(
            fakeDet(listOf(OcrBox(20, 20, 100, 20))),
            fakeRec(dict, listOf(1, 2), 0.99f),
            dict
        )
        val image = PixelImage(200, 100, IntArray(200 * 100) { 0xFFFFFFFF.toInt() })
        val results = engine.recognize(image)
        assertEquals(1, results.size)
        assertEquals("Hi", results[0].text)
        // Detected 100x20 at (20,20) -> pads h=12 / v=8 -> (8,12,124,36).
        assertEquals(OcrBox(8, 12, 124, 36), results[0].box)
        assertEquals(0.99f, results[0].confidence, 0.0001f)
    }

    @Test
    fun engineReturnsTwoLinesInReadingOrder() {
        val dict = listOf("", "H", "i", "")
        val engine = PaddleOcrEngine(
            fakeDet(listOf(OcrBox(20, 60, 100, 20), OcrBox(20, 10, 100, 20))),
            fakeRec(dict, listOf(1, 2), 0.99f),
            dict
        )
        val image = PixelImage(200, 100, IntArray(200 * 100) { 0xFFFFFFFF.toInt() })
        val results = engine.recognize(image)
        assertEquals(2, results.size)
        assertTrue(results[0].box.y < results[1].box.y)
    }

    @Test
    fun engineConfidenceGateDropsWeakAndSymbolOnlyText() {
        val dict = listOf("", "H", "i", "")
        val image = PixelImage(200, 100, IntArray(200 * 100) { 0xFFFFFFFF.toInt() })
        val det = fakeDet(listOf(OcrBox(20, 20, 100, 20)))

        // Mean confidence 0.4 < 0.5 -> dropped.
        val weak = PaddleOcrEngine(det, fakeRec(dict, listOf(1, 2), 0.4f), dict)
        assertTrue(weak.recognize(image).isEmpty())

        // Symbol-only text faces the raised 0.8 bar.
        val symDict = listOf("", "!", "")
        val symWeak = PaddleOcrEngine(det, fakeRec(symDict, listOf(1), 0.7f), symDict)
        assertTrue(symWeak.recognize(image).isEmpty())
        val symStrong = PaddleOcrEngine(det, fakeRec(symDict, listOf(1), 0.85f), symDict)
        assertEquals(1, symStrong.recognize(image).size)
    }

    @Test
    fun engineEmptyWhenNoDetection() {
        val dict = listOf("", "H", "i", "")
        val engine = PaddleOcrEngine(
            fakeDet(emptyList()),
            fakeRec(dict, listOf(1, 2), 0.99f),
            dict
        )
        val image = PixelImage(200, 100, IntArray(200 * 100) { 0xFFFFFFFF.toInt() })
        assertTrue(engine.recognize(image).isEmpty())
    }
}
