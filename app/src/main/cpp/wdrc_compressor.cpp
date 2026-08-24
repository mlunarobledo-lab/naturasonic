#include "wdrc_compressor.h"
#include <algorithm>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "WdrcCompressor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

constexpr float WdrcCompressor::kCenterFreqs[];

WdrcCompressor::WdrcCompressor() {
    for (int buf = 0; buf < 2; buf++) {
        for (int i = 0; i < kNumBands; i++) {
            float Q = 1.4f;
            computeBandpassCoeffs(snapshots_[buf].bandCoeffs[i],
                                  kCenterFreqs[i], Q);
        }
        applyPresetSpeech(snapshots_[buf]);
    }
    for (int i = 0; i < kNumBands; i++) {
        envelopeDb_[i] = kFloorDb;
    }
}

void WdrcCompressor::process(float* buffer, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    int idx = activeIndex_.load(std::memory_order_acquire);
    const auto& snap = snapshots_[idx];
    float makeupLinear = std::pow(10.0f,
        makeupGainDb_.load(std::memory_order_relaxed) / 20.0f);

    float bandBuffers[kNumBands];

    for (int i = 0; i < numFrames; i++) {
        float sample = buffer[i];
        float output = 0.0f;

        for (int b = 0; b < kNumBands; b++) {
            float filtered = processBiquad(sample, snap.bandCoeffs[b],
                                           bandStates_[b]);
            bandBuffers[b] = filtered;

            float absVal = std::abs(filtered);
            float inputDb = (absVal > 1e-10f)
                ? 20.0f * std::log10(absVal) : kFloorDb;

            float targetDb = inputDb;
            if (inputDb > envelopeDb_[b]) {
                envelopeDb_[b] += (inputDb - envelopeDb_[b])
                    * snap.bandParams[b].attackCoeff;
            } else {
                envelopeDb_[b] += (inputDb - envelopeDb_[b])
                    * snap.bandParams[b].releaseCoeff;
            }
            envelopeDb_[b] = std::max(envelopeDb_[b], kFloorDb);

            float gainDb = computeGainDb(envelopeDb_[b],
                                         snap.bandParams[b],
                                         snap.kneeWidthDb);
            float gainLinear = std::pow(10.0f, gainDb / 20.0f);

            activeGains_[b].store(gainDb, std::memory_order_relaxed);

            output += filtered * gainLinear;
        }

        buffer[i] = output * makeupLinear;
    }
}

void WdrcCompressor::setEnabled(bool enabled) {
    enabled_.store(enabled, std::memory_order_relaxed);
    if (!enabled) {
        for (int i = 0; i < kNumBands; i++) {
            envelopeDb_[i] = kFloorDb;
            activeGains_[i].store(0.0f, std::memory_order_relaxed);
        }
    }
    LOGI("WDRC: %s", enabled ? "enabled" : "disabled");
}

void WdrcCompressor::setMakeupGainDb(float gainDb) {
    makeupGainDb_.store(std::clamp(gainDb, 0.0f, 24.0f),
                        std::memory_order_relaxed);
}

void WdrcCompressor::setPreset(int presetIndex) {
    std::lock_guard<std::mutex> lock(writeMutex_);
    int readIdx = activeIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;
    snapshots_[writeIdx] = snapshots_[readIdx];

    switch (presetIndex) {
        case kPresetSpeech:
            applyPresetSpeech(snapshots_[writeIdx]);
            break;
        case kPresetMusic:
            applyPresetMusic(snapshots_[writeIdx]);
            break;
        case kPresetLoudEnv:
            applyPresetLoudEnv(snapshots_[writeIdx]);
            break;
        default:
            break;
    }
    activeIndex_.store(writeIdx, std::memory_order_release);
    LOGI("WDRC preset: %d", presetIndex);
}

void WdrcCompressor::setBandParams(int bandIndex, float thresholdDb,
                                    float ratio, float attackMs,
                                    float releaseMs) {
    if (bandIndex < 0 || bandIndex >= kNumBands) return;
    std::lock_guard<std::mutex> lock(writeMutex_);
    int readIdx = activeIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;
    snapshots_[writeIdx] = snapshots_[readIdx];
    setBandParamsInSnapshot(snapshots_[writeIdx], bandIndex,
                            thresholdDb, ratio, attackMs, releaseMs);
    activeIndex_.store(writeIdx, std::memory_order_release);
}

