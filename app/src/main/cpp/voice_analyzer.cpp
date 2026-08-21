#include "voice_analyzer.h"
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "VoiceAnalyzer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

VoiceAnalyzer::VoiceAnalyzer() : ringBuffer_(kBufferSize, 0.0f) {}

VoiceAnalyzer::~VoiceAnalyzer() {
    stop();
}

void VoiceAnalyzer::start() {
    if (running_.load()) return;
    running_.store(true);
    analysisThread_ = std::thread(&VoiceAnalyzer::analysisLoop, this);
    LOGI("Voice analyzer started");
}

void VoiceAnalyzer::stop() {
    running_.store(false);
    if (analysisThread_.joinable()) {
        analysisThread_.join();
    }
    LOGI("Voice analyzer stopped");
}

void VoiceAnalyzer::feedAudio(const float* data, int numFrames) {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    for (int i = 0; i < numFrames; i++) {
        ringBuffer_[writePos_] = data[i];
        writePos_++;
        if (writePos_ >= static_cast<size_t>(kBufferSize)) {
            writePos_ = 0;
            bufferFull_ = true;
        }
    }
}

VoiceMetrics VoiceAnalyzer::getMetrics() const {
    std::lock_guard<std::mutex> lock(metricsMutex_);
    return currentMetrics_;
}

void VoiceAnalyzer::analysisLoop() {
    while (running_.load()) {
        std::vector<float> snapshot(kBufferSize);
        int availableFrames;
        {
            std::lock_guard<std::mutex> lock(bufferMutex_);
            if (!bufferFull_ && writePos_ < static_cast<size_t>(kSampleRate)) {
                std::this_thread::sleep_for(
                    std::chrono::milliseconds(kAnalysisIntervalMs));
                continue;
            }
            if (bufferFull_) {
                size_t tail = kBufferSize - writePos_;
                std::copy(ringBuffer_.begin() + writePos_,
                          ringBuffer_.end(), snapshot.begin());
                std::copy(ringBuffer_.begin(),
                          ringBuffer_.begin() + writePos_,
                          snapshot.begin() + tail);
                availableFrames = kBufferSize;
            } else {
                std::copy(ringBuffer_.begin(),
                          ringBuffer_.begin() + writePos_,
                          snapshot.begin());
                availableFrames = static_cast<int>(writePos_);
            }
        }

        float pitchHz = detectPitch(snapshot.data(), availableFrames);

        VoiceMetrics metrics;
        if (pitchHz > 0) {
            metrics = computeJitterShimmer(snapshot.data(),
                                           availableFrames, pitchHz);
            metrics.pitchHz = pitchHz;
            metrics.isVoiced = true;
        }

        {
            std::lock_guard<std::mutex> lock(metricsMutex_);
            currentMetrics_ = metrics;
        }

        std::this_thread::sleep_for(
            std::chrono::milliseconds(kAnalysisIntervalMs));
    }
}

float VoiceAnalyzer::detectPitch(const float* data, int length) const {
    if (length < kMaxLag * 2) return 0.0f;

    int analysisLength = std::min(length, kSampleRate);
    const float* analysisData = data + (length - analysisLength);

    int W = analysisLength / 2;
    if (W < kMaxLag) return 0.0f;

    std::vector<float> dn(kMaxLag + 1, 0.0f);
    float cumulativeSum = 0.0f;
    dn[0] = 1.0f;

    for (int tau = 1; tau <= kMaxLag; tau++) {
        float d = computeDifferenceFunction(analysisData, W, tau);
        cumulativeSum += d;
        dn[tau] = (cumulativeSum > 0)
                  ? d * static_cast<float>(tau) / cumulativeSum
                  : 1.0f;
    }

    int bestLag = 0;
    for (int tau = kMinLag; tau <= kMaxLag; tau++) {
        if (dn[tau] < kYinThreshold) {
            while (tau + 1 <= kMaxLag && dn[tau + 1] < dn[tau]) {
                tau++;
            }
            bestLag = tau;
            break;
        }
    }

    if (bestLag == 0) return 0.0f;

    float pitchHz;
    if (bestLag > kMinLag && bestLag < kMaxLag) {
        float s0 = dn[bestLag - 1];
        float s1 = dn[bestLag];
        float s2 = dn[bestLag + 1];
        float denom = s0 - 2.0f * s1 + s2;
        if (std::fabs(denom) > 1e-9f) {
            float delta = (s0 - s2) / (2.0f * denom);
            pitchHz = static_cast<float>(kSampleRate) /
                      (static_cast<float>(bestLag) + delta);
        } else {
            pitchHz = static_cast<float>(kSampleRate) /
                      static_cast<float>(bestLag);
        }
    } else {
        pitchHz = static_cast<float>(kSampleRate) /
                  static_cast<float>(bestLag);
    }

    if (pitchHz < kMinPitchHz || pitchHz > kMaxPitchHz) return 0.0f;

    return pitchHz;
}

