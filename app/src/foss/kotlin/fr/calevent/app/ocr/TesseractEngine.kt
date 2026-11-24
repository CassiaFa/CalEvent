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
import fr.calevent.core.image.CellCrop
import fr.calevent.core.image.OcrPreprocess
import fr.calevent.core.image.TableCells
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
 *
 * The photo is cleaned first ([OcrPreprocess]). When it shows a ruled table, each cell is read on its own
 * ([TableCells]): on a whole page Tesseract misses a third of the cells of a schedule. Otherwise (wall calendar
 * without rules, list) the whole page is read in sparse mode.
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
                val (gray, w, h) = uprightGray(bitmap, rotationDegrees, width, height)
                // Evens out the lighting and erases the table grid; finds the grid first, when there is one.
                val prepared = OcrPreprocess.prepare(gray, w, h)
                val tess = engine()
                val grid = prepared.grid
                val text = if (grid == null) {
                    RecognizedText(w, h, readPage(tess, prepared.pixels, w, h))
                } else {
                    // A ruled table: each cell alone (Tesseract misses many cells of a whole page), plus the
                    // area around the table (title, month) read as a page.
                    val outside = readPage(tess, TableCells.outside(prepared.pixels, w, h, grid), w, h)
                    val cells = TableCells.crops(prepared.pixels, w, h, grid).map { crop -> crop to readCell(tess, crop) }
                    TableCells.assemble(w, h, outside, cells)
                }
                text.scaled(width.toDouble() / w, width, height)
            }
        }

    /** The cleaned page read in sparse mode, without the cell-by-cell reading of a table (much faster). */
    override suspend fun recognizeQuick(bitmap: Bitmap): RecognizedText =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val (gray, w, h) = uprightGray(bitmap, 0, bitmap.width, bitmap.height)
                val clean = OcrPreprocess.process(gray, w, h)
                RecognizedText(w, h, readPage(engine(), clean, w, h)).scaled(bitmap.width.toDouble() / w, bitmap.width, bitmap.height)
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
        api = tess
        return tess
    }

    /**
     * Rotates upright, converts to grayscale and downscales so the long side is at most [MAX_SIDE].
     * Returns the gray levels (0..255, row-major) and the size. Runs on the caller's background dispatcher.
     */
    private fun uprightGray(src: Bitmap, rotationDegrees: Int, width: Int, height: Int): Triple<IntArray, Int, Int> {
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
        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            Canvas(out).drawBitmap(src, matrix, paint)
            val argb = IntArray(outW * outH)
            out.getPixels(argb, 0, outW, 0, 0, outW, outH)
            // The bitmap is gray: any channel (here blue) is the gray level.
            return Triple(IntArray(argb.size) { argb[it] and 0xFF }, outW, outH)
        } finally {
            out.recycle()
        }
    }

    /** Whole page, sparse text: a planning is a table, not flowing text (see [readLines]). */
    private fun readPage(tess: TessBaseAPI, gray: IntArray, w: Int, h: Int): List<TextLine> {
        // PSM_SPARSE_TEXT: PSM_AUTO tends to merge columns into paragraphs or drop isolated cells; sparse mode
        // finds as many separate text fragments as possible, whatever their order, and the parser regroups
        // them from their boxes anyway.
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        return read(tess, gray, w, h)
    }

    /**
     * One table cell as a block of text, then a second reading, restricted to code characters, of the words
     * that look like misread codes ("MoO2" for "M02"); the first reading's confidence is kept.
     */
    private fun readCell(tess: TessBaseAPI, crop: CellCrop): List<TextLine> {
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        val lines = read(tess, crop.pixels, crop.width, crop.height)
        if (lines.none { l -> l.elements.any { TableCells.looksLikeMisreadCode(it.text) } }) return lines
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
        tess.setVariable(WHITELIST, TableCells.CODE_CHARS)
        try {
            return TableCells.applyRereads(crop, lines) { area ->
                val px = IntArray(area.width * area.height) { i ->
                    crop.pixels[(area.top + i / area.width) * crop.width + area.left + i % area.width]
                }
                val words = read(tess, px, area.width, area.height).flatMap { it.elements }
                if (words.isEmpty()) null else TextElement(words.joinToString("") { it.text }, words.first().box)
            }
        } finally {
            tess.setVariable(WHITELIST, "")
        }
    }

    /** Reads [gray] with the current page segmentation mode: words grouped by text line, in image pixels. */
    private fun read(tess: TessBaseAPI, gray: IntArray, w: Int, h: Int): List<TextLine> {
        tess.setImage(ByteArray(gray.size) { gray[it].toByte() }, w, h, 1, w)
        try {
            tess.getUTF8Text() // triggers the recognition; the iterator reads its result
            val it = tess.resultIterator ?: return emptyList()
            val lines = ArrayList<TextLine>()
            var words = ArrayList<TextElement>()
            fun flush() {
                if (words.isEmpty()) return
                lines += TableCells.line(words)
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
                            box = Box(r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble()),
                            confidence = (it.confidence(PageIteratorLevel.RIL_WORD) / 100.0).coerceIn(0.0, 1.0),
                        )
                    }
                } while (it.next(PageIteratorLevel.RIL_WORD))
                flush()
            } finally {
                it.delete()
            }
            return lines
        } finally {
            tess.clear()
        }
    }

    /** Same text with every box multiplied by [scale] (prepared image -> upright photo of [width] x [height]). */
    private fun RecognizedText.scaled(scale: Double, width: Int, height: Int): RecognizedText {
        fun Box.times() = Box(left * scale, top * scale, right * scale, bottom * scale)
        return RecognizedText(width, height, lines.map { l ->
            l.copy(box = l.box.times(), elements = l.elements.map { e -> e.copy(box = e.box.times()) })
        })
    }

    private companion object {
        const val LANGUAGE = "fra"
        const val MAX_SIDE = 2000
        const val WHITELIST = "tessedit_char_whitelist"
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
