#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeInit(
        JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return 0;
    }

    LOGI("Initializing whisper model from: %s", path);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("Failed to initialize whisper context");
        return 0;
    }

    LOGI("Whisper model initialized successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeTranscribe(
        JNIEnv* env, jobject thiz, jlong ctxPtr, jfloatArray audioData) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    jsize nSamples = env->GetArrayLength(audioData);
    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);
    if (!samples || nSamples == 0) {
        return env->NewStringUTF("");
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.single_segment   = false;
    params.n_threads        = 4;
    params.language         = "es";
    params.translate        = false;

    int result = whisper_full(ctx, params, samples, nSamples);
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        return env->NewStringUTF("");
    }

    int nSegments = whisper_full_n_segments(ctx);
    std::string text;
    for (int i = 0; i < nSegments; i++) {
        const char* segText = whisper_full_get_segment_text(ctx, i);
        if (segText) {
            text += segText;
        }
    }

    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_naturasonic_app_transcription_WhisperTranscriptionEngine_nativeFree(
        JNIEnv* env, jobject thiz, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (ctx) {
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

}
