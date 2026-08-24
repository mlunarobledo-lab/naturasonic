#include "anc_phase_inverter.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AncPhaseInverter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

AncPhaseInverter::AncPhaseInverter() {
    computeLpCoeffs(configs_[0].lpCoeffs, configs_[0].lpCutoffHz);
    computeHpCoeffs(configs_[0].hpCoeffs, configs_[0].hpCutoffHz);
    configs_[1] = configs_[0];
}

void AncPhaseInverter::process(float* buffer, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    float gain = cancellationGain_.load(std::memory_order_relaxed);
    if (gain <= 0.0f) return;

    bool lpOn = lpEnabled_.load(std::memory_order_relaxed);
    bool hpOn = hpEnabled_.load(std::memory_order_relaxed);
    if (!lpOn && !hpOn) return;

    int idx = activeConfigIndex_.load(std::memory_order_acquire);
    const auto& cfg = configs_[idx];

    for (int i = 0; i < numFrames; i++) {
        float sample = buffer[i];
        float cancelled = 0.0f;

        if (lpOn) {
            cancelled += processBiquad(sample, cfg.lpCoeffs, lpState_);
        }
        if (hpOn) {
            cancelled += processBiquad(sample, cfg.hpCoeffs, hpState_);
        }

        buffer[i] = sample - cancelled * gain;
    }
}

void AncPhaseInverter::setEnabled(bool enabled) {
    enabled_.store(enabled, std::memory_order_relaxed);
    LOGI("ANC phase inverter: %s", enabled ? "enabled" : "disabled");
}

void AncPhaseInverter::setCancellationGain(float gain) {
    cancellationGain_.store(std::clamp(gain, 0.0f, 1.0f), std::memory_order_relaxed);
}

void AncPhaseInverter::setLpEnabled(bool enabled) {
    lpEnabled_.store(enabled, std::memory_order_relaxed);
}

void AncPhaseInverter::setHpEnabled(bool enabled) {
    hpEnabled_.store(enabled, std::memory_order_relaxed);
}

void AncPhaseInverter::setLpCutoff(float cutoffHz) {
    float clamped = std::clamp(cutoffHz, kMinLpCutoff, kMaxLpCutoff);
    std::lock_guard<std::mutex> lock(configWriteMutex_);
    int readIdx = activeConfigIndex_.load(std::memory_order_relaxed);
    int writeIdx = 1 - readIdx;
    configs_[writeIdx] = configs_[readIdx];
    configs_[writeIdx].lpCutoffHz = clamped;
    computeLpCoeffs(configs_[writeIdx].lpCoeffs, clamped);
    activeConfigIndex_.store(writeIdx, std::memory_order_release);
}

void AncPhaseInverter::setHpCutoff(float cutoffHz) {
    float clamped = std::clamp(cutoffHz, kMinHpCutoff, kMaxHpCutoff);
    std::lock_guard<std::mutex> lock(configWriteMutex_);
    int readIdx = activeConfigIndex_.load(std::memory_order_relaxed);
    int writeIdx = 1 - readIdx;
    configs_[writeIdx] = configs_[readIdx];
    configs_[writeIdx].hpCutoffHz = clamped;
    computeHpCoeffs(configs_[writeIdx].hpCoeffs, clamped);
    activeConfigIndex_.store(writeIdx, std::memory_order_release);
}

float AncPhaseInverter::processBiquad(float input, const BiquadCoeffs& c, BiquadState& s) {
    float output = c.b0 * input + c.b1 * s.x1 + c.b2 * s.x2
                   - c.a1 * s.y1 - c.a2 * s.y2;
    s.x2 = s.x1; s.x1 = input;
    s.y2 = s.y1; s.y1 = output;
    return output;
}

// Butterworth 2nd order low-pass: bilinear transform with pre-warping
void AncPhaseInverter::computeLpCoeffs(BiquadCoeffs& coeffs, float cutoffHz) {
    float K = std::tan(M_PI * cutoffHz / kSampleRate);
    float K2 = K * K;
    float sqrt2K = 1.41421356f * K; // sqrt(2) * K
    float norm = 1.0f / (1.0f + sqrt2K + K2);

    coeffs.b0 = K2 * norm;
    coeffs.b1 = 2.0f * K2 * norm;
    coeffs.b2 = K2 * norm;
    coeffs.a1 = 2.0f * (K2 - 1.0f) * norm;
    coeffs.a2 = (1.0f - sqrt2K + K2) * norm;
}

// Butterworth 2nd order high-pass: bilinear transform with pre-warping
void AncPhaseInverter::computeHpCoeffs(BiquadCoeffs& coeffs, float cutoffHz) {
    float K = std::tan(M_PI * cutoffHz / kSampleRate);
    float K2 = K * K;
    float sqrt2K = 1.41421356f * K;
    float norm = 1.0f / (1.0f + sqrt2K + K2);

    coeffs.b0 = norm;
    coeffs.b1 = -2.0f * norm;
    coeffs.b2 = norm;
    coeffs.a1 = 2.0f * (K2 - 1.0f) * norm;
    coeffs.a2 = (1.0f - sqrt2K + K2) * norm;
}
