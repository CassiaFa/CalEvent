package fr.calevent.core.crop

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.image.OcrPreprocess
import fr.calevent.core.image.TableCells
import fr.calevent.core.parse.Orientation
import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextElement
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local-only: replays the app's automatic crop ([CropSuggestion], as `AutoCrop` does it) on the photos of the
 * reported problems in `data/issues/` (private, never committed): table search ([TableFrame]) on a 1600 px gray copy, text bounds
 * from a quick OCR pass on a 1024 px copy (Tesseract command line `--psm 11` standing in for the phone's engine),
 * then the suggestion. Prints the proposed frame per photo and draws it (green; candidates in blue, textures in
 * red, chosen table in magenta, text block in orange) on a copy in `data/issues/out/`. Skipped when the
 * folder does not exist.
 *
 * The older screenshots (the `.jpeg` files of `data/issues`, 920 x 2048) are used through the photo area they show ([SCREENSHOT_AREAS]).
 */
class AutoCropEval {

    private val root = File("../data/issues")

    /** Photo area (left, top, right, bottom in pixels) of the older app screenshots. */
    private val SCREENSHOT_AREAS = mapOf(
        "problem_auto_recrop.jpeg" to intArrayOf(194, 228, 738, 1414),
        "problem_auto_recrop-2.jpeg" to intArrayOf(194, 228, 738, 1414),
        "problem_auto_recrop-3.jpeg" to intArrayOf(194, 228, 738, 1414),
    )

    @Test
    fun suggestCrops() {
        assumeTrue(root.isDirectory, "no data/issues folder: nothing to evaluate")
        val out = File(root, "out").apply { mkdirs() }
        val inputs = File(root, "recadrage_play_v0.9.0").listFiles { f -> f.name.startsWith("photo") }.orEmpty().sortedBy { it.name } +
            SCREENSHOT_AREAS.keys.map { File(root, it) }.filter { it.isFile }
        val only = System.getenv("CROP_ONLY") ?: ""
        val report = StringBuilder()
        val photos = inputs.associate { f ->
            var img = ImageIO.read(f)
            SCREENSHOT_AREAS[f.name]?.let { (l, t, r, b) -> img = img.getSubimage(l, t, r - l, b - t) }
            f.name.substringBeforeLast('.') to img
        } + composites()
        for ((name, img) in photos.filter { it.key.startsWith(only) }) {
            val big = scaled(img, 1600)
            val start = System.nanoTime()
            val gray = gray(big)
            val candidates = TableFrame.candidates(gray, big.width, big.height)
            val gridMs = (System.nanoTime() - start) / 1_000_000
            val small = scaled(img, 1024)
            val text = tesseract(small)
            val textRect = TextBounds.suggestCrop(text)
            val table = TableFrame.choose(candidates, text)
            val rect = CropSuggestion.suggest(table, text)
            report.appendLine("$name (${gridMs} ms) table=${table?.fmt()} text=${textRect?.fmt()} lines=${text.lines.size} -> ${rect?.fmt()}")
            for (c in candidates.take(6)) report.appendLine("    ${c.rect.fmt()} h=${c.hLines} v=${c.vLines} x=${c.crossings} spacing=%.4f area=%.2f".format(c.spacing, c.area))
            draw(big, candidates, table, textRect, rect, File(out, "$name.png"))
        }
        println(report)
        File(out, "report.txt").writeText(report.toString())
    }

    /**
     * Replays the automatic upright step ([Orientation.detect]) on each reported photo and on the photos of
     * `data/small`, each turned by 0, 90, 180 and 270 degrees: quick reading of the whole 1024 px photo, then of its
     * central part of the 1600 px photo, cleaned, in each orientation. Prints the rotation chosen against the expected one.
     */
    /** Clockwise rotation that makes the photos of `data/small` upright (the others are upright as stored). */
    private val SMALL_TURNS = mapOf("IMG_2929" to 180, "IMG_2930" to 270, "IMG_2931" to 270)

    @Test
    fun orientations() {
        assumeTrue(root.isDirectory, "no data/issues folder: nothing to evaluate")
        // Clockwise rotation that makes each source upright.
        val sources = listOf(
            File(root, "recadrage_play_v0.9.0/photo1.jpeg") to 180,
            File(root, "recadrage_play_v0.9.0/photo2.jpeg") to 180,
            File(root, "recadrage_play_v0.9.0/photo3.jpeg") to 0,
        ) + File(root, "../small").listFiles { f -> f.name.endsWith(".jpg") }.orEmpty().sortedBy { it.name }
            .map { it to (SMALL_TURNS[it.nameWithoutExtension] ?: 0) }
        val only = System.getenv("CROP_ONLY") ?: ""
        val today = LocalDate.of(2026, 11, 24)
        val report = StringBuilder()
        var ok = 0
        var total = 0
        for ((f, truth) in sources.filter { it.first.name.startsWith(only) }) {
            val full = scaled(ImageIO.read(f), 1600)
            for (r in intArrayOf(0, 90, 180, 270)) {
                val big = rotated(full, r)
                val photo = scaled(big, 1024)
                val start = System.nanoTime()
                var reads = 0
                val upright = tesseract(photo)
                val scores = StringBuilder()
                val chosen = Orientation.detect(upright, today, ShiftCodeTable()) { deg ->
                    reads++
                    tesseract(cleaned(central(rotated(big, deg)))).also {
                        scores.append(" %d:%.1f/%.1f".format(deg, Orientation.score(it, today), Orientation.readableWords(it)))
                    }
                }
                val ms = (System.nanoTime() - start) / 1_000_000
                val expected = ((truth - r) % 360 + 360) % 360
                total++
                if (chosen == expected) ok++
                report.appendLine("%-14s turned %3d: chosen %3d expected %3d %s (%d extra reads, %d ms)%s".format(
                    f.nameWithoutExtension, r, chosen, expected, if (chosen == expected) "ok" else "WRONG", reads, ms, scores,
                ))
            }
        }
        report.appendLine("$ok / $total")
        println(report)
        File(root, "out").mkdirs()
        File(root, "out/orientation.txt").writeText(report.toString())
    }

    /** [img] in gray, cleaned by [OcrPreprocess] as the foss engine does before reading. */
    private fun cleaned(img: BufferedImage): BufferedImage {
        val px = OcrPreprocess.process(gray(img), img.width, img.height)
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_GRAY)
        out.raster.setPixels(0, 0, img.width, img.height, px)
        return out
    }

    /** Central part of [img] (70 % of each side), as the app reads it to compare orientations. */
    private fun central(img: BufferedImage): BufferedImage {
        val w = (img.width * 0.7).toInt()
        val h = (img.height * 0.7).toInt()
        return img.getSubimage((img.width - w) / 2, (img.height - h) / 2, w, h)
    }

    /** [img] turned clockwise by [degrees] (a multiple of 90). */
    private fun rotated(img: BufferedImage, degrees: Int): BufferedImage {
        if (degrees % 360 == 0) return img
        val quarter = degrees % 180 != 0
        val out = BufferedImage(if (quarter) img.height else img.width, if (quarter) img.width else img.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        val t = java.awt.geom.AffineTransform()
        t.translate(out.width / 2.0, out.height / 2.0)
        t.rotate(Math.toRadians(degrees.toDouble()))
        t.translate(-img.width / 2.0, -img.height / 2.0)
        g.drawImage(img, t, null)
        g.dispose()
        return out
    }

    /**
     * Photos rebuilt from the reported ones to stand for the earlier screenshots: photo3 turned a quarter turn and
     * placed on the wood floor of photo2 (strip tiled below and to the right), as in the "frame on the wood" report.
     */
    private fun composites(): Map<String, BufferedImage> {
        val p2 = File(root, "recadrage_play_v0.9.0/photo2.jpeg").takeIf { it.isFile }?.let { ImageIO.read(it) } ?: return emptyMap()
        val p3 = File(root, "recadrage_play_v0.9.0/photo3.jpeg").takeIf { it.isFile }?.let { ImageIO.read(it) } ?: return emptyMap()
        val wood = p2.getSubimage(0, (p2.height * 0.955).toInt(), p2.width, (p2.height * 0.045).toInt())
        val out = BufferedImage(1536, 2048, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        var y = 0
        while (y < out.height) { g.drawImage(wood, 0, y, null); y += wood.height }
        // photo3 turned clockwise, scaled to 1100 x 1467, at the top left.
        val t = java.awt.geom.AffineTransform()
        t.translate(1100.0, 0.0)
        t.scale(1100.0 / p3.height, 1467.0 / p3.width)
        t.rotate(Math.PI / 2)
        g.drawImage(p3, t, null)
        g.dispose()
        return mapOf("wood_photo3" to out)
    }

    private fun NormalizedRect.fmt() = "[%.2f %.2f %.2f %.2f]".format(left, top, right, bottom)

    private fun scaled(src: BufferedImage, side: Int): BufferedImage {
        val s = minOf(1.0, side.toDouble() / max(src.width, src.height))
        val w = max(1, (src.width * s).roundToInt())
        val h = max(1, (src.height * s).roundToInt())
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    private fun gray(img: BufferedImage): IntArray {
        val argb = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        return IntArray(argb.size) { i ->
            val p = argb[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
    }

    /** Lines read by the Tesseract command line on [img] (`--psm 11`, sparse text). */
    private fun tesseract(img: BufferedImage): RecognizedText {
        val file = File.createTempFile("autocrop", ".png")
        try {
            ImageIO.write(img, "png", file)
            val pb = ProcessBuilder("tesseract", file.absolutePath, "stdout", "-l", "fra", "--psm", "11", "tsv")
            pb.environment()["OMP_THREAD_LIMIT"] = "2"
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val p = pb.start()
            val text = p.inputStream.bufferedReader().readText()
            check(p.waitFor(5, TimeUnit.MINUTES) && p.exitValue() == 0) { "tesseract failed" }
            val words = LinkedHashMap<List<Int>, MutableList<TextElement>>()
            for (row in text.lineSequence().drop(1)) {
                val c = row.split('\t')
                if (c.size < 12 || c[0] != "5" || c[11].isBlank()) continue
                val (l, t, ww, hh) = c.subList(6, 10).map { it.toInt() }
                words.getOrPut(c.subList(1, 5).map { it.toInt() }) { ArrayList() } += TextElement(
                    c[11].trim(), Box(l.toDouble(), t.toDouble(), (l + ww).toDouble(), (t + hh).toDouble()),
                    (c[10].toDouble() / 100.0).coerceIn(0.0, 1.0),
                )
            }
            return RecognizedText(img.width, img.height, words.values.map { TableCells.line(it) })
        } finally {
            file.delete()
        }
    }

    private fun draw(img: BufferedImage, candidates: List<TableFrame.Candidate>, table: NormalizedRect?, text: NormalizedRect?, rect: NormalizedRect?, f: File) {
        val s = 0.5
        val copy = scaled(img, (max(img.width, img.height) * s).toInt())
        val g = copy.createGraphics()
        g.stroke = BasicStroke(1f)
        fun box(r: NormalizedRect, c: Color, width: Float) {
            g.color = c
            g.stroke = BasicStroke(width)
            g.drawRect((r.left * copy.width).toInt(), (r.top * copy.height).toInt(), ((r.right - r.left) * copy.width).toInt(), ((r.bottom - r.top) * copy.height).toInt())
        }
        candidates.forEach { box(it.rect, if (it.texture) Color.RED else Color.BLUE, 1f) }
        table?.let { box(it, Color.MAGENTA, 2f) }
        text?.let { box(it, Color.ORANGE, 2f) }
        rect?.let { box(it, Color.GREEN, 4f) }
        g.dispose()
        ImageIO.write(copy, "png", f)
    }
}
