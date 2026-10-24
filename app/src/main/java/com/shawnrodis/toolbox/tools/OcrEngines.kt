package com.shawnrodis.toolbox.tools

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * OCR engines for PDF Tools:
 *
 *  - "Local Engine — PaddleOCR (Offline)" is true PaddleOCR (PP-OCRv6
 *    tiny detection + recognition models over ONNX Runtime, bundled as
 *    app assets) — the same engine and the same model files the web
 *    edition runs via ppu-paddle-ocr. See PaddleOcr.kt for the
 *    pipeline port. Results are LINE-level: text + box in
 *    rendered-image pixels, the shape the searchable-PDF layer draws.
 *  - "AI Vision OCR — Cloud AI API Key" is callAiVisionOcrWithFailover():
 *    POST https://generativelanguage.googleapis.com/v1beta/models/
 *    gemini-3.6-flash:generateContent?key=<key> with
 *    {contents:[{parts:[{text:"Extract text line by line accurately."},
 *    {inline_data:{mime_type:"image/png", data:<base64>}}]}]},
 *    trying keys in order (Key 1, then Key 2) and returning
 *    candidates[0].content.parts[0].text. On total failure it throws
 *    "AI OCR failed on all saved keys. Details: <last error>".
 *
 * SECURITY: keys are ONLY the user-entered Settings values (the source
 * falls back to keys built into the HTML; the native app deliberately has
 * none — see README). The key travels in the request URL exactly as the
 * source sends it, and is never logged or embedded in error strings
 * (HTTP errors surface status/message only, as in the source).
 */

/** sanitizeText(), verbatim: curly quotes straightened, non-printables cut. */
fun sanitizeOcrText(str: String?): String {
    if (str == null) return ""
    return str
        .replace('“', '"').replace('”', '"')
        .replace('‘', '\'').replace('’', '\'')
        .replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF]"), "")
}

/**
 * One recognised text segment with its box, in rendered-image pixels.
 * Since the PaddleOCR swap the local engine reports whole LINES, so a
 * "word" here is one line of text with the line's box (the consumer
 * draws each entry at its box, exactly as the web edition now does).
 */
data class OcrWord(
    val text: String,
    val x0: Int, val y0: Int, val x1: Int, val y1: Int
)

/** Local engine: PaddleOCR on-device recognition, line level. */
fun recognizeWordsLocal(context: Context, bitmap: Bitmap): List<OcrWord> {
    return PaddleOcrRuntime.recognize(context, bitmap).map { line ->
        OcrWord(line.text, line.box.x, line.box.y, line.box.x + line.box.width, line.box.y + line.box.height)
    }
}

/** Local engine plain text: lines grouped and joined with newlines, as the engine reports them. */
fun recognizeTextLocal(context: Context, bitmap: Bitmap): String {
    val results = PaddleOcrRuntime.recognize(context, bitmap)
    return groupResultsByLine(results)
        .joinToString("\n") { line -> line.joinToString(" ") { it.text } }
}

/** blobToBase64(): PNG bytes -> base64 payload (the data after the comma). */
fun bitmapToBase64Png(bitmap: Bitmap): String {
    val bos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

/**
 * callAiVisionOcrWithFailover(base64Data) with user-entered keys only.
 * Throws with the source's message shape when every key fails, and with a
 * Settings hint when no key exists at all (native-only case).
 */
fun callAiVisionOcrWithFailover(base64Data: String, userKeys: List<String>): String {
    val keysToTry = userKeys.map { it.trim() }.filter { it.isNotEmpty() }
    if (keysToTry.isEmpty()) {
        throw Exception(
            "No Gemini API key saved. Add your Gemini API key in Settings to use AI Vision OCR."
        )
    }
    var lastError: String? = null
    for (key in keysToTry) {
        try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-3.6-flash:generateContent?key=$key"
            )
            val body = JSONObject().put(
                "contents", JSONArray().put(
                    JSONObject().put(
                        "parts", JSONArray()
                            .put(JSONObject().put("text", "Extract text line by line accurately."))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().put("mime_type", "image/png").put("data", base64Data)
                                )
                            )
                    )
                )
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val res = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                return res.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text") ?: ""
            }
            val code = conn.responseCode
            val errText = try {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (e: Exception) {
                ""
            }
            conn.disconnect()
            lastError = try {
                JSONObject(errText).optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotEmpty() } ?: "HTTP $code"
            } catch (e: Exception) {
                "HTTP $code"
            }
        } catch (e: IOException) {
            lastError = e.message
        }
    }
    throw Exception("AI OCR failed on all saved keys. Details: $lastError")
}
