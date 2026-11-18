package fr.calevent.app.capture

import android.content.Context
import fr.calevent.app.history.HistoryRepository
import fr.calevent.core.PhotoRetention
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Number and total size of the photos stored by the app. */
data class PhotoStats(val count: Int, val bytes: Long)

/** Photos captured or imported live in `cacheDir/captures/` (see [CapturedPhotoStore]). */
object PhotoStorage {
    private fun files(context: Context): List<File> =
        File(context.cacheDir, "captures").listFiles { f -> f.isFile }?.toList().orEmpty()

    private fun currentFile(): File? = CapturedPhotoStore.photo.value?.uri?.path?.let(::File)

    suspend fun stats(context: Context): PhotoStats = withContext(Dispatchers.IO) {
        val all = files(context)
        PhotoStats(all.size, all.sumOf { it.length() })
    }

    /** Deletes every stored photo except the one currently being processed. */
    suspend fun deleteAll(context: Context) = withContext(Dispatchers.IO) {
        val keep = currentFile()
        files(context).filter { it != keep }.forEach { it.delete() }
        HistoryRepository.get(context).clearThumbnails()
    }

    /** Deletes photos older than [retention] (never the current one). */
    suspend fun purgeExpired(context: Context, retention: PhotoRetention, nowMillis: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val keep = currentFile()
            files(context).filter { it != keep && retention.isExpired(it.lastModified(), nowMillis) }.forEach { it.delete() }
            HistoryRepository.get(context).purgeExpiredThumbnails(retention, nowMillis)
        }
}
