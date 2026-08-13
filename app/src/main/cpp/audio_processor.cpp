#include "audio_processor.h"
#include <cstring>
#include <algorithm>

constexpr float AudioProcessor::kCenterFreqs[];

AudioProcessor::AudioProcessor() {
    for (int buf = 0; buf < 2; buf++) {
        computeEqCoefficients(eqSnapshots_[buf]);
    }
}

void AudioProcessor::process(float* buffer, int numFrames) {
    int idx = activeEqIndex_.load(std::memory_order_acquire);
    const EqSnapshot& snap = eqSnapshots_[idx];

    applyAmplification(buffer, numFrames, snap.amplification);

    if (snap.noiseSuppression) {
        applyNoiseGate(buffer, numFrames);
    }

    applyEqualizer(buffer, numFrames, snap);
}

void AudioProcessor::setAmplification(float level) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    eqSnapshots_[writeIdx].amplification = std::clamp(level, 0.0f, 1.0f);

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::setEqBands(const float* bands, int count) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    EqSnapshot& snap = eqSnapshots_[writeIdx];
    snap.bandCount = std::min(count, kMaxEqBands);
    for (int i = 0; i < snap.bandCount; i++) {
        snap.gains[i] = std::clamp(bands[i], -12.0f, 12.0f);
    }
    computeEqCoefficients(snap);

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::setNoiseSuppressionEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    eqSnapshots_[writeIdx].noiseSuppression = enabled;

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::applyProfile(const float* bands, int count, float amplification, bool noiseSuppression) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int writeIdx = 1 - activeEqIndex_.load(std::memory_order_acquire);

    EqSnapshot& snap = eqSnapshots_[writeIdx];
    snap.bandCount = std::min(count, kMaxEqBands);
    for (int i = 0; i < snap.bandCount; i++) {
        snap.gains[i] = std::clamp(bands[i], -12.0f, 12.0f);
    }
    computeEqCoefficients(snap);
    snap.amplification = std::clamp(amplification, 0.0f, 1.0f);
    snap.noiseSuppression = noiseSuppression;

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::applyAmplification(float* buffer, int numFrames, float level) {
    float linearGain = 1.0f + level * 3.0f;

    for (int i = 0; i < numFrames; i++) {
        buffer[i] *= linearGain;
    }
}

void AudioProcessor::applyEqualizer(float* buffer, int numFrames, const EqSnapshot& snap) {
    for (int band = 0; band < snap.bandCount; band++) {
        if (std::abs(snap.gains[band]) < 0.1f) continue;

        for (int i = 0; i < numFrames; i++) {
            buffer[i] = processBiquad(buffer[i], snap.coeffs[band], eqStates_[band]);
        }
    }
}

void AudioProcessor::applyNoiseGate(float* buffer, int numFrames) {
    for (int i = 0; i < numFrames; i++) {
        float absVal = std::abs(buffer[i]);
        if (absVal > noiseGateEnvelope_) {
            noiseGateEnvelope_ = absVal;
        } else {
            noiseGateEnvelope_ *= kNoiseGateRelease;
        }

        if (noiseGateEnvelope_ < kNoiseGateThreshold) {
            buffer[i] *= noiseGateEnvelope_ / kNoiseGateThreshold;
        }
    }
}

void AudioProcessor::computeEqCoefficients(EqSnapshot& snap) {
    for (int i = 0; i < snap.bandCount; i++) {
        float f0 = kCenterFreqs[i];
        float gainDb = snap.gains[i];
        float Q = 1.0f;

        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * M_PI * f0 / static_cast<float>(kSampleRate);
        float sinW0 = std::sin(w0);
        float cosW0 = std::cos(w0);
        float alpha = sinW0 / (2.0f * Q);

        float a0 = 1.0f + alpha / A;
        snap.coeffs[i].b0 = (1.0f + alpha * A) / a0;
        snap.coeffs[i].b1 = (-2.0f * cosW0) / a0;
        snap.coeffs[i].b2 = (1.0f - alpha * A) / a0;
        snap.coeffs[i].a1 = (-2.0f * cosW0) / a0;
        snap.coeffs[i].a2 = (1.0f - alpha / A) / a0;
    }
}

float AudioProcessor::processBiquad(float input, const BiquadCoeffs& c, BiquadState& s) {
    float output = c.b0 * input + c.b1 * s.x1 + c.b2 * s.x2
                   - c.a1 * s.y1 - c.a2 * s.y2;
    s.x2 = s.x1;
    s.x1 = input;
    s.y2 = s.y1;
    s.y1 = output;
    return output;
}
