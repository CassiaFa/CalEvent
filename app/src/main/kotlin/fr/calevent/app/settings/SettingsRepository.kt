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
import fr.calevent.core.PersonName
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
    /**
     * The user in team schedules ("Moi"), as a [PersonName.encode]d string (a plain string is a legacy
     * surname-only value). Decode with [PersonName.decode]; pass it to `findPerson`.
     */
    val me: String? = null,
    val photoRetention: PhotoRetention = PhotoRetention.DAYS_30,
    /** False until the first-launch presentation was finished or skipped. */
    val onboardingDone: Boolean = true,
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
            me = PersonName(p[ME].orEmpty(), p[ME_FIRST].orEmpty()).encode(),
            photoRetention = p[RETENTION]?.let { n -> PhotoRetention.entries.firstOrNull { it.name == n } } ?: PhotoRetention.DAYS_30,
            onboardingDone = p[ONBOARDING_DONE] ?: false,
        )
    }

    /** The user's shift code table; empty until the user defines codes. */
    val codeTable: Flow<ShiftCodeTable> = store.data.map { p ->
        p[CODE_TABLE]?.let { ShiftCodeTable.parse(it) } ?: ShiftCodeTable()
    }

    suspend fun setCodeTable(table: ShiftCodeTable) = store.edit { it[CODE_TABLE] = table.serialize() }
    suspend fun resetCodeTable() = store.edit { it.remove(CODE_TABLE) }

    suspend fun setDefaultCalendar(id: Long?) = store.edit { if (id == null) it.remove(DEFAULT_CALENDAR) else it[DEFAULT_CALENDAR] = id }
    suspend fun setAlwaysAskCalendar(v: Boolean) = store.edit { it[ALWAYS_ASK] = v }
    suspend fun setSkipDuplicates(v: Boolean) = store.edit { it[SKIP_DUPLICATES] = v }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    /** Legacy single-string form: the name is stored as the surname. */
    suspend fun setMe(name: String?) = setMe(name, null)

    suspend fun setMe(last: String?, first: String?) = store.edit {
        if (last.isNullOrBlank()) it.remove(ME) else it[ME] = last.trim()
        if (first.isNullOrBlank()) it.remove(ME_FIRST) else it[ME_FIRST] = first.trim()
    }
    suspend fun setOnboardingDone(v: Boolean) = store.edit { it[ONBOARDING_DONE] = v }
    suspend fun setPhotoRetention(r: PhotoRetention) = store.edit { it[RETENTION] = r.name }

    companion object {
        private val DEFAULT_CALENDAR = longPreferencesKey("default_calendar_id")
        private val ALWAYS_ASK = booleanPreferencesKey("always_ask_calendar")
        private val SKIP_DUPLICATES = booleanPreferencesKey("skip_duplicates")
        private val THEME = stringPreferencesKey("theme")
        private val ME = stringPreferencesKey("me")
        private val ME_FIRST = stringPreferencesKey("me_first")
        private val CODE_TABLE = stringPreferencesKey("shift_code_table")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val RETENTION = stringPreferencesKey("photo_retention")

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) { instance ?: SettingsRepository(context).also { instance = it } }
    }
}
