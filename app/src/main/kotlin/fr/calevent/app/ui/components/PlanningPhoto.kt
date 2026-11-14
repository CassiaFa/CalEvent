package fr.calevent.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Which fake paper to draw. */
enum class PlanningPhotoVariant {
    /** Printed weekly schedule: ruled lines and a day column. */
    Weekly,

    /** Team schedule with codes: dense grid of small cells. */
    TeamCodes,

    /** Wall calendar: dark header band over a month grid (home thumbnail). */
    WallCalendar,
}

// Colours of a photographed sheet of paper: scene content, not UI theme, so they are fixed in both themes.
private val Paper = Color(0xFFF6F3EA)
private val PaperLine = Color(0xFFCFC8B8)
private val PaperInk = Color(0xFFB9B2A2)
private val PaperHeader = Color(0xFF2E3E4E)

/**
 * Fake planning photo drawn in a Canvas (cream paper + grid), used until the camera is wired.
 * Purely decorative (hidden from accessibility). The caller sets the size, e.g. `Modifier.size(52.dp, 56.dp)`.
 */
@Composable
fun PlanningPhotoPlaceholder(variant: PlanningPhotoVariant, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        drawRect(Paper)
        when (variant) {
            PlanningPhotoVariant.Weekly -> drawWeekly()
            PlanningPhotoVariant.TeamCodes -> drawGrid(cols = 8, rows = 12, headerRow = false)
            PlanningPhotoVariant.WallCalendar -> {
                val band = size.height * 0.16f
                drawRect(PaperHeader, size = Size(size.width, band))
                drawGrid(cols = 7, rows = 6, top = band, headerRow = false)
            }
        }
    }
}

private fun DrawScope.drawWeekly() {
    val rows = 14
    val rowH = size.height / rows
    val line = (size.minDimension / 120f).coerceAtLeast(1f)
    val split = size.width * 0.3f
    for (i in 1 until rows) drawRect(PaperLine, Offset(0f, i * rowH), Size(size.width, line))
    drawRect(PaperLine, Offset(split, 0f), Size(line, size.height))
    val widths = floatArrayOf(0.55f, 0.4f, 0.65f, 0.5f, 0.35f)
    for (i in 0 until rows) {
        drawRoundRect(PaperInk, Offset(size.width * 0.06f, i * rowH + rowH * 0.3f), Size(split * 0.6f, rowH * 0.4f), CornerRadius(rowH * 0.2f))
        val w = (size.width - split) * widths[i % widths.size] * 0.9f
        drawRoundRect(PaperInk, Offset(split + size.width * 0.05f, i * rowH + rowH * 0.3f), Size(w, rowH * 0.4f), CornerRadius(rowH * 0.2f))
    }
}

private fun DrawScope.drawGrid(cols: Int, rows: Int, top: Float = 0f, headerRow: Boolean) {
    val cellW = size.width / cols
    val cellH = (size.height - top) / rows
    val line = (size.minDimension / 120f).coerceAtLeast(1f)
    for (r in 0..rows) drawRect(PaperLine, Offset(0f, top + r * cellH), Size(size.width, line))
    for (cIdx in 0..cols) drawRect(PaperLine, Offset(cIdx * cellW, top), Size(line, size.height - top))
    if (headerRow) drawRect(PaperLine, Offset(0f, top), Size(size.width, cellH))
    for (r in 0 until rows) for (cIdx in 0 until cols) {
        if ((r * 5 + cIdx * 3) % 4 == 0) continue
        drawRoundRect(
            PaperInk,
            Offset(cIdx * cellW + cellW * 0.2f, top + r * cellH + cellH * 0.35f),
            Size(cellW * 0.6f, cellH * 0.3f),
            CornerRadius(cellH * 0.15f),
        )
    }
}
