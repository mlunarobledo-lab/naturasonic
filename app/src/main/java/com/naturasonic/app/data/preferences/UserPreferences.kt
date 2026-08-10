package com.naturasonic.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val currentMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_MODE] ?: "CONVERSATION"
    }

    val masterVolume: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_MASTER_VOLUME] ?: 0.5f
    }

    val subtitleColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_SUBTITLE_COLOR] ?: 0
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val disclaimerAccepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DISCLAIMER_ACCEPTED] ?: false
    }

    val alertDetectionEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALERT_DETECTION] ?: true
    }

    val selectedProfileId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_PROFILE_ID]?.toLongOrNull() ?: -1L
    }

    suspend fun setCurrentMode(mode: String) {
        dataStore.edit { it[KEY_CURRENT_MODE] = mode }
    }

    suspend fun setMasterVolume(volume: Float) {
        dataStore.edit { it[KEY_MASTER_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setSubtitleColor(colorIndex: Int) {
        dataStore.edit { it[KEY_SUBTITLE_COLOR] = colorIndex }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        dataStore.edit { it[KEY_DISCLAIMER_ACCEPTED] = accepted }
    }

    suspend fun setAlertDetectionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ALERT_DETECTION] = enabled }
    }

    suspend fun setSelectedProfileId(id: Long) {
        dataStore.edit { it[KEY_SELECTED_PROFILE_ID] = id.toString() }
    }

    companion object {
        private val KEY_CURRENT_MODE = stringPreferencesKey("current_mode")
        private val KEY_MASTER_VOLUME = floatPreferencesKey("master_volume")
        private val KEY_SUBTITLE_COLOR = intPreferencesKey("subtitle_color")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        private val KEY_ALERT_DETECTION = booleanPreferencesKey("alert_detection_enabled")
        private val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
    }
}
