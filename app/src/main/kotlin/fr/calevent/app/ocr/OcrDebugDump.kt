package fr.calevent.app.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.RecognizedTextJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug builds only: saves the recognized text as JSON in the app's own external files, to build
 * real test fixtures. Nothing leaves the phone; the developer copies the files with
 * `adb pull /sdcard/Android/data/fr.calevent.app/files/ocr-dumps/`.
 * The JSON format is [RecognizedTextJson] (readable by the `:core` tests).
 */
object OcrDebugDump {

    const val DIRECTORY = "ocr-dumps"

    /** True for a debuggable build (BuildConfig is not generated in this project). */
    fun isEnabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Writes [text] to `ocr-dumps/<name>.json`; returns the file, or null in release builds. */
    suspend fun write(
        context: Context,
        text: RecognizedText,
        name: String = "ocr-${System.currentTimeMillis()}",
    ): File? {
        if (!isEnabled(context)) return null
        return withContext(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(DIRECTORY) ?: return@withContext null
            File(dir, "$name.json").apply { writeText(RecognizedTextJson.write(text)) }
        }
    }
}
