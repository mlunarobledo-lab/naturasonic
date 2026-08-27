#include <jni.h>
#include <string>
#include <android/log.h>
#include "oboe_engine.h"

#define LOG_TAG "NaturaSonicJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static NaturaSonicEngine* engine = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeCreateEngine(
        JNIEnv* env, jobject thiz) {
    if (engine == nullptr) {
        engine = new NaturaSonicEngine();
    }
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jboolean JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStart(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        return eng->start() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStop(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAmplification(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat level) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAmplification(level);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetEqBands(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloatArray bands) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng && bands) {
        jsize len = env->GetArrayLength(bands);
        jfloat* data = env->GetFloatArrayElements(bands, nullptr);
        eng->setEqBands(data, len);
        env->ReleaseFloatArrayElements(bands, data, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetNoiseSuppressionEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setNoiseSuppressionEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetVolumeLimitDb(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat limitDb) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setVolumeLimitDb(limitDb);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetNoiseGateMode(
        JNIEnv* env, jobject thiz, jlong engineHandle, jint mode) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setNoiseGateMode(mode);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetOutputMuted(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean muted) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setOutputMuted(muted == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetBalance(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat balance) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setBalance(balance);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetHeadTrackingEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setHeadTrackingEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetHeadTrackingAngles(
        JNIEnv* env, jobject thiz, jlong engineHandle,
        jfloat azimuthDeg, jfloat pitchDeg, jfloat sensitivity) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setHeadTrackingAngles(azimuthDeg, pitchDeg, sensitivity);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAttentionAgcEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAttentionAgcEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAttentionGainOffsets(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloatArray offsets) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng && offsets) {
        jsize len = env->GetArrayLength(offsets);
        jfloat* data = env->GetFloatArrayElements(offsets, nullptr);
        eng->setAttentionGainOffsets(data, len);
        env->ReleaseFloatArrayElements(offsets, data, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeApplyProfile(
        JNIEnv* env, jobject thiz, jlong engineHandle,
        jfloatArray bands, jfloat amplification, jint noiseGateMode) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng && bands) {
        jsize len = env->GetArrayLength(bands);
        jfloat* data = env->GetFloatArrayElements(bands, nullptr);
        eng->applyProfile(data, len, amplification, noiseGateMode);
        env->ReleaseFloatArrayElements(bands, data, JNI_ABORT);
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetAudioBuffer(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto buffer = eng->getLatestAudioBuffer();
    if (buffer.empty()) return nullptr;

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(buffer.size()));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(buffer.size()), buffer.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetYamnetAudioBuffer(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto buffer = eng->getYamnetAudioBuffer();
    if (buffer.empty()) return nullptr;

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(buffer.size()));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(buffer.size()), buffer.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetLatencyStats(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto stats = eng->getLatencyStats();
    jfloatArray result = env->NewFloatArray(4);
    float data[4] = {stats.dspMinUs, stats.dspMaxUs, stats.dspAvgUs,
                     static_cast<float>(stats.frameCount)};
    env->SetFloatArrayRegion(result, 0, 4, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStartVoiceAnalyzer(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->startVoiceAnalyzer();
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStopVoiceAnalyzer(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->stopVoiceAnalyzer();
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetVoiceMetrics(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto metrics = eng->getVoiceMetrics();
    jfloatArray result = env->NewFloatArray(4);
    float data[4] = {metrics.pitchHz, metrics.jitterPercent,
                     metrics.shimmerPercent,
                     metrics.isVoiced ? 1.0f : 0.0f};
    env->SetFloatArrayRegion(result, 0, 4, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAecMode(
        JNIEnv* env, jobject thiz, jlong engineHandle, jint mode) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAecMode(mode);
    }
}

JNIEXPORT jint JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetAudioSessionId(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        return eng->getAudioSessionId();
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeDestroy(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        delete eng;
        if (eng == engine) engine = nullptr;
    }
}

// --- Transient Limiter JNI bridge ---

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTransientLimiterEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTransientLimiterEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTransientLimiterThreshold(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat thresholdDb) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTransientLimiterThreshold(thresholdDb);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetTransientLimiterActive(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        return eng->isTransientLimiterActive() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// --- Watchdog JNI bridge ---

JNIEXPORT jlongArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetWatchdogStats(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto stats = eng->getWatchdogStats();
    jlongArray result = env->NewLongArray(5);
    jlong data[5] = {
        stats.lastCallbackNs,
        static_cast<jlong>(stats.xRunCount),
        static_cast<jlong>(stats.restartCount),
        static_cast<jlong>(stats.consecutiveErrors),
        stats.isRunning ? 1L : 0L
    };
    env->SetLongArrayRegion(result, 0, 5, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeResetWatchdog(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->resetWatchdog();
    }
}

// --- Dosimetry JNI bridge ---

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStartDosimetry(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->startDosimetry();
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeStopDosimetry(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->stopDosimetry();
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetDosimetryData(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto data = eng->getDosimetryData();
    jfloatArray result = env->NewFloatArray(3);
    float arr[3] = {data.instantDba, data.leq, data.peakDba};
    env->SetFloatArrayRegion(result, 0, 3, arr);
    return result;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetCalibrationOffset(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat offsetDb) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setCalibrationOffset(offsetDb);
    }
}

// --- ANC Phase Inverter JNI bridge ---

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncPhaseEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncPhaseEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncCancellationGain(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat gain) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncCancellationGain(gain);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncLpEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncLpEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncHpEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncHpEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncLpCutoff(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat cutoffHz) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncLpCutoff(cutoffHz);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetAncHpCutoff(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat cutoffHz) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setAncHpCutoff(cutoffHz);
    }
}

// --- WDRC Compressor JNI bridge ---

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetWdrcEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setWdrcEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetWdrcMakeupGainDb(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat gainDb) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setWdrcMakeupGainDb(gainDb);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetWdrcPreset(
        JNIEnv* env, jobject thiz, jlong engineHandle, jint presetIndex) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setWdrcPreset(presetIndex);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetWdrcBandParams(
        JNIEnv* env, jobject thiz, jlong engineHandle, jint bandIndex,
        jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setWdrcBandParams(bandIndex, thresholdDb, ratio, attackMs, releaseMs);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeApplyWdrcAudiogramProfile(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloatArray thresholds) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng || !thresholds) return;

    jsize len = env->GetArrayLength(thresholds);
    jfloat* data = env->GetFloatArrayElements(thresholds, nullptr);
    if (data) {
        eng->applyWdrcAudiogramProfile(data, len);
        env->ReleaseFloatArrayElements(thresholds, data, JNI_ABORT);
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetWdrcActiveGains(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    auto gains = eng->getWdrcActiveGains();
    jfloatArray result = env->NewFloatArray(10);
    env->SetFloatArrayRegion(result, 0, 10, gains.gains);
    return result;
}

// --- Tinnitus Generator JNI bridge ---

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTinnitusEnabled(
        JNIEnv* env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTinnitusEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTinnitusSoundType(
        JNIEnv* env, jobject thiz, jlong engineHandle, jint type) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTinnitusSoundType(type);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTinnitusVolume(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat volume) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTinnitusVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeSetTinnitusFrequencyHz(
        JNIEnv* env, jobject thiz, jlong engineHandle, jfloat freqHz) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        eng->setTinnitusFrequencyHz(freqHz);
    }
}

// --- Spectrum Analyzer JNI bridge ---

JNIEXPORT jfloatArray JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeGetSpectrumData(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return nullptr;

    float bands[10];
    eng->getSpectrumData(bands);
    jfloatArray result = env->NewFloatArray(10);
    env->SetFloatArrayRegion(result, 0, 10, bands);
    return result;
}

// --- Whisper JNI bridge (audio stays in C++) ---

JNIEXPORT jboolean JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeInitWhisper(
        JNIEnv* env, jobject thiz, jlong engineHandle, jstring modelPath) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) {
        LOGE("nativeInitWhisper: null engine handle");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    bool ok = eng->initWhisper(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeStartWhisperCapture(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) eng->startWhisperCapture();
}

JNIEXPORT jstring JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeStopWhisperCapture(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return env->NewStringUTF("");

    std::string text = eng->stopWhisperCapture();
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeGetWhisperText(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (!eng) return env->NewStringUTF("");

    std::string text = eng->getWhisperText();
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeReleaseWhisper(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) eng->releaseWhisper();
}

}
