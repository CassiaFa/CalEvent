package fr.calevent.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Crop rectangle in 0..1 coordinates of the photo as displayed (EXIF and user rotation already applied). */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * The photo currently being processed.
 * @param uri `file:` Uri of a JPEG inside `cacheDir/captures/` (never the gallery).
 * @param quarterTurns user rotation, clockwise quarter turns, always in 0..3.
 * @param crop optional crop in displayed (rotated) coordinates; null means no crop.
 * @param id changes every time a photo is set (capture or import), even for an identical uri, so work started for
 *   a previous photo can be recognised as stale.
 */
data class CapturedPhoto(val uri: Uri, val quarterTurns: Int = 0, val crop: CropRect? = null, val id: Long = 0)

/** App-wide holder of the current photo. Files live in the app cache only and are deleted by [clear]. */
object CapturedPhotoStore {
    private val _photo = MutableStateFlow<CapturedPhoto?>(null)
    val photo: StateFlow<CapturedPhoto?> = _photo.asStateFlow()

    /** Creates a new, empty JPEG target file in `cacheDir/captures/`. */
    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "capture_${UUID.randomUUID()}.jpg")
    }

    private var nextId = 0L

    /**
     * Makes [uri] the current photo (rotation and crop reset, new [CapturedPhoto.id]); the previous file is deleted.
     * A new photo or import always gets a fresh automatic crop suggestion.
     */
    fun set(uri: Uri) {
        deleteFile(_photo.value)
        autoCropDone = null
        autoOrientDone = null
        _photo.value = CapturedPhoto(uri, id = ++nextId)
    }

    fun rotateRight() = rotate(1)
    fun rotateLeft() = rotate(3)

    /**
     * Rotating resets the crop, since it is expressed in displayed coordinates, and so allows a new suggestion.
     * The user chose the orientation: it is not searched automatically any more for this photo.
     */
    private fun rotate(delta: Int) {
        autoCropDone = null
        autoOrientDone = _photo.value?.id
        _photo.update { p -> p?.copy(quarterTurns = (p.quarterTurns + delta) % 4, crop = null) }
    }

    /** Sets the crop (normalised 0..1, displayed orientation); null removes it. */
    fun setCrop(crop: CropRect?) = _photo.update { p -> p?.copy(crop = crop) }

    // (photo id, rotation) for which the automatic crop search finished, so coming back to the preview
    // does not search again nor overwrite what the user chose. Reset whenever the photo changes.
    private var autoCropDone: Pair<Long, Int>? = null

    /** True once the automatic crop search has finished for [photo] at its current rotation. */
    fun isAutoCropDone(photo: CapturedPhoto) = autoCropDone == photo.id to photo.quarterTurns

    /** Records that the search finished for [photo]; ignored when [photo] is no longer the current one. */
    fun markAutoCropDone(photo: CapturedPhoto) {
        val now = _photo.value ?: return
        if (now.id == photo.id) autoCropDone = photo.id to photo.quarterTurns
    }

    // Photo id for which the automatic orientation was searched (or the user turned the photo himself).
    private var autoOrientDone: Long? = null

    /** True once the orientation of [photo] was searched automatically or chosen by the user. */
    fun isAutoOrientDone(photo: CapturedPhoto) = autoOrientDone == photo.id

    /** Records that the orientation search finished for [photo]. */
    fun markAutoOrientDone(photo: CapturedPhoto) {
        if (_photo.value?.id == photo.id) autoOrientDone = photo.id
    }

    /**
     * Applies an automatic orientation ([quarterTurns], clockwise, 0..3) and [crop] (in the turned photo's
     * coordinates, or null) found for [photo], and records both searches as done. Ignored when the photo was
     * replaced, turned or cropped meanwhile.
     */
    fun applyAutoOrientation(photo: CapturedPhoto, quarterTurns: Int, crop: CropRect?) {
        val now = _photo.value ?: return
        if (now.id != photo.id || now.quarterTurns != photo.quarterTurns || now.crop != null) return
        autoOrientDone = now.id
        autoCropDone = now.id to quarterTurns
        _photo.value = now.copy(quarterTurns = quarterTurns, crop = crop)
    }

    /** Forgets the current photo and deletes its cache file. */
    fun clear() {
        deleteFile(_photo.value)
        autoCropDone = null
        autoOrientDone = null
        _photo.value = null
    }

    private inline fun MutableStateFlow<CapturedPhoto?>.update(f: (CapturedPhoto?) -> CapturedPhoto?) {
        value = f(value)
    }

    private fun deleteFile(p: CapturedPhoto?) {
        val path = p?.uri?.path ?: return
        File(path).takeIf { it.exists() && it.parentFile?.name == "captures" }?.delete()
    }

    /**
     * Decodes the current photo for display or OCR, off the main thread (runs on [Dispatchers.IO]).
     *
     * Applies, in order: downsampled decode (`inSampleSize`), EXIF orientation, user rotation
     * ([CapturedPhoto.quarterTurns]), crop ([CapturedPhoto.crop]), then scales down so the longest side
     * is at most [maxSide] pixels. The bitmap is a fresh copy owned by the caller.
     *
     * With [applyCrop] = false the crop is skipped (used by the preview to draw the crop handles on the full photo).
     *
     * @throws IllegalStateException if there is no current photo or it cannot be decoded.
     */
    suspend fun loadBitmap(context: Context, maxSide: Int = 2048, applyCrop: Boolean = true): Bitmap = withContext(Dispatchers.IO) {
        val photo = _photo.value ?: error("No current photo")
        decode(photo, maxSide, applyCrop)
    }

    /** Same as [loadBitmap] for a given [photo] snapshot, so a long job keeps working on the photo it started with. */
    suspend fun loadBitmapOf(photo: CapturedPhoto, maxSide: Int = 2048, applyCrop: Boolean = true): Bitmap =
        withContext(Dispatchers.IO) { decode(photo, maxSide, applyCrop) }

    private fun decode(photo: CapturedPhoto, maxSide: Int, applyCrop: Boolean): Bitmap {
        val file = File(requireNotNull(photo.uri.path) { "Bad photo uri" })
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot decode photo" }
        // Keep at least maxSide pixels on the long side so the crop still has resolution.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot decode photo")

        val matrix = exifMatrix(file)
        matrix.postRotate(photo.quarterTurns * 90f)
        var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (bmp !== raw) raw.recycle()

        if (applyCrop) photo.crop?.let { c ->
            val x = (c.left * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val y = (c.top * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val w = ((c.right - c.left) * bmp.width).roundToInt().coerceIn(1, bmp.width - x)
            val h = ((c.bottom - c.top) * bmp.height).roundToInt().coerceIn(1, bmp.height - y)
            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            if (cropped !== bmp) bmp.recycle()
            bmp = cropped
        }

        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        return bmp
    }

    private fun exifMatrix(file: File): Matrix {
        val m = Matrix()
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        return m
    }
}
