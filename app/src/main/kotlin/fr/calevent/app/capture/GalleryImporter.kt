package fr.calevent.app.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Handle returned by [rememberGalleryImporter]: [launch] opens the system photo picker. */
class GalleryImporter internal constructor(private val open: () -> Unit, busy: Boolean) {
    /** True while the picked image is being copied into the app cache. */
    val busy: Boolean = busy
    fun launch() = open()
}

/**
 * System Photo Picker (no storage permission). The picked image is copied into the app cache,
 * becomes the current photo in [CapturedPhotoStore], then [onImported] runs; [onError] if the copy fails.
 */
@Composable
fun rememberGalleryImporter(onImported: () -> Unit, onError: () -> Unit): GalleryImporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val imported by rememberUpdatedState(onImported)
    val failed by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = CapturedPhotoStore.newCaptureFile(context)
                        context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                        target
                    }.getOrNull()
                }
                busy = false
                if (file != null) {
                    CapturedPhotoStore.set(Uri.fromFile(file))
                    imported()
                } else failed()
            }
        }
    }
    return GalleryImporter({ launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, busy)
}
