#include "oboe_engine.h"
#include <android/log.h>
#include <android/trace.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "NaturaSonicEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NaturaSonicEngine::NaturaSonicEngine()
    : limiter_(85.0f), spectrumAnalyzer_(kSampleRate) {
    captureBuffer_.resize(kFramesPerBuffer * kInputChannelCount);
    yamnetBuffer_.resize(kYamnetBufferSize, 0.0f);
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
    if (resultIn != oboe::Result::OK) {
        LOGE("Failed to start input stream: %d", static_cast<int>(resultIn));
        stop();
        return false;
    }

    std::this_thread::sleep_for(std::chrono::milliseconds(500));

    auto resultOut = outputStream_->requestStart();
    if (resultOut != oboe::Result::OK) {
        LOGE("Failed to start output stream: %d", static_cast<int>(resultOut));
        stop();
        return false;
    }

    running_.store(true);
    fadeInRemaining_.store(kFadeInSamples, std::memory_order_relaxed);
    consecutiveErrors_.store(0, std::memory_order_relaxed);
    LOGI("Audio engine started: %dHz, in=%dch out=%dch, %d frames/buffer",
         kSampleRate, kInputChannelCount, kOutputChannelCount, kFramesPerBuffer);
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

void NaturaSonicEngine::setNoiseGateMode(int mode) {
    processor_.setNoiseGateMode(mode);
}

void NaturaSonicEngine::setVolumeLimitDb(float limitDb) {
    limiter_.setLimitDb(limitDb);
}

void NaturaSonicEngine::applyProfile(const float* bands, int count, float amplification, int noiseGateMode) {
    processor_.applyProfile(bands, count, amplification, noiseGateMode);
    limiter_.reset();
}

void NaturaSonicEngine::setOutputMuted(bool muted) {
    outputMuted_.store(muted, std::memory_order_relaxed);
    LOGI("Output muted: %s", muted ? "true" : "false");
}

void NaturaSonicEngine::setBalance(float balance) {
    balance_.store(std::clamp(balance, -1.0f, 1.0f), std::memory_order_relaxed);
}

void NaturaSonicEngine::setHeadTrackingEnabled(bool enabled) {
    processor_.setHeadTrackingEnabled(enabled);
}

void NaturaSonicEngine::setHeadTrackingAngles(float azimuthDeg, float pitchDeg, float sensitivity) {
    processor_.setHeadTrackingAngles(azimuthDeg, pitchDeg, sensitivity);
}

void NaturaSonicEngine::setAttentionAgcEnabled(bool enabled) {
    processor_.setAttentionAgcEnabled(enabled);
}

void NaturaSonicEngine::setAttentionGainOffsets(const float* offsets, int count) {
    processor_.setAttentionGainOffsets(offsets, count);
}

void NaturaSonicEngine::setTransientLimiterEnabled(bool enabled) {
    transientLimiter_.setEnabled(enabled);
    LOGI("Transient limiter: %s", enabled ? "enabled" : "disabled");
}

void NaturaSonicEngine::setTransientLimiterThreshold(float thresholdDb) {
    transientLimiter_.setThresholdDb(thresholdDb);
}

bool NaturaSonicEngine::isTransientLimiterActive() const {
    return transientLimiter_.isActive();
}

void NaturaSonicEngine::setAncPhaseEnabled(bool enabled) {
    ancPhaseInverter_.setEnabled(enabled);
}

void NaturaSonicEngine::setAncCancellationGain(float gain) {
    ancPhaseInverter_.setCancellationGain(gain);
}

void NaturaSonicEngine::setAncLpEnabled(bool enabled) {
    ancPhaseInverter_.setLpEnabled(enabled);
}

void NaturaSonicEngine::setAncHpEnabled(bool enabled) {
    ancPhaseInverter_.setHpEnabled(enabled);
}

void NaturaSonicEngine::setAncLpCutoff(float cutoffHz) {
    ancPhaseInverter_.setLpCutoff(cutoffHz);
}

void NaturaSonicEngine::setAncHpCutoff(float cutoffHz) {
    ancPhaseInverter_.setHpCutoff(cutoffHz);
}

void NaturaSonicEngine::setWdrcEnabled(bool enabled) {
    wdrcCompressor_.setEnabled(enabled);
}

void NaturaSonicEngine::setWdrcMakeupGainDb(float gainDb) {
    wdrcCompressor_.setMakeupGainDb(gainDb);
}

void NaturaSonicEngine::setWdrcPreset(int presetIndex) {
    wdrcCompressor_.setPreset(presetIndex);
}

void NaturaSonicEngine::setWdrcBandParams(int bandIndex, float thresholdDb,
                                           float ratio, float attackMs,
                                           float releaseMs) {
    wdrcCompressor_.setBandParams(bandIndex, thresholdDb, ratio,
                                  attackMs, releaseMs);
}

void NaturaSonicEngine::applyWdrcAudiogramProfile(const float* thresholdsDbHl,
                                                    int count) {
    wdrcCompressor_.applyAudiogramProfile(thresholdsDbHl, count);
}

WdrcCompressor::BandGains NaturaSonicEngine::getWdrcActiveGains() const {
    return wdrcCompressor_.getActiveGains();
}

void NaturaSonicEngine::setTinnitusEnabled(bool enabled) {
    tinnitusGenerator_.setEnabled(enabled);
    LOGI("Tinnitus generator: %s", enabled ? "enabled" : "disabled");
}

void NaturaSonicEngine::setTinnitusSoundType(int type) {
    tinnitusGenerator_.setSoundType(type);
}

void NaturaSonicEngine::setTinnitusVolume(float volume) {
    tinnitusGenerator_.setVolume(volume);
}

void NaturaSonicEngine::setTinnitusFrequencyHz(float freqHz) {
    tinnitusGenerator_.setFrequencyHz(freqHz);
}

void NaturaSonicEngine::setAecMode(int mode) {
    int clamped = std::clamp(mode, 0, 2);
    aecMode_.store(clamped, std::memory_order_relaxed);
    aecFilter_.setEnabled(clamped == kAecSoftware);
    LOGI("AEC mode: %d", clamped);
}

int NaturaSonicEngine::getAudioSessionId() const {
    if (inputStream_) {
        return inputStream_->getSessionId();
    }
    return 0;
}

std::vector<float> NaturaSonicEngine::getLatestAudioBuffer() {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    return latestBuffer_;
}

std::vector<float> NaturaSonicEngine::getYamnetAudioBuffer() {
    std::lock_guard<std::mutex> lock(yamnetMutex_);
    if (!yamnetBufferFull_) {
        return std::vector<float>(yamnetBuffer_.begin(),
                                  yamnetBuffer_.begin() + yamnetWritePos_);
    }
    std::vector<float> result(kYamnetBufferSize);
    size_t tail = kYamnetBufferSize - yamnetWritePos_;
    std::copy(yamnetBuffer_.begin() + yamnetWritePos_,
              yamnetBuffer_.end(), result.begin());
    std::copy(yamnetBuffer_.begin(),
              yamnetBuffer_.begin() + yamnetWritePos_,
              result.begin() + tail);
    return result;
}

oboe::DataCallbackResult NaturaSonicEngine::onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    if (!running_.load()) return oboe::DataCallbackResult::Stop;

    lastCallbackNs_.store(
        std::chrono::steady_clock::now().time_since_epoch().count(),
        std::memory_order_relaxed);
    consecutiveErrors_.store(0, std::memory_order_relaxed);

    if (stream == outputStream_.get()) {
        auto* output = static_cast<float*>(audioData);
        int framesToOutput = numFrames;

        std::memset(output, 0, numFrames * kOutputChannelCount * sizeof(float));

        if (inputStream_) {
            auto result = inputStream_->read(
                captureBuffer_.data(), numFrames, 0);

            if (result.value() > 0) {
                int framesToProcess = result.value();
                framesToOutput = framesToProcess;

                ATrace_beginSection("NaturaSonic::DSP");
                auto dspStart = std::chrono::steady_clock::now();

                if (aecMode_.load(std::memory_order_relaxed) == kAecSoftware) {
                    aecFilter_.process(captureBuffer_.data(), framesToProcess);
                }

                dosimetryAnalyzer_.feedAudio(captureBuffer_.data(), framesToProcess);

                ancPhaseInverter_.process(captureBuffer_.data(), framesToProcess);
                wdrcCompressor_.process(captureBuffer_.data(), framesToProcess);
                processor_.process(captureBuffer_.data(), framesToProcess);
                transientLimiter_.process(captureBuffer_.data(), framesToProcess);
                limiter_.process(captureBuffer_.data(), framesToProcess);

                auto dspEnd = std::chrono::steady_clock::now();
                ATrace_endSection();

                float elapsedUs = std::chrono::duration<float, std::micro>(dspEnd - dspStart).count();
                latencyHistoryUs_[latencyWritePos_ % kLatencyWindowSize] = elapsedUs;
                latencyWritePos_++;
                totalFrameCount_.fetch_add(1, std::memory_order_relaxed);

                spectrumAnalyzer_.feedAudio(captureBuffer_.data(), framesToProcess);

                float bal = balance_.load(std::memory_order_relaxed);
                float gainL = std::min(1.0f, 1.0f - bal);
                float gainR = std::min(1.0f, 1.0f + bal);

                if (!outputMuted_.load(std::memory_order_relaxed)) {
                    for (int i = 0; i < framesToProcess; i++) {
                        output[i * 2]     = captureBuffer_[i] * gainL;
                        output[i * 2 + 1] = captureBuffer_[i] * gainR;
                    }
                }

                int fadeRem = fadeInRemaining_.load(std::memory_order_relaxed);
                if (fadeRem > 0) {
                    for (int i = 0; i < framesToProcess; i++) {
                        int pos = kFadeInSamples - fadeRem + i;
                        float ramp = std::min(1.0f, static_cast<float>(pos) / static_cast<float>(kFadeInSamples));
                        output[i * 2]     *= ramp;
                        output[i * 2 + 1] *= ramp;
                    }
                    fadeInRemaining_.store(std::max(0, fadeRem - framesToProcess), std::memory_order_relaxed);
                }

                if (aecMode_.load(std::memory_order_relaxed) == kAecSoftware) {
                    aecFilter_.feedReference(captureBuffer_.data(), framesToProcess);
                }

                {
                    std::lock_guard<std::mutex> lock(bufferMutex_);
                    latestBuffer_.assign(captureBuffer_.begin(),
                                        captureBuffer_.begin() + framesToProcess);
                }

                whisperBridge_.feedAudio(captureBuffer_.data(), framesToProcess);

                voiceAnalyzer_.feedAudio(captureBuffer_.data(),
                                         framesToProcess);

                {
                    std::lock_guard<std::mutex> lock(yamnetMutex_);
                    for (int i = 0; i < framesToProcess; i++) {
                        yamnetBuffer_[yamnetWritePos_] = captureBuffer_[i];
                        yamnetWritePos_++;
                        if (yamnetWritePos_ >= kYamnetBufferSize) {
                            yamnetWritePos_ = 0;
                            yamnetBufferFull_ = true;
                        }
                    }
                }
            }
        }

        if (!outputMuted_.load(std::memory_order_relaxed)) {
            float bal = balance_.load(std::memory_order_relaxed);
            float gL = std::min(1.0f, 1.0f - bal);
            float gR = std::min(1.0f, 1.0f + bal);
            tinnitusGenerator_.generate(output, framesToOutput, kOutputChannelCount, gL, gR);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void NaturaSonicEngine::onErrorAfterClose(
        oboe::AudioStream* stream, oboe::Result result) {
    LOGE("Stream error: %s", oboe::convertToText(result));

    if (!running_.load()) return;

    if (outputStream_) {
        auto xr = outputStream_->getXRunCount();
        if (xr) accumulatedXRuns_.fetch_add(xr.value(), std::memory_order_relaxed);
    }

    int errors = consecutiveErrors_.fetch_add(1, std::memory_order_relaxed) + 1;

    if (errors > kMaxRetries) {
        LOGE("Max retries (%d) exceeded, giving up", kMaxRetries);
        running_.store(false);
        return;
    }

    restartCount_.fetch_add(1, std::memory_order_relaxed);

    int backoffMs = std::min(kBackoffBaseMs * (1 << (errors - 1)), kBackoffMaxMs);
    LOGI("Restart attempt %d/%d after %dms backoff", errors, kMaxRetries, backoffMs);

    std::this_thread::sleep_for(std::chrono::milliseconds(backoffMs));

    stop();
    start();
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

LatencyStats NaturaSonicEngine::getLatencyStats() const {
    LatencyStats stats;
    stats.frameCount = totalFrameCount_.load(std::memory_order_relaxed);

    int count = std::min(static_cast<int>(stats.frameCount), kLatencyWindowSize);
    if (count == 0) return stats;

    float minVal = latencyHistoryUs_[0];
    float maxVal = latencyHistoryUs_[0];
    float sum = 0;
    for (int i = 0; i < count; i++) {
        float v = latencyHistoryUs_[i];
        if (v < minVal) minVal = v;
        if (v > maxVal) maxVal = v;
        sum += v;
    }
    stats.dspMinUs = minVal;
    stats.dspMaxUs = maxVal;
    stats.dspAvgUs = sum / static_cast<float>(count);
    return stats;
}

WatchdogStats NaturaSonicEngine::getWatchdogStats() const {
    WatchdogStats stats;
    stats.lastCallbackNs = lastCallbackNs_.load(std::memory_order_relaxed);

    int32_t currentXRuns = 0;
    if (outputStream_) {
        auto xr = outputStream_->getXRunCount();
        if (xr) currentXRuns = xr.value();
    }
    stats.xRunCount = accumulatedXRuns_.load(std::memory_order_relaxed) + currentXRuns;
    stats.restartCount = restartCount_.load(std::memory_order_relaxed);
    stats.consecutiveErrors = consecutiveErrors_.load(std::memory_order_relaxed);
    stats.isRunning = running_.load(std::memory_order_relaxed);
    return stats;
}

void NaturaSonicEngine::resetWatchdog() {
    accumulatedXRuns_.store(0, std::memory_order_relaxed);
    restartCount_.store(0, std::memory_order_relaxed);
    consecutiveErrors_.store(0, std::memory_order_relaxed);
    LOGI("Watchdog stats reset");
}

void NaturaSonicEngine::startVoiceAnalyzer() {
    voiceAnalyzer_.start();
}

void NaturaSonicEngine::stopVoiceAnalyzer() {
    voiceAnalyzer_.stop();
}

VoiceMetrics NaturaSonicEngine::getVoiceMetrics() const {
    return voiceAnalyzer_.getMetrics();
}

void NaturaSonicEngine::startDosimetry() {
    dosimetryAnalyzer_.start();
}

void NaturaSonicEngine::stopDosimetry() {
    dosimetryAnalyzer_.stop();
}

DosimetryData NaturaSonicEngine::getDosimetryData() const {
    return dosimetryAnalyzer_.getData();
}

void NaturaSonicEngine::setCalibrationOffset(float offsetDb) {
    dosimetryAnalyzer_.setCalibrationOffset(offsetDb);
}

void NaturaSonicEngine::getSpectrumData(float* outBands) const {
    spectrumAnalyzer_.getSpectrum(outBands);
}

void NaturaSonicEngine::openInputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::None)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(kInputChannelCount)
           ->setSampleRate(kSampleRate)
           ->setInputPreset(oboe::InputPreset::VoicePerformance);

    auto result = builder.openStream(inputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));
        inputStream_.reset();
    } else {
        LOGI("Input stream opened: sampleRate=%d, framesPerBurst=%d, bufferCapacity=%d",
             inputStream_->getSampleRate(),
             inputStream_->getFramesPerBurst(),
             inputStream_->getBufferCapacityInFrames());
    }
}

void NaturaSonicEngine::openOutputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::None)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(kOutputChannelCount)
           ->setSampleRate(kSampleRate)
           ->setUsage(oboe::Usage::Media)
           ->setContentType(oboe::ContentType::Music)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    auto result = builder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));
        outputStream_.reset();
    } else {
        LOGI("Output stream opened: sampleRate=%d, framesPerBurst=%d, bufferCapacity=%d",
             outputStream_->getSampleRate(),
             outputStream_->getFramesPerBurst(),
             outputStream_->getBufferCapacityInFrames());
    }
}
