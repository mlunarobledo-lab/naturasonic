#include "whisper_bridge.h"
#include <android/log.h>
#include <algorithm>
#include <chrono>

#define LOG_TAG "WhisperBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

WhisperBridge::WhisperBridge() {
    audioBuffer_.reserve(BUFFER_RESERVE);
}

WhisperBridge::~WhisperBridge() {
    if (capturing_.load()) {
        stopCapture();
    }
    releaseModel();
}

bool WhisperBridge::initModel(const char* modelPath) {
    releaseModel();

    LOGI("Initializing whisper model: %s", modelPath);
    struct whisper_context_params cparams = whisper_context_default_params();
    ctx_ = whisper_init_from_file_with_params(modelPath, cparams);

    if (!ctx_) {
        LOGE("Failed to initialize whisper context");
        return false;
    }

    LOGI("Whisper model loaded successfully");
    return true;
}

void WhisperBridge::releaseModel() {
    if (capturing_.load()) {
        stopCapture();
    }
    if (ctx_) {
        whisper_free(ctx_);
        ctx_ = nullptr;
        LOGI("Whisper context released");
    }
}

void WhisperBridge::startCapture() {
    if (capturing_.load() || !ctx_) return;

    {
        std::lock_guard<std::mutex> lock(audioMutex_);
        audioBuffer_.clear();
    }
    {
        std::lock_guard<std::mutex> lock(textMutex_);
        latestText_.clear();
        accumulatedText_.clear();
    }

    capturing_.store(true);
    threadRunning_.store(true);
    processingThread_ = std::thread(&WhisperBridge::processingLoop, this);
    LOGI("Whisper capture started");
}

std::string WhisperBridge::stopCapture() {
    if (!capturing_.load()) {
        std::lock_guard<std::mutex> lock(textMutex_);
        return accumulatedText_;
    }

    capturing_.store(false);
    cv_.notify_all();

    if (processingThread_.joinable()) {
        processingThread_.join();
    }

    // Process remaining audio
    std::vector<float> remaining;
    {
        std::lock_guard<std::mutex> lock(audioMutex_);
        remaining = std::move(audioBuffer_);
        audioBuffer_.clear();
        audioBuffer_.reserve(BUFFER_RESERVE);
    }

    int remaining16k = static_cast<int>(remaining.size()) / RESAMPLE_RATIO;
    if (remaining16k >= MIN_SAMPLES_16K && ctx_) {
        processSegment(remaining.data(), static_cast<int>(remaining.size()));
    }

    std::lock_guard<std::mutex> lock(textMutex_);
    LOGI("Whisper capture stopped, accumulated %zu chars", accumulatedText_.size());
    return accumulatedText_;
}

void WhisperBridge::feedAudio(const float* data, int numFrames) {
    if (!capturing_.load()) return;

    std::lock_guard<std::mutex> lock(audioMutex_);
    audioBuffer_.insert(audioBuffer_.end(), data, data + numFrames);

    if (static_cast<int>(audioBuffer_.size()) >= SEGMENT_SAMPLES_48K) {
        cv_.notify_one();
    }
}

std::string WhisperBridge::getLatestText() {
    std::lock_guard<std::mutex> lock(textMutex_);
    return latestText_;
}

void WhisperBridge::processingLoop() {
    LOGI("Processing thread started");

    while (capturing_.load()) {
        std::vector<float> segment;

        {
            std::unique_lock<std::mutex> lock(cvMutex_);
            cv_.wait_for(lock, std::chrono::milliseconds(200), [this] {
                std::lock_guard<std::mutex> alock(audioMutex_);
                return !capturing_.load() ||
                       static_cast<int>(audioBuffer_.size()) >= SEGMENT_SAMPLES_48K;
            });
        }

        if (!capturing_.load()) break;

        {
            std::lock_guard<std::mutex> lock(audioMutex_);
            if (static_cast<int>(audioBuffer_.size()) >= SEGMENT_SAMPLES_48K) {
                segment.assign(audioBuffer_.begin(),
                               audioBuffer_.begin() + SEGMENT_SAMPLES_48K);
                audioBuffer_.erase(audioBuffer_.begin(),
                                   audioBuffer_.begin() + SEGMENT_SAMPLES_48K);
            }
        }

        if (!segment.empty() && ctx_) {
            processSegment(segment.data(), static_cast<int>(segment.size()));
        }
    }

    threadRunning_.store(false);
    LOGI("Processing thread finished");
}

void WhisperBridge::processSegment(const float* samples48k, int numSamples48k) {
    std::vector<float> resampled;
    resample48to16(samples48k, numSamples48k, resampled);

    if (resampled.empty()) return;

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.single_segment   = false;
    params.n_threads        = 4;
    params.language         = "es";
    params.translate        = false;

    int result = whisper_full(ctx_, params,
                              resampled.data(),
                              static_cast<int>(resampled.size()));

    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return;
    }

    int nSegments = whisper_full_n_segments(ctx_);
    std::string text;
    for (int i = 0; i < nSegments; i++) {
        const char* segText = whisper_full_get_segment_text(ctx_, i);
        if (segText) {
            text += segText;
        }
    }

    if (!text.empty()) {
        std::lock_guard<std::mutex> lock(textMutex_);
        latestText_ = text;
        if (!accumulatedText_.empty()) {
            accumulatedText_ += " ";
        }
        accumulatedText_ += text;
        LOGI("Transcribed segment: %zu chars", text.size());
    }
}

void WhisperBridge::resample48to16(const float* input, int inputFrames,
                                    std::vector<float>& output) {
    int outputSize = inputFrames / RESAMPLE_RATIO;
    if (outputSize == 0) return;

    output.resize(outputSize);
    for (int i = 0; i < outputSize; i++) {
        int srcIdx = i * RESAMPLE_RATIO;
        float sum = 0.0f;
        int count = 0;
        for (int j = 0; j < RESAMPLE_RATIO; j++) {
            if (srcIdx + j < inputFrames) {
                sum += input[srcIdx + j];
                count++;
            }
        }
        output[i] = sum / static_cast<float>(count);
    }
}