float VoiceAnalyzer::computeDifferenceFunction(
        const float* data, int length, int lag) const {
    float sum = 0.0f;
    for (int i = 0; i < length - lag; i++) {
        float diff = data[i] - data[i + lag];
        sum += diff * diff;
    }
    return sum;
}

VoiceMetrics VoiceAnalyzer::computeJitterShimmer(
        const float* data, int length, float pitchHz) const {
    VoiceMetrics metrics;

    float periodSamples = static_cast<float>(kSampleRate) / pitchHz;
    int periodInt = static_cast<int>(std::round(periodSamples));

    int analysisLength = std::min(length, kSampleRate);
    const float* analysisData = data + (length - analysisLength);

    std::vector<float> periods;
    std::vector<float> amplitudes;

    int pos = 0;
    while (pos + periodInt * 2 < analysisLength) {
        int searchStart = pos + periodInt - periodInt / 4;
        int searchEnd = std::min(pos + periodInt + periodInt / 4,
                                 analysisLength - 1);

        int zeroCross = -1;
        for (int i = searchStart; i < searchEnd; i++) {
            if (analysisData[i] <= 0 && analysisData[i + 1] > 0) {
                zeroCross = i;
                break;
            }
        }

        if (zeroCross < 0) {
            pos += periodInt;
            continue;
        }

        float actualPeriod = static_cast<float>(zeroCross - pos);
        if (actualPeriod > periodSamples * 0.5f &&
            actualPeriod < periodSamples * 1.5f) {
            periods.push_back(actualPeriod);

            float maxAmp = 0.0f;
            for (int i = pos; i < zeroCross && i < analysisLength; i++) {
                float absVal = std::fabs(analysisData[i]);
                if (absVal > maxAmp) maxAmp = absVal;
            }
            amplitudes.push_back(maxAmp);
        }

        pos = zeroCross;
    }

    if (periods.size() < 3) return metrics;

    float avgPeriod = 0;
    for (float p : periods) avgPeriod += p;
    avgPeriod /= static_cast<float>(periods.size());

    float jitterSum = 0;
    for (size_t i = 1; i < periods.size(); i++) {
        jitterSum += std::fabs(periods[i] - periods[i - 1]);
    }
    float jitterAvg = jitterSum / static_cast<float>(periods.size() - 1);
    metrics.jitterPercent = (jitterAvg / avgPeriod) * 100.0f;

    float avgAmp = 0;
    for (float a : amplitudes) avgAmp += a;
    avgAmp /= static_cast<float>(amplitudes.size());

    if (avgAmp > 0.001f) {
        float shimmerSum = 0;
        for (size_t i = 1; i < amplitudes.size(); i++) {
            shimmerSum += std::fabs(amplitudes[i] - amplitudes[i - 1]);
        }
        float shimmerAvg = shimmerSum /
                           static_cast<float>(amplitudes.size() - 1);
        metrics.shimmerPercent = (shimmerAvg / avgAmp) * 100.0f;
    }

    return metrics;
}
