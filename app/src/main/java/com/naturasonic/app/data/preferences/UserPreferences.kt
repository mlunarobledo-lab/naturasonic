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

    val noiseGateMode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NOISE_GATE_MODE] ?: 0
    }

    val ecoModeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ECO_MODE_ENABLED] ?: false
    }

    val ecoModeAutoActivate: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ECO_MODE_AUTO] ?: true
    }

    val ecoModeThreshold: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ECO_MODE_THRESHOLD] ?: 20
    }

    val selectedProfileId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_PROFILE_ID]?.toLongOrNull() ?: -1L
    }

    val headTrackingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HEAD_TRACKING_ENABLED] ?: false
    }

    val headTrackingSensitivity: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_HEAD_TRACKING_SENSITIVITY] ?: 0.6f
    }

    val aecMode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AEC_MODE] ?: 0
    }

    val dosimetryEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DOSIMETRY_ENABLED] ?: false
    }

    val calibrationOffset: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_CALIBRATION_OFFSET] ?: 94.0f
    }

    val attentionAgcEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ATTENTION_AGC_ENABLED] ?: false
    }

    val speechBoostDb: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_BOOST_DB] ?: 3.0f
    }

    val alertAttenuationDb: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_ALERT_ATTENUATION_DB] ?: 4.0f
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

    suspend fun setNoiseGateMode(mode: Int) {
        dataStore.edit { it[KEY_NOISE_GATE_MODE] = mode }
    }

    suspend fun setEcoModeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ECO_MODE_ENABLED] = enabled }
    }

    suspend fun setEcoModeAutoActivate(enabled: Boolean) {
        dataStore.edit { it[KEY_ECO_MODE_AUTO] = enabled }
    }

    suspend fun setEcoModeThreshold(threshold: Int) {
        dataStore.edit { it[KEY_ECO_MODE_THRESHOLD] = threshold.coerceIn(5, 50) }
    }

    suspend fun setSelectedProfileId(id: Long) {
        dataStore.edit { it[KEY_SELECTED_PROFILE_ID] = id.toString() }
    }

    suspend fun setHeadTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HEAD_TRACKING_ENABLED] = enabled }
    }

    suspend fun setHeadTrackingSensitivity(sensitivity: Float) {
        dataStore.edit { it[KEY_HEAD_TRACKING_SENSITIVITY] = sensitivity.coerceIn(0f, 1f) }
    }

    suspend fun setAecMode(mode: Int) {
        dataStore.edit { it[KEY_AEC_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setDosimetryEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DOSIMETRY_ENABLED] = enabled }
    }

    suspend fun setCalibrationOffset(offset: Float) {
        dataStore.edit { it[KEY_CALIBRATION_OFFSET] = offset.coerceIn(60f, 120f) }
    }

    suspend fun setAttentionAgcEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ATTENTION_AGC_ENABLED] = enabled }
    }

    suspend fun setSpeechBoostDb(db: Float) {
        dataStore.edit { it[KEY_SPEECH_BOOST_DB] = db.coerceIn(1f, 6f) }
    }

    suspend fun setAlertAttenuationDb(db: Float) {
        dataStore.edit { it[KEY_ALERT_ATTENUATION_DB] = db.coerceIn(1f, 8f) }
    }

    companion object {
        private val KEY_CURRENT_MODE = stringPreferencesKey("current_mode")
        private val KEY_MASTER_VOLUME = floatPreferencesKey("master_volume")
        private val KEY_SUBTITLE_COLOR = intPreferencesKey("subtitle_color")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        private val KEY_ALERT_DETECTION = booleanPreferencesKey("alert_detection_enabled")
        private val KEY_NOISE_GATE_MODE = intPreferencesKey("noise_gate_mode")
        private val KEY_ECO_MODE_ENABLED = booleanPreferencesKey("eco_mode_enabled")
        private val KEY_ECO_MODE_AUTO = booleanPreferencesKey("eco_mode_auto_activate")
        private val KEY_ECO_MODE_THRESHOLD = intPreferencesKey("eco_mode_threshold")
        private val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val KEY_HEAD_TRACKING_ENABLED = booleanPreferencesKey("head_tracking_enabled")
        private val KEY_HEAD_TRACKING_SENSITIVITY = floatPreferencesKey("head_tracking_sensitivity")
        private val KEY_AEC_MODE = intPreferencesKey("aec_mode")
        private val KEY_DOSIMETRY_ENABLED = booleanPreferencesKey("dosimetry_enabled")
        private val KEY_CALIBRATION_OFFSET = floatPreferencesKey("calibration_offset")
        private val KEY_ATTENTION_AGC_ENABLED = booleanPreferencesKey("attention_agc_enabled")
        private val KEY_SPEECH_BOOST_DB = floatPreferencesKey("speech_boost_db")
        private val KEY_ALERT_ATTENUATION_DB = floatPreferencesKey("alert_attenuation_db")
    }
}
