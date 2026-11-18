package fr.calevent.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icon set drawn from the mockup SVGs: 24 viewBox, stroke 2, round caps and joins, no fill.
 * Strokes are black and meant to be recoloured with `Icon(tint = ...)`.
 * Use `Icon(CalEventIcons.Camera, contentDescription = null)`; give a description only
 * when the icon is the sole content of a button.
 */
object CalEventIcons {
    val Camera = icon("Camera", paths = arrayOf("M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f)))
    val Gallery = icon(
        "Gallery",
        paths = arrayOf(
            "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            circle(9f, 10f, 2f),
            "M21 16l-5-5-9 9",
        ),
    )
    val Settings = icon("Settings", paths = arrayOf("M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f)))
    val Check = icon("Check", paths = arrayOf("M5 12.5l4.5 4.5L19 7.5"))

    /** Check with a heavier stroke (3), for the checked box of a slot card. */
    val CheckBold = icon("CheckBold", strokeWidth = 3f, paths = arrayOf("M5 12.5l4.5 4.5L19 7.5"))
    val Warning = icon("Warning", paths = arrayOf("M12 3.5l9.5 16.5h-19z", "M12 10v4M12 17v.01"))
    val EyeOff = icon(
        "EyeOff",
        paths = arrayOf(
            "M3 3l18 18",
            "M10.6 5.1A10 10 0 0 1 12 5c5.5 0 9 5.5 9.5 7a13 13 0 0 1-2.6 3.6M6.6 6.6C4.3 8 2.9 10.4 2.5 12c.5 1.5 4 7 9.5 7a9.6 9.6 0 0 0 4.4-1.1",
        ),
    )
    val ShieldOffline = icon("ShieldOffline", paths = arrayOf("M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z", "M9 12l2 2 4-4"))
    val Info = icon("Info", paths = arrayOf(circle(12f, 12f, 9f), "M12 11v6M12 7.5v.01"))
    val Error = icon("Error", paths = arrayOf(circle(12f, 12f, 9f), "M8 8l8 8M16 8l-8 8"))
    val RotateRight = icon("RotateRight", paths = arrayOf("M21 4v5h-5", "M20.5 9A8.5 8.5 0 1 0 19 15.5"))

    /** Mirror image of [RotateRight]. */
    val RotateLeft = icon("RotateLeft", paths = arrayOf("M3 4v5h5", "M3.5 9A8.5 8.5 0 1 1 5 15.5"))
    val Crop = icon("Crop", paths = arrayOf("M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2"))
    val Back = icon("Back", paths = arrayOf("M19 12H5", "M11 6l-6 6 6 6"))
    val Close = icon("Close", paths = arrayOf("M6 6l12 12M18 6L6 18"))
    val Flash = icon("Flash", paths = arrayOf("M13 2L4 14h7l-1 8 9-12h-7z"))
    val Calendar = icon(
        "Calendar",
        paths = arrayOf("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 3v4M16 3v4M3 10h18"),
    )

    /** A sheet of paper with text lines (the photographed schedule). */
    val Photo = icon("Photo", paths = arrayOf("M6 3h12v18H6z", "M9 8h6M9 12h6M9 16h3"))
    val ChevronLeft = icon("ChevronLeft", paths = arrayOf("M15 5l-7 7 7 7"))
    val Clock = icon("Clock", paths = arrayOf(circle(12f, 12f, 9f), "M12 7v5l3 2"))
    val Trash = icon("Trash", paths = arrayOf("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"))
    val External = icon("External", paths = arrayOf("M14 4h6v6M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"))
    val Chevron = icon("Chevron", paths = arrayOf("M9 5l7 7-7 7"))
    val Plus = icon("Plus", paths = arrayOf("M12 5v14M5 12h14"))
    val Search = icon("Search", paths = arrayOf(circle(11f, 11f, 6.5f), "M16 16l4.5 4.5"))
    val Person = icon("Person", paths = arrayOf(circle(12f, 8f, 4f), "M4 20c0-4 3.6-6 8-6s8 2 8 6"))
}

/** SVG path data for a circle, since [addPathNodes] only parses path commands. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 ${-2 * r},0"

private fun icon(name: String, strokeWidth: Float = 2f, paths: Array<String>): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
