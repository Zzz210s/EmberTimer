package com.embertimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

enum class ReminderIntensity { LIGHT, STANDARD, STRONG }

class SettingsRepository(private val ds: DataStore<Preferences>) {
    private val keyActive = longPreferencesKey("active_profile_id")
    private val keyIntensity = stringPreferencesKey("reminder_intensity")
    private val keyThemePack = stringPreferencesKey("theme_pack")

    val activeProfileId: Flow<Long> = ds.data.map { it[keyActive] ?: -1L }

    suspend fun setActiveProfile(id: Long) { ds.edit { it[keyActive] = id } }

    val reminderIntensity: Flow<ReminderIntensity> = ds.data.map { prefs ->
        prefs[keyIntensity]?.let { name -> ReminderIntensity.entries.firstOrNull { it.name == name } } ?: ReminderIntensity.STANDARD
    }

    suspend fun setReminderIntensity(v: ReminderIntensity) { ds.edit { it[keyIntensity] = v.name } }

    val themePack: Flow<com.embertimer.ui.theme.ThemePack> = ds.data.map { prefs ->
        com.embertimer.ui.theme.ThemePack.fromName(prefs[keyThemePack])
    }

    suspend fun setThemePack(p: com.embertimer.ui.theme.ThemePack) { ds.edit { it[keyThemePack] = p.name } }
}
