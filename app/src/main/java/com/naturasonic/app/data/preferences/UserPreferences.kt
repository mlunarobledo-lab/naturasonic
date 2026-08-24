package com.naturasonic.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

    val securityLockEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SECURITY_LOCK_ENABLED] ?: false
    }

    val securityLockTimeout: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_SECURITY_LOCK_TIMEOUT] ?: 1
    }

    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_ENABLED] ?: false
    }

    val lastBackupTimestamp: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_BACKUP_TIMESTAMP] ?: 0L
    }

    val dspWatchdogEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DSP_WATCHDOG_ENABLED] ?: true
    }

    val ancPhaseEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_PHASE_ENABLED] ?: false
    }

    val ancCancellationGain: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_CANCELLATION_GAIN] ?: 0.5f
    }

    val ancLpEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_LP_ENABLED] ?: true
    }

    val ancHpEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_HP_ENABLED] ?: true
    }

    val ancLpCutoff: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_LP_CUTOFF] ?: 200.0f
    }

    val ancHpCutoff: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_ANC_HP_CUTOFF] ?: 4000.0f
    }

    val wdrcEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WDRC_ENABLED] ?: false
    }

    val wdrcMakeupGainDb: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_WDRC_MAKEUP_GAIN_DB] ?: 6.0f
    }

    val wdrcPreset: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_WDRC_PRESET] ?: 0
    }

    val wdrcCustomParams: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WDRC_CUSTOM_PARAMS] ?: ""
    }

    val tinnitusEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TINNITUS_ENABLED] ?: false
    }

    val tinnitusSoundType: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_TINNITUS_SOUND_TYPE] ?: 0
    }

    val tinnitusVolume: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TINNITUS_VOLUME] ?: 0.3f
    }

    val tinnitusFrequencyHz: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TINNITUS_FREQUENCY_HZ] ?: 4000.0f
    }

    val tinnitusTimerMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_TINNITUS_TIMER_MINUTES] ?: 0
    }

    val transientLimiterEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TRANSIENT_LIMITER_ENABLED] ?: false
    }

    val transientLimiterThreshold: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TRANSIENT_LIMITER_THRESHOLD] ?: -6.0f
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

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun setDspWatchdogEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DSP_WATCHDOG_ENABLED] = enabled }
    }

    suspend fun setAncPhaseEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ANC_PHASE_ENABLED] = enabled }
    }

    suspend fun setAncCancellationGain(gain: Float) {
        dataStore.edit { it[KEY_ANC_CANCELLATION_GAIN] = gain.coerceIn(0f, 1f) }
    }

    suspend fun setAncLpEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ANC_LP_ENABLED] = enabled }
    }

    suspend fun setAncHpEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ANC_HP_ENABLED] = enabled }
    }

    suspend fun setAncLpCutoff(cutoffHz: Float) {
        dataStore.edit { it[KEY_ANC_LP_CUTOFF] = cutoffHz.coerceIn(50f, 500f) }
    }

    suspend fun setAncHpCutoff(cutoffHz: Float) {
        dataStore.edit { it[KEY_ANC_HP_CUTOFF] = cutoffHz.coerceIn(2000f, 8000f) }
    }

    suspend fun setWdrcEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WDRC_ENABLED] = enabled }
    }

    suspend fun setWdrcMakeupGainDb(gainDb: Float) {
        dataStore.edit { it[KEY_WDRC_MAKEUP_GAIN_DB] = gainDb.coerceIn(0f, 24f) }
    }

    suspend fun setWdrcPreset(preset: Int) {
        dataStore.edit { it[KEY_WDRC_PRESET] = preset.coerceIn(0, 3) }
    }

    suspend fun setWdrcCustomParams(params: String) {
        dataStore.edit { it[KEY_WDRC_CUSTOM_PARAMS] = params }
    }

    suspend fun setTinnitusEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TINNITUS_ENABLED] = enabled }
    }

    suspend fun setTinnitusSoundType(type: Int) {
        dataStore.edit { it[KEY_TINNITUS_SOUND_TYPE] = type.coerceIn(0, 4) }
    }

    suspend fun setTinnitusVolume(volume: Float) {
        dataStore.edit { it[KEY_TINNITUS_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setTinnitusFrequencyHz(freqHz: Float) {
        dataStore.edit { it[KEY_TINNITUS_FREQUENCY_HZ] = freqHz.coerceIn(500f, 16000f) }
    }

    suspend fun setTinnitusTimerMinutes(minutes: Int) {
        dataStore.edit { it[KEY_TINNITUS_TIMER_MINUTES] = minutes }
    }

    suspend fun setTransientLimiterEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TRANSIENT_LIMITER_ENABLED] = enabled }
    }

    suspend fun setTransientLimiterThreshold(thresholdDb: Float) {
        dataStore.edit { it[KEY_TRANSIENT_LIMITER_THRESHOLD] = thresholdDb.coerceIn(-20f, 0f) }
    }

    suspend fun setSpeechBoostDb(db: Float) {
        dataStore.edit { it[KEY_SPEECH_BOOST_DB] = db.coerceIn(1f, 6f) }
    }

    suspend fun setAlertAttenuationDb(db: Float) {
        dataStore.edit { it[KEY_ALERT_ATTENUATION_DB] = db.coerceIn(1f, 8f) }
    }

    suspend fun setSecurityLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SECURITY_LOCK_ENABLED] = enabled }
    }

    suspend fun setSecurityLockTimeout(minutes: Int) {
        dataStore.edit { it[KEY_SECURITY_LOCK_TIMEOUT] = minutes.coerceIn(0, 15) }
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
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        private val KEY_DSP_WATCHDOG_ENABLED = booleanPreferencesKey("dsp_watchdog_enabled")
        private val KEY_ANC_PHASE_ENABLED = booleanPreferencesKey("anc_phase_enabled")
        private val KEY_ANC_CANCELLATION_GAIN = floatPreferencesKey("anc_cancellation_gain")
        private val KEY_ANC_LP_ENABLED = booleanPreferencesKey("anc_lp_enabled")
        private val KEY_ANC_HP_ENABLED = booleanPreferencesKey("anc_hp_enabled")
        private val KEY_ANC_LP_CUTOFF = floatPreferencesKey("anc_lp_cutoff")
        private val KEY_ANC_HP_CUTOFF = floatPreferencesKey("anc_hp_cutoff")
        private val KEY_WDRC_ENABLED = booleanPreferencesKey("wdrc_enabled")
        private val KEY_WDRC_MAKEUP_GAIN_DB = floatPreferencesKey("wdrc_makeup_gain_db")
        private val KEY_WDRC_PRESET = intPreferencesKey("wdrc_preset")
        private val KEY_WDRC_CUSTOM_PARAMS = stringPreferencesKey("wdrc_custom_params")
        private val KEY_TINNITUS_ENABLED = booleanPreferencesKey("tinnitus_enabled")
        private val KEY_TINNITUS_SOUND_TYPE = intPreferencesKey("tinnitus_sound_type")
        private val KEY_TINNITUS_VOLUME = floatPreferencesKey("tinnitus_volume")
        private val KEY_TINNITUS_FREQUENCY_HZ = floatPreferencesKey("tinnitus_frequency_hz")
        private val KEY_TINNITUS_TIMER_MINUTES = intPreferencesKey("tinnitus_timer_minutes")
        private val KEY_TRANSIENT_LIMITER_ENABLED = booleanPreferencesKey("transient_limiter_enabled")
        private val KEY_TRANSIENT_LIMITER_THRESHOLD = floatPreferencesKey("transient_limiter_threshold")
        private val KEY_SPEECH_BOOST_DB = floatPreferencesKey("speech_boost_db")
        private val KEY_ALERT_ATTENUATION_DB = floatPreferencesKey("alert_attenuation_db")
        private val KEY_SECURITY_LOCK_ENABLED = booleanPreferencesKey("security_lock_enabled")
        private val KEY_SECURITY_LOCK_TIMEOUT = intPreferencesKey("security_lock_timeout")
    }
}
