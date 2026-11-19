package fr.calevent.app.ocr

import android.content.Context

/** `foss` flavor: Tesseract (Apache-2.0), French data bundled in assets. */
fun createOcrEngine(context: Context): OcrEngine = TesseractEngine(context.applicationContext)
