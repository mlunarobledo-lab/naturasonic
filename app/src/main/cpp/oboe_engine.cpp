#include "oboe_engine.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "NaturaSonicEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NaturaSonicEngine::NaturaSonicEngine()
    : limiter_(85.0f) {
    captureBuffer_.resize(kFramesPerBuffer * kChannelCount);
}

NaturaSonicEngine::~NaturaSonicEngine() {
    stop();
}

bool NaturaSonicEngine::start() {
    if (running_.load()) return true;

    openInputStream();
    openOutputStream();

    if (!inputStream_ || !outputStream_) {
        LOGE("Failed to open audio streams");
        stop();
        return false;
    }

    auto resultIn = inputStream_->requestStart();
    auto resultOut = outputStream_->requestStart();

    if (resultIn != oboe::Result::OK || resultOut != oboe::Result::OK) {
        LOGE("Failed to start streams: in=%d out=%d",
             static_cast<int>(resultIn), static_cast<int>(resultOut));
        stop();
        return false;
    }

    running_.store(true);
    LOGI("Audio engine started: %dHz, %d channels, %d frames/buffer",
         kSampleRate, kChannelCount, kFramesPerBuffer);
    return true;
}

void NaturaSonicEngine::stop() {
    running_.store(false);
    if (inputStream_) {
        inputStream_->stop();
        inputStream_->close();
        inputStream_.reset();
    }
    if (outputStream_) {
        outputStream_->stop();
        outputStream_->close();
        outputStream_.reset();
    }
    LOGI("Audio engine stopped");
}

void NaturaSonicEngine::setAmplification(float level) {
    processor_.setAmplification(level);
}

void NaturaSonicEngine::setEqBands(const float* bands, int count) {
    processor_.setEqBands(bands, count);
}

void NaturaSonicEngine::setNoiseSuppressionEnabled(bool enabled) {
    processor_.setNoiseSuppressionEnabled(enabled);
}

void NaturaSonicEngine::setVolumeLimitDb(float limitDb) {
    limiter_.setLimitDb(limitDb);
}

std::vector<float> NaturaSonicEngine::getLatestAudioBuffer() {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    return latestBuffer_;
}

oboe::DataCallbackResult NaturaSonicEngine::onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    if (!running_.load()) return oboe::DataCallbackResult::Stop;

    if (stream == outputStream_.get()) {
        auto* output = static_cast<float*>(audioData);

        if (inputStream_) {
            auto result = inputStream_->read(
                captureBuffer_.data(), numFrames, 0);

            if (result.value() > 0) {
                int framesToProcess = result.value();

                processor_.process(captureBuffer_.data(), framesToProcess);
                limiter_.process(captureBuffer_.data(), framesToProcess);

                std::memcpy(output, captureBuffer_.data(),
                           framesToProcess * kChannelCount * sizeof(float));

                {
                    std::lock_guard<std::mutex> lock(bufferMutex_);
                    latestBuffer_.assign(captureBuffer_.begin(),
                                        captureBuffer_.begin() + framesToProcess);
                }

                whisperBridge_.feedAudio(captureBuffer_.data(), framesToProcess);
            } else {
                std::memset(output, 0, numFrames * kChannelCount * sizeof(float));
            }
        } else {
            std::memset(output, 0, numFrames * kChannelCount * sizeof(float));
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void NaturaSonicEngine::onErrorAfterClose(
        oboe::AudioStream* stream, oboe::Result result) {
    LOGE("Stream error: %s", oboe::convertToText(result));
    if (running_.load()) {
        stop();
        start();
    }
}

bool NaturaSonicEngine::initWhisper(const char* modelPath) {
    return whisperBridge_.initModel(modelPath);
}

void NaturaSonicEngine::releaseWhisper() {
    whisperBridge_.releaseModel();
}

void NaturaSonicEngine::startWhisperCapture() {
    whisperBridge_.startCapture();
}

std::string NaturaSonicEngine::stopWhisperCapture() {
    return whisperBridge_.stopCapture();
}

std::string NaturaSonicEngine::getWhisperText() {
    return whisperBridge_.getLatestText();
}

bool NaturaSonicEngine::isWhisperCapturing() const {
    return whisperBridge_.isCapturing();
}

bool NaturaSonicEngine::isWhisperModelLoaded() const {
    return whisperBridge_.isModelLoaded();
}

void NaturaSonicEngine::openInputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(kChannelCount)
           ->setSampleRate(kSampleRate)
           ->setFramesPerDataCallback(kFramesPerBuffer)
           ->setInputPreset(oboe::InputPreset::Unprocessed);

    auto result = builder.openStream(inputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));
        inputStream_.reset();
    }
}

void NaturaSonicEngine::openOutputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(kChannelCount)
           ->setSampleRate(kSampleRate)
           ->setFramesPerDataCallback(kFramesPerBuffer)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    auto result = builder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));
        outputStream_.reset();
    }
}
