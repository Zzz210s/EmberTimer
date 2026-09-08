package com.embertimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    // v1.9.11 自动备份
    private val keyAutoBackup = booleanPreferencesKey("autobackup")
    private val keyBackupUri = stringPreferencesKey("backup_uri")
    private val keyBackupLast = longPreferencesKey("backup_last")
    private val keyFirstLaunch = stringPreferencesKey("first_launch_date")

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

    // ---- 自动备份 ----
    val autoBackupEnabled: Flow<Boolean> = ds.data.map { it[keyAutoBackup] ?: false }
    val backupUri: Flow<String?> = ds.data.map { it[keyBackupUri] }
    val backupLastAt: Flow<Long> = ds.data.map { it[keyBackupLast] ?: 0L }

    suspend fun setAutoBackupEnabled(v: Boolean) { ds.edit { it[keyAutoBackup] = v } }
    suspend fun setBackupUri(uri: String) { ds.edit { it[keyBackupUri] = uri } }
    suspend fun setBackupLastAt(t: Long) { ds.edit { it[keyBackupLast] = t } }

    /** 首次打开应用日期(yyyy-MM-dd);报表往期回顾的起点。未设置时返回 null。 */
    val firstLaunchDate: Flow<String?> = ds.data.map { it[keyFirstLaunch] }
    suspend fun setFirstLaunchDate(d: String) { ds.edit { it[keyFirstLaunch] = d } }
}