void WdrcCompressor::applyAudiogramProfile(const float* thresholdsDbHl,
                                            int count) {
    std::lock_guard<std::mutex> lock(writeMutex_);
    int readIdx = activeIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;
    snapshots_[writeIdx] = snapshots_[readIdx];

    int n = std::min(count, kNumBands);
    for (int i = 0; i < n; i++) {
        float hl = std::clamp(thresholdsDbHl[i], 0.0f, 80.0f);
        float threshold = -40.0f + hl * 0.5f;
        float ratio = 1.0f + hl / 20.0f;
        float attack = 5.0f;
        float release = (hl > 30.0f) ? 150.0f : 100.0f;
        setBandParamsInSnapshot(snapshots_[writeIdx], i,
                                threshold, ratio, attack, release);
    }
    activeIndex_.store(writeIdx, std::memory_order_release);
    LOGI("WDRC: audiogram profile applied (%d bands)", n);
}

WdrcCompressor::BandGains WdrcCompressor::getActiveGains() const {
    BandGains g;
    for (int i = 0; i < kNumBands; i++) {
        g.gains[i] = activeGains_[i].load(std::memory_order_relaxed);
    }
    return g;
}

float WdrcCompressor::processBiquad(float input, const BiquadCoeffs& c,
                                     BiquadState& s) {
    float output = c.b0 * input + c.b1 * s.x1 + c.b2 * s.x2
                   - c.a1 * s.y1 - c.a2 * s.y2;
    s.x2 = s.x1; s.x1 = input;
    s.y2 = s.y1; s.y1 = output;
    return output;
}

void WdrcCompressor::computeBandpassCoeffs(BiquadCoeffs& coeffs,
                                            float centerFreq, float Q) {
    float w0 = 2.0f * M_PI * centerFreq / static_cast<float>(kSampleRate);
    float sinW0 = std::sin(w0);
    float cosW0 = std::cos(w0);
    float alpha = sinW0 / (2.0f * Q);

    float a0 = 1.0f + alpha;
    coeffs.b0 = (sinW0 / 2.0f) / a0;
    coeffs.b1 = 0.0f;
    coeffs.b2 = -(sinW0 / 2.0f) / a0;
    coeffs.a1 = (-2.0f * cosW0) / a0;
    coeffs.a2 = (1.0f - alpha) / a0;
}

float WdrcCompressor::computeGainDb(float inputLevelDb,
                                     const BandParams& params,
                                     float kneeWidthDb) const {
    float T = params.thresholdDb;
    float R = params.ratio;
    float halfKnee = kneeWidthDb / 2.0f;

    if (R <= 1.0f) return 0.0f;

    if (inputLevelDb <= T - halfKnee) {
        return 0.0f;
    } else if (inputLevelDb >= T + halfKnee) {
        return (1.0f / R - 1.0f) * (inputLevelDb - T);
    } else {
        float diff = inputLevelDb - T + halfKnee;
        return (1.0f / R - 1.0f) * diff * diff / (2.0f * kneeWidthDb);
    }
}

float WdrcCompressor::msToCoeff(float ms) const {
    if (ms <= 0.0f) return 1.0f;
    return 1.0f - std::exp(-2.2f / (ms * kSampleRate / 1000.0f));
}

void WdrcCompressor::setBandParamsInSnapshot(WdrcSnapshot& snap, int band,
                                              float thresholdDb, float ratio,
                                              float attackMs, float releaseMs) {
    snap.bandParams[band].thresholdDb = std::clamp(thresholdDb, -60.0f, 0.0f);
    snap.bandParams[band].ratio = std::clamp(ratio, 1.0f, 10.0f);
    snap.bandParams[band].attackCoeff = msToCoeff(
        std::clamp(attackMs, 1.0f, 100.0f));
    snap.bandParams[band].releaseCoeff = msToCoeff(
        std::clamp(releaseMs, 10.0f, 1000.0f));
}

void WdrcCompressor::applyPresetSpeech(WdrcSnapshot& snap) {
    // Speech: emphasize 500-4000Hz with lower thresholds, higher ratio outside
    for (int i = 0; i < kNumBands; i++) {
        float freq = kCenterFreqs[i];
        float threshold, ratio, attack, release;
        if (freq >= 500.0f && freq <= 4000.0f) {
            threshold = -35.0f;
            ratio = 3.0f;
            attack = 5.0f;
            release = 100.0f;
        } else {
            threshold = -25.0f;
            ratio = 4.0f;
            attack = 10.0f;
            release = 150.0f;
        }
        setBandParamsInSnapshot(snap, i, threshold, ratio, attack, release);
    }
}

void WdrcCompressor::applyPresetMusic(WdrcSnapshot& snap) {
    // Music: gentle compression across all bands, preserve dynamics
    for (int i = 0; i < kNumBands; i++) {
        setBandParamsInSnapshot(snap, i, -25.0f, 1.5f, 15.0f, 200.0f);
    }
}

void WdrcCompressor::applyPresetLoudEnv(WdrcSnapshot& snap) {
    // Loud environment: aggressive compression, low threshold everywhere
    for (int i = 0; i < kNumBands; i++) {
        setBandParamsInSnapshot(snap, i, -40.0f, 5.0f, 3.0f, 80.0f);
    }
}
