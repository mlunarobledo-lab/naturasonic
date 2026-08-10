#include <jni.h>
#include <string>
#include "oboe_engine.h"

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

JNIEXPORT void JNICALL
Java_com_naturasonic_app_audio_OboeAudioEngine_nativeDestroy(
        JNIEnv* env, jobject thiz, jlong engineHandle) {
    auto* eng = reinterpret_cast<NaturaSonicEngine*>(engineHandle);
    if (eng) {
        delete eng;
        if (eng == engine) engine = nullptr;
    }
}

}
