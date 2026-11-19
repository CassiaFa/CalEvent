package fr.calevent.core.image

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Local-only: runs [OcrPreprocess] on the prepared gray images of `data/ocr-eval/img` (private, never committed)
 * and writes `data/ocr-eval/prep-kotlin/<name>.png`, plus the time per image in `prep-kotlin/timings.txt`.
 * Driven by `tools/ocr-eval/kotlin-prep.sh`. Skipped when the folder does not exist.
 */
class OcrPreprocessEval {

    private val root = File("../data/ocr-eval")

    @Test
    fun prepareImages() {
        val input = File(root, "img")
        assumeTrue(input.isDirectory, "no data/ocr-eval/img folder: nothing to prepare")
        val outDir = File(root, "prep-kotlin").apply { mkdirs() }
        val files = input.listFiles { f -> f.name.endsWith(".png") }!!.sortedBy { it.name }
        // Warm up the JIT so that the timings are representative.
        files.firstOrNull()?.let { process(it) }
        val timings = StringBuilder()
        for (f in files) {
            val start = System.nanoTime()
            val (out, w, h) = process(f)
            val ms = (System.nanoTime() - start) / 1_000_000
            val image = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
            image.raster.setPixels(0, 0, w, h, out)
            ImageIO.write(image, "png", File(outDir, f.name))
            timings.appendLine("${f.name} ${w}x$h ${ms}ms")
        }
        File(outDir, "timings.txt").writeText(timings.toString())
    }

    private fun process(f: File): Triple<IntArray, Int, Int> {
        val img = ImageIO.read(f)
        val w = img.width
        val h = img.height
        val gray = IntArray(w * h)
        img.raster.getPixels(0, 0, w, h, gray) // 8-bit gray source: one band
        return Triple(OcrPreprocess.process(gray, w, h), w, h)
    }
}
