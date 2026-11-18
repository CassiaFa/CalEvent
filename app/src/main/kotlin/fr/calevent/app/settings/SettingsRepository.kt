package fr.calevent.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.PhotoRetention
import fr.calevent.core.ShiftCodeTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User settings, stored locally only. */
data class Settings(
    val defaultCalendarId: Long? = null,
    val alwaysAskCalendar: Boolean = false,
    val skipDuplicates: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Name of the user in team schedules ("Moi"). */
    val me: String? = null,
    val photoRetention: PhotoRetention = PhotoRetention.DAYS_30,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            defaultCalendarId = p[DEFAULT_CALENDAR],
            alwaysAskCalendar = p[ALWAYS_ASK] ?: false,
            skipDuplicates = p[SKIP_DUPLICATES] ?: true,
            theme = p[THEME]?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.SYSTEM,
            me = p[ME]?.takeIf { it.isNotBlank() },
            photoRetention = p[RETENTION]?.let { n -> PhotoRetention.entries.firstOrNull { it.name == n } } ?: PhotoRetention.DAYS_30,
        )
    }

    /** The user's shift code table; the built-in defaults until the user changes something. */
    val codeTable: Flow<ShiftCodeTable> = store.data.map { p ->
        p[CODE_TABLE]?.let { ShiftCodeTable.parse(it) } ?: ShiftCodeTable.default()
    }

    suspend fun setCodeTable(table: ShiftCodeTable) = store.edit { it[CODE_TABLE] = table.serialize() }
    suspend fun resetCodeTable() = store.edit { it.remove(CODE_TABLE) }

    suspend fun setDefaultCalendar(id: Long?) = store.edit { if (id == null) it.remove(DEFAULT_CALENDAR) else it[DEFAULT_CALENDAR] = id }
    suspend fun setAlwaysAskCalendar(v: Boolean) = store.edit { it[ALWAYS_ASK] = v }
    suspend fun setSkipDuplicates(v: Boolean) = store.edit { it[SKIP_DUPLICATES] = v }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setMe(name: String?) = store.edit { if (name.isNullOrBlank()) it.remove(ME) else it[ME] = name }
    suspend fun setPhotoRetention(r: PhotoRetention) = store.edit { it[RETENTION] = r.name }

    companion object {
        private val DEFAULT_CALENDAR = longPreferencesKey("default_calendar_id")
        private val ALWAYS_ASK = booleanPreferencesKey("always_ask_calendar")
        private val SKIP_DUPLICATES = booleanPreferencesKey("skip_duplicates")
        private val THEME = stringPreferencesKey("theme")
        private val ME = stringPreferencesKey("me")
        private val CODE_TABLE = stringPreferencesKey("shift_code_table")
        private val RETENTION = stringPreferencesKey("photo_retention")

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) { instance ?: SettingsRepository(context).also { instance = it } }
    }
}
