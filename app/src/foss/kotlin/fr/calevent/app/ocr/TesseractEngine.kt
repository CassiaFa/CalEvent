package fr.calevent.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import fr.calevent.core.image.OcrPreprocess
import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextElement
import fr.calevent.core.parse.TextLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-device text recognition with Tesseract (free software, Apache-2.0) and the `fra` model
 * bundled in assets: no download, no network. Not thread-safe by itself, so calls are serialized.
 */
internal class TesseractEngine(private val context: Context) : OcrEngine {

    private val mutex = Mutex()
    private var api: TessBaseAPI? = null

    override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): RecognizedText =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val quarterTurn = rotationDegrees % 180 != 0
                val width = if (quarterTurn) bitmap.height else bitmap.width
                val height = if (quarterTurn) bitmap.width else bitmap.height
                val prepared = prepare(bitmap, rotationDegrees, width, height)
                try {
                    val scale = width.toDouble() / prepared.width
                    readLines(engine(), prepared, scale, width, height)
                } finally {
                    prepared.recycle()
                }
            }
        }

    override fun close() {
        api?.recycle()
        api = null
    }

    private fun engine(): TessBaseAPI {
        api?.let { return it }
        val dataDir = TessData.install(context)
        val tess = TessBaseAPI()
        if (!tess.init(dataDir.absolutePath, LANGUAGE)) {
            tess.recycle()
            error("Tesseract init failed")
        }
        // PSM_SPARSE_TEXT: a planning is a table (cells, codes like M06, short numbers), not flowing
        // text. PSM_AUTO tends to merge columns into paragraphs or drop isolated cells; sparse mode
        // finds as many separate text fragments as possible, whatever their order, and the parser
        // regroups them from their boxes anyway.
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        api = tess
        return tess
    }

    /**
     * Rotates upright, converts to grayscale and downscales so the long side is at most [MAX_SIDE], then
     * evens out the lighting and erases the table grid ([OcrPreprocess]): tinted cells and ruled lines are
     * what makes Tesseract misread plannings. Runs on the caller's background dispatcher.
     */
    private fun prepare(src: Bitmap, rotationDegrees: Int, width: Int, height: Int): Bitmap {
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(width, height)).toFloat()
        val outW = max(1, (width * scale).roundToInt())
        val outH = max(1, (height * scale).roundToInt())
        val matrix = Matrix().apply {
            // Rotate around the origin, then shift back into the positive quadrant.
            postRotate(rotationDegrees.toFloat())
            when (((rotationDegrees % 360) + 360) % 360) {
                90 -> postTranslate(src.height.toFloat(), 0f)
                180 -> postTranslate(src.width.toFloat(), src.height.toFloat())
                270 -> postTranslate(0f, src.width.toFloat())
            }
            postScale(scale, scale)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(src, matrix, paint)
        cleanUp(out)
        return out
    }

    /** Replaces the pixels of the gray [bitmap] by their [OcrPreprocess] version, in place. */
    private fun cleanUp(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        // The bitmap is already gray: any channel (here blue) is the gray level.
        val gray = IntArray(w * h) { argb[it] and 0xFF }
        val clean = OcrPreprocess.process(gray, w, h)
        for (i in argb.indices) argb[i] = OPAQUE or (clean[i] * 0x010101)
        bitmap.setPixels(argb, 0, w, 0, 0, w, h)
    }

    /** Reads words grouped by text line; boxes are scaled by [scale] back to the upright image. */
    private fun readLines(tess: TessBaseAPI, image: Bitmap, scale: Double, width: Int, height: Int): RecognizedText {
        tess.setImage(image)
        try {
            tess.getUTF8Text() // triggers the recognition; the iterator reads its result
            val it = tess.resultIterator ?: return RecognizedText(width, height, emptyList())
            val lines = ArrayList<TextLine>()
            var words = ArrayList<TextElement>()
            fun flush() {
                if (words.isEmpty()) return
                val box = words.map { w -> w.box }.reduce { a, b -> a union b }
                val confs = words.mapNotNull { w -> w.confidence }
                lines += TextLine(
                    text = words.joinToString(" ") { w -> w.text },
                    box = box,
                    confidence = confs.takeIf { c -> c.isNotEmpty() }?.average(),
                    elements = words,
                )
                words = ArrayList()
            }
            try {
                it.begin()
                do {
                    if (it.isAtBeginningOf(PageIteratorLevel.RIL_TEXTLINE)) flush()
                    val text = it.getUTF8Text(PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                    val r = it.getBoundingRect(PageIteratorLevel.RIL_WORD)
                    if (text.isNotEmpty() && r != null) {
                        words += TextElement(
                            text = text,
                            box = Box(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale),
                            confidence = (it.confidence(PageIteratorLevel.RIL_WORD) / 100.0).coerceIn(0.0, 1.0),
                        )
                    }
                } while (it.next(PageIteratorLevel.RIL_WORD))
                flush()
            } finally {
                it.delete()
            }
            return RecognizedText(width, height, lines)
        } finally {
            tess.clear()
        }
    }

    private companion object {
        const val LANGUAGE = "fra"
        const val MAX_SIDE = 2000
        const val OPAQUE = 0xFF000000.toInt()
    }
}

/** Copies the bundled `fra.traineddata` to `filesDir/tessdata`, once per [VERSION]. */
private object TessData {
    /** Bump when the asset changes, so installed copies are refreshed. */
    const val VERSION = "fra-tessdata_fast-1"
    private const val FILE = "fra.traineddata"

    /** Returns the directory that contains `tessdata/` (what [TessBaseAPI.init] expects). */
    fun install(context: Context): File {
        val root = context.filesDir
        val dir = File(root, "tessdata").apply { mkdirs() }
        val target = File(dir, FILE)
        val marker = File(dir, ".version")
        val upToDate = target.isFile && marker.isFile && marker.readText() == VERSION
        if (!upToDate) {
            val tmp = File(dir, "$FILE.tmp")
            context.assets.open("tessdata/$FILE").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "Cannot install $FILE" }
            marker.writeText(VERSION)
        }
        return root
    }
}
