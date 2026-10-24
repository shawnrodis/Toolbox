package com.shawnrodis.toolbox.tools

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * ONNX Runtime glue for the PaddleOCR local engine: lazily builds the
 * two sessions (PP-OCRv6 tiny detection + recognition, bundled as
 * assets — see assets/ocr/NOTICE) plus the character dictionary once,
 * then serves every OCR run from the cached [PaddleOcrEngine]. The
 * runtime is pinned to 1.26.0, the same version the web edition loads
 * as onnxruntime-web.
 */
object PaddleOcrRuntime {

    private const val DET_ASSET = "ocr/PP-OCRv6_tiny_det.onnx"
    private const val REC_ASSET = "ocr/PP-OCRv6_tiny_rec.onnx"
    private const val DICT_ASSET = "ocr/ppocrv6_tiny_dict.txt"

    @Volatile
    private var cached: PaddleOcrEngine? = null

    /** Runs the full PP-OCR pipeline over [bitmap]; results are line-level, in bitmap pixels. */
    fun recognize(context: Context, bitmap: Bitmap): List<OcrLineResult> {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return engine(context).recognize(PixelImage(w, h, px))
    }

    private fun engine(context: Context): PaddleOcrEngine {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val env = OrtEnvironment.getEnvironment()
            val detSession = env.createSession(
                app.assets.open(DET_ASSET).readBytes(), OrtSession.SessionOptions()
            )
            val recSession = env.createSession(
                app.assets.open(REC_ASSET).readBytes(), OrtSession.SessionOptions()
            )
            val dictText = app.assets.open(DICT_ASSET).bufferedReader(Charsets.UTF_8).readText()
            val built = PaddleOcrEngine(
                detModel = PaddleDetModel { tensor, width, height ->
                    val input = OnnxTensor.createTensor(
                        env, FloatBuffer.wrap(tensor),
                        longArrayOf(1, 3, height.toLong(), width.toLong())
                    )
                    input.use {
                        detSession.run(mapOf(detSession.inputNames.first() to input)).use { res ->
                            val out = res[0] as OnnxTensor
                            val buf = out.floatBuffer
                            FloatArray(buf.remaining()).also { buf.get(it) }
                        }
                    }
                },
                recModel = PaddleRecModel { tensor, width ->
                    val input = OnnxTensor.createTensor(
                        env, FloatBuffer.wrap(tensor),
                        longArrayOf(1, 3, PaddleOcrEngine.REC_HEIGHT.toLong(), width.toLong())
                    )
                    input.use {
                        recSession.run(mapOf(recSession.inputNames.first() to input)).use { res ->
                            val out = res[0] as OnnxTensor
                            val shape = out.info.shape // [1, seqLen, numClasses]
                            val buf = out.floatBuffer
                            val data = FloatArray(buf.remaining()).also { buf.get(it) }
                            RecOutput(data, shape[1].toInt(), shape[2].toInt())
                        }
                    }
                },
                dictionary = Ctc.parseDictionary(dictText)
            )
            cached = built
            return built
        }
    }
}
