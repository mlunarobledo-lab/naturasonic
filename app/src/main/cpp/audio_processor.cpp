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

    if (snap.noiseGateMode != kNoiseGateOff) {
        applyAdaptiveNoiseGate(buffer, numFrames, snap.noiseGateMode);
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
    setNoiseGateMode(enabled ? kNoiseGateVoiceFocus : kNoiseGateOff);
}

void AudioProcessor::setNoiseGateMode(int mode) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    eqSnapshots_[writeIdx].noiseGateMode = std::clamp(mode, 0, 2);

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::applyProfile(const float* bands, int count, float amplification, int noiseGateMode) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int writeIdx = 1 - activeEqIndex_.load(std::memory_order_acquire);

    EqSnapshot& snap = eqSnapshots_[writeIdx];
    snap.bandCount = std::min(count, kMaxEqBands);
    for (int i = 0; i < snap.bandCount; i++) {
        snap.gains[i] = std::clamp(bands[i], -12.0f, 12.0f);
    }
    computeEqCoefficients(snap);
    snap.amplification = std::clamp(amplification, 0.0f, 1.0f);
    snap.noiseGateMode = std::clamp(noiseGateMode, 0, 2);

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::setHeadTrackingEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    eqSnapshots_[writeIdx].headTrackingEnabled = enabled;
    if (!enabled) {
        for (int i = 0; i < kMaxEqBands; i++) {
            eqSnapshots_[writeIdx].spatialGainOffsets[i] = 0.0f;
        }
    }
    computeEqCoefficients(eqSnapshots_[writeIdx]);

    activeEqIndex_.store(writeIdx, std::memory_order_release);
}

void AudioProcessor::setHeadTrackingAngles(float azimuthDeg, float pitchDeg, float sensitivity) {
    std::lock_guard<std::mutex> lock(eqWriteMutex_);
    int readIdx = activeEqIndex_.load(std::memory_order_acquire);
    int writeIdx = 1 - readIdx;

    eqSnapshots_[writeIdx] = eqSnapshots_[readIdx];
    EqSnapshot& snap = eqSnapshots_[writeIdx];

    if (!snap.headTrackingEnabled) {
        return;
    }

    float azRad = azimuthDeg * (M_PI / 180.0f);
    float cosFactor = std::cos(azRad);
    float attenuation = (1.0f - cosFactor) * 0.5f;
    float maxAtten = -6.0f * sensitivity;

    for (int i = 0; i < snap.bandCount; i++) {
        float freqWeight;
        if (kCenterFreqs[i] <= 500.0f) {
            freqWeight = 0.1f;
        } else if (kCenterFreqs[i] <= 2000.0f) {
            freqWeight = 0.3f + 0.4f * ((kCenterFreqs[i] - 500.0f) / 1500.0f);
        } else {
            freqWeight = 0.7f + 0.3f * std::min((kCenterFreqs[i] - 2000.0f) / 10000.0f, 1.0f);
        }
        snap.spatialGainOffsets[i] = maxAtten * attenuation * freqWeight;
    }

    computeEqCoefficients(snap);
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
        float effectiveGain = snap.gains[band];
        if (snap.headTrackingEnabled) {
            effectiveGain += snap.spatialGainOffsets[band];
        }
        if (std::abs(effectiveGain) < 0.1f) continue;

        for (int i = 0; i < numFrames; i++) {
            buffer[i] = processBiquad(buffer[i], snap.coeffs[band], eqStates_[band]);
        }
    }
}

void AudioProcessor::applyAdaptiveNoiseGate(float* buffer, int numFrames, int mode) {
    float voiceRatio, minAtten, attackCoeff, releaseCoeff, floorAdapt;
    if (mode == kNoiseGateAggressive) {
        voiceRatio = 2.5f;
        minAtten = 0.02f;
        attackCoeff = 0.05f;
        releaseCoeff = 0.999f;
        floorAdapt = 0.002f;
    } else {
        voiceRatio = 4.0f;
        minAtten = 0.15f;
        attackCoeff = 0.02f;
        releaseCoeff = 0.9995f;
        floorAdapt = 0.001f;
    }

    for (int i = 0; i < numFrames; i++) {
        float absVal = std::abs(buffer[i]);

        ngSignalLevel_ = ngSignalLevel_ * 0.999f + absVal * 0.001f;

        if (ngSignalLevel_ < ngNoiseFloor_ * 2.0f) {
            ngNoiseFloor_ = ngNoiseFloor_ * (1.0f - floorAdapt) + ngSignalLevel_ * floorAdapt;
        }
        ngNoiseFloor_ = std::max(ngNoiseFloor_, 1e-7f);

        float targetGain = (ngSignalLevel_ > ngNoiseFloor_ * voiceRatio) ? 1.0f : minAtten;

        if (targetGain > ngGateGain_) {
            ngGateGain_ += (targetGain - ngGateGain_) * attackCoeff;
        } else {
            ngGateGain_ *= releaseCoeff;
            if (ngGateGain_ < minAtten) ngGateGain_ = minAtten;
        }

        buffer[i] *= ngGateGain_;
    }
}

void AudioProcessor::computeEqCoefficients(EqSnapshot& snap) {
    for (int i = 0; i < snap.bandCount; i++) {
        float f0 = kCenterFreqs[i];
        float gainDb = snap.gains[i];
        if (snap.headTrackingEnabled) {
            gainDb = std::clamp(gainDb + snap.spatialGainOffsets[i], -12.0f, 12.0f);
        }
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
