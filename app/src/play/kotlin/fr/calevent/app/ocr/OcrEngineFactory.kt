package fr.calevent.app.ocr

import android.content.Context

/** `play` flavor: ML Kit with its bundled Latin model. */
@Suppress("UNUSED_PARAMETER")
fun createOcrEngine(context: Context): OcrEngine = TextRecognizerEngine()
