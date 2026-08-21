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
