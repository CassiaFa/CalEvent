package fr.calevent.app.history

import android.content.Context
import android.graphics.Bitmap
import fr.calevent.core.History
import fr.calevent.core.HistoryEntry
import fr.calevent.core.PhotoRetention
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local history of the last sends (`filesDir/history/history.json`) and their small thumbnails
 * (`filesDir/history/thumbs/<id>.jpg`). Private storage only, nothing leaves the phone.
 */
class HistoryRepository private constructor(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "history")
    private val thumbs = File(dir, "thumbs")
    private val file = File(dir, "history.json")
    private val lock = Mutex()
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var loaded = false

    /** Newest first. Empty until [load] ran. */
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun thumbnailFile(id: String): File = File(thumbs, "$id.jpg")

    suspend fun load() = lock.withLock { ensureLoaded() }

    /** Records [entry]; [thumbnail] (any size) is scaled to about 200 px and saved when given. */
    suspend fun add(entry: HistoryEntry, thumbnail: Bitmap?) = lock.withLock {
        withContext(Dispatchers.IO) {
            ensureLoadedBlocking()
            var e = entry
            if (thumbnail != null && saveThumbnail(entry.id, thumbnail)) e = e.copy(hasThumbnail = true) else e = e.copy(hasThumbnail = false)
            val before = _entries.value
            val after = History.add(before, e)
            (before + e).filter { b -> after.none { it.id == b.id } }.forEach { thumbnailFile(it.id).delete() }
            _entries.value = after
            write(after)
        }
    }

    /** Forgets one entry (after "Annuler l'ajout"). */
    suspend fun remove(id: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            ensureLoadedBlocking()
            thumbnailFile(id).delete()
            _entries.value = _entries.value.filter { it.id != id }.also(::write)
        }
    }

    /** Deletes every thumbnail; the entries stay, without picture. */
    suspend fun clearThumbnails() = lock.withLock {
        withContext(Dispatchers.IO) {
            ensureLoadedBlocking()
            thumbs.listFiles()?.forEach { it.delete() }
            _entries.value = _entries.value.map { it.copy(hasThumbnail = false) }.also(::write)
        }
    }

    /** Deletes the thumbnails of entries older than [retention]. */
    suspend fun purgeExpiredThumbnails(retention: PhotoRetention, nowMillis: Long = System.currentTimeMillis()) = lock.withLock {
        withContext(Dispatchers.IO) {
            ensureLoadedBlocking()
            val expired = History.expiredThumbnails(_entries.value, retention, nowMillis).map { it.id }.toSet()
            if (expired.isNotEmpty()) {
                expired.forEach { thumbnailFile(it).delete() }
                _entries.value = _entries.value.map { if (it.id in expired) it.copy(hasThumbnail = false) else it }.also(::write)
            }
        }
    }

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) { ensureLoadedBlocking() }

    private fun ensureLoadedBlocking() {
        if (loaded) return
        loaded = true
        _entries.value = runCatching { History.normalize(parse(file.readText())) }.getOrDefault(emptyList())
    }

    private fun write(list: List<HistoryEntry>) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "history.json.tmp")
            tmp.writeText(serialize(list))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    private fun saveThumbnail(id: String, src: Bitmap): Boolean = runCatching {
        thumbs.mkdirs()
        val s = 200f / max(src.width, src.height)
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true) else src
        thumbnailFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        if (bmp !== src) bmp.recycle()
        true
    }.getOrDefault(false)

    private fun serialize(list: List<HistoryEntry>): String = JSONArray().also { arr ->
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("at", e.createdAtMillis); put("kind", e.kindLabel ?: JSONObject.NULL)
                put("team", e.isTeam); put("person", e.personName ?: JSONObject.NULL); put("added", e.addedCount)
                put("calendar", e.calendarName); put("from", e.from.toString()); put("to", e.to.toString())
                put("thumb", e.hasThumbnail); put("events", JSONArray(e.eventIds))
            })
        }
    }.toString()

    private fun parse(text: String): List<HistoryEntry> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    id = o.getString("id"),
                    createdAtMillis = o.getLong("at"),
                    kindLabel = o.optString("kind").takeIf { !o.isNull("kind") && it.isNotBlank() },
                    isTeam = o.optBoolean("team"),
                    personName = o.optString("person").takeIf { !o.isNull("person") && it.isNotBlank() },
                    addedCount = o.getInt("added"),
                    calendarName = o.optString("calendar"),
                    from = LocalDate.parse(o.getString("from")),
                    to = LocalDate.parse(o.getString("to")),
                    hasThumbnail = o.optBoolean("thumb"),
                    eventIds = o.getJSONArray("events").let { a -> (0 until a.length()).map { a.getLong(it) } },
                )
            }.getOrNull()
        }
    }

    companion object {
        @Volatile private var instance: HistoryRepository? = null
        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) { instance ?: HistoryRepository(context).also { instance = it } }
    }
}
