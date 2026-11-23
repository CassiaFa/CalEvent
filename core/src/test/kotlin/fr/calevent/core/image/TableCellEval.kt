package fr.calevent.core.image

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.RecognizedTextJson
import fr.calevent.core.parse.TextElement
import fr.calevent.core.parse.TextLine
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Local-only: replays the foss app's cell-by-cell table reading on the PC, with the Tesseract command line in
 * place of Tesseract4Android (same `fra` model). For each prepared photo of `data/ocr-eval/img` (private, never
 * committed): [OcrPreprocess.prepare] (cleaning + [GridDetector]), then, when a grid is found, [TableCells.crops]
 * read with `tesseract --psm 6` (one process for all the crops, like the app's single engine instance), the
 * code-like words read again alone with `--psm 7` and a code whitelist ([TableCells.applyRereads]), and the area
 * outside the table read with `--psm 11`; without a grid, the whole cleaned page with `--psm 11` (as before).
 * Writes `data/ocr-eval/<CELLS_OUT or "cells">/<photo>_<deg>.json` (compared by [fr.calevent.core.parse.TableCellReport]
 * and `OcrEvalReport`), `timings.txt`, and per image a grid overlay and the candidate lines in `data/ocr-eval/cells-debug/`.
 * Skipped when the folder does not exist.
 *
 * Env: `CELLS_PSM` = `6` (default) or `auto` (psm 7 for one-line cells: measured identical, not kept);
 * `CELLS_WL=0` disables the second reading; `CELLS_ONLY` = comma-separated name prefixes.
 */
class TableCellEval {

    private val root = File("../data/ocr-eval")

    @Test
    fun readCells() {
        val input = File(root, "img")
        assumeTrue(input.isDirectory, "no data/ocr-eval/img folder: nothing to read")
        val outName = System.getenv("CELLS_OUT") ?: "cells"
        val psmMode = System.getenv("CELLS_PSM") ?: "6"
        val only = System.getenv("CELLS_ONLY") ?: ""
        val outDir = File(root, outName).apply { mkdirs() }
        val debugDir = File(root, "cells-debug").apply { mkdirs() }
        val work = File(root, "cells-tmp").apply { mkdirs() }
        val files = input.listFiles { f -> f.name.endsWith(".png") && only.split(',').any { f.name.startsWith(it) } }!!.sortedBy { it.name }
        val timings = StringBuilder()
        for (f in files) {
            val start = System.nanoTime()
            val img = ImageIO.read(f)
            val w = img.width
            val h = img.height
            val gray = IntArray(w * h)
            img.raster.getPixels(0, 0, w, h, gray)
            val trace = StringBuilder()
            GridDetector.trace = { trace.appendLine(it) }
            val prepared = try { OcrPreprocess.prepare(gray, w, h) } finally { GridDetector.trace = null }
            File(debugDir, f.name.removeSuffix(".png") + ".lines.txt").writeText(trace.toString())
            val grid = prepared.grid
            val text: RecognizedText
            var nCrops = 0
            if (grid == null) {
                val page = File(work, "page.png").also { writeGray(prepared.pixels, w, h, it) }
                text = RecognizedText(w, h, tesseract(listOf(page), "11").getOrElse(1) { emptyList() })
            } else {
                val crops = TableCells.crops(prepared.pixels, w, h, grid)
                nCrops = crops.size
                work.listFiles()?.forEach { it.delete() }
                val outside = File(work, "outside.png").also { writeGray(TableCells.outside(prepared.pixels, w, h, grid), w, h, it) }
                val outsideLines = tesseract(listOf(outside), "11").getOrElse(1) { emptyList() }
                val cropFiles = crops.mapIndexed { i, c -> File(work, "c%04d.png".format(i)).also { writeGray(c.pixels, c.width, c.height, it) } }
                val read = ArrayList<Pair<CellCrop, List<TextLine>>>()
                val groups = if (psmMode == "auto") crops.indices.groupBy { if (crops[it].textLines == 1) "7" else "6" }
                else mapOf(psmMode to crops.indices.toList())
                for ((psm, idx) in groups) {
                    val pages = tesseract(idx.map { cropFiles[it] }, psm)
                    idx.forEachIndexed { k, i -> read += crops[i] to pages.getOrElse(k + 1) { emptyList() } }
                }
                val final = if (System.getenv("CELLS_WL") != "0") rereadCodes(read, work) else read
                text = TableCells.assemble(w, h, outsideLines, final)
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            File(outDir, f.name.removeSuffix(".png") + ".json").writeText(RecognizedTextJson.write(text))
            timings.appendLine("${f.name} grid=${grid?.let { "${it.rows}x${it.cols} cells=${it.cells.size}" } ?: "none"} crops=$nCrops ${ms}ms")
            if (grid != null) drawDebug(prepared.pixels, w, h, grid, File(debugDir, f.name))
        }
        File(outDir, "timings.txt").writeText(timings.toString())
    }

    /** Second reading of the code-like words ([TableCells.looksLikeMisreadCode]) with [TableCells.CODE_CHARS] only. */
    private fun rereadCodes(read: List<Pair<CellCrop, List<TextLine>>>, work: File): List<Pair<CellCrop, List<TextLine>>> {
        val requests = ArrayList<Pair<Int, IntBox>>()
        read.forEachIndexed { ci, (crop, lines) ->
            lines.flatMap { it.elements }.filter { TableCells.looksLikeMisreadCode(it.text) }
                .forEach { requests += ci to TableCells.rereadArea(crop, it.box) }
        }
        if (requests.isEmpty()) return read
        val files = requests.mapIndexed { k, (ci, a) ->
            val crop = read[ci].first
            val px = IntArray(a.width * a.height) { i -> crop.pixels[(a.top + i / a.width) * crop.width + a.left + i % a.width] }
            File(work, "r%04d.png".format(k)).also { writeGray(px, a.width, a.height, it) }
        }
        val pages = tesseract(files, "7", listOf("-c", "tessedit_char_whitelist=${TableCells.CODE_CHARS}"))
        val results = HashMap<Pair<Int, IntBox>, TextElement>()
        requests.forEachIndexed { k, req ->
            val words = pages[k + 1].orEmpty().flatMap { it.elements }
            if (words.isNotEmpty()) results[req] = TextElement(words.joinToString("") { it.text }, words.first().box, words.minOf { it.confidence ?: 0.0 })
        }
        return read.mapIndexed { ci, (crop, lines) -> crop to TableCells.applyRereads(crop, lines) { area -> results[ci to area] } }
    }

    /** Runs Tesseract on [images] (one process); returns the lines per page number (1-based). */
    private fun tesseract(images: List<File>, psm: String, extra: List<String> = emptyList()): Map<Int, List<TextLine>> {
        if (images.isEmpty()) return emptyMap()
        val list = File(images.first().parentFile, "list-$psm.txt")
        list.writeText(images.joinToString("\n", postfix = "\n") { it.absolutePath })
        val pb = ProcessBuilder(listOf("tesseract", list.absolutePath, "stdout", "-l", "fra", "--psm", psm) + extra + "tsv")
        pb.environment()["OMP_THREAD_LIMIT"] = "2"
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(10, TimeUnit.MINUTES) && p.exitValue() == 0) { "tesseract failed" }
        // level page block par line word left top width height conf text
        val words = LinkedHashMap<List<Int>, MutableList<TextElement>>()
        for (row in out.lineSequence().drop(1)) {
            val c = row.split('\t')
            if (c.size < 12 || c[0] != "5" || c[11].isBlank()) continue
            val (l, t, ww, hh) = c.subList(6, 10).map { it.toInt() }
            val key = c.subList(1, 5).map { it.toInt() }
            words.getOrPut(key) { ArrayList() } += TextElement(
                c[11].trim(), Box(l.toDouble(), t.toDouble(), (l + ww).toDouble(), (t + hh).toDouble()),
                (c[10].toDouble() / 100.0).coerceIn(0.0, 1.0),
            )
        }
        return words.entries.groupBy({ it.key[0] }, { TableCells.line(it.value) })
    }

    private fun writeGray(px: IntArray, w: Int, h: Int, f: File) {
        val image = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        image.raster.setPixels(0, 0, w, h, px)
        ImageIO.write(image, "png", f)
    }

    private fun drawDebug(px: IntArray, w: Int, h: Int, grid: TableGrid, f: File) {
        val s = 0.5
        val img = BufferedImage((w * s).toInt(), (h * s).toInt(), BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val v = px[(y / s).toInt() * w + (x / s).toInt()]
            img.setRGB(x, y, v * 0x010101)
        }
        val g = img.createGraphics()
        g.stroke = BasicStroke(1f)
        for (c in grid.cells) {
            g.color = Color.BLUE
            val xs = listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft).map { (it.x * s).toInt() }.toIntArray()
            val ys = listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft).map { (it.y * s).toInt() }.toIntArray()
            g.drawPolygon(xs, ys, 4)
            g.color = Color.RED
            g.drawRect((c.inner.left * s).toInt(), (c.inner.top * s).toInt(), (c.inner.width * s).toInt(), (c.inner.height * s).toInt())
        }
        g.dispose()
        ImageIO.write(img, "png", f)
    }
}
