#include "audio_processor.h"
#include <cstring>
#include <algorithm>

constexpr float AudioProcessor::kCenterFreqs[];

AudioProcessor::AudioProcessor() {
    for (int i = 0; i < kMaxEqBands; i++) {
        eqGains_[i] = 0.0f;
    }
    computeEqCoefficients();
}

void AudioProcessor::process(float* buffer, int numFrames) {
    applyAmplification(buffer, numFrames);

    if (noiseSuppressionEnabled_.load()) {
        applyNoiseGate(buffer, numFrames);
    }

    applyEqualizer(buffer, numFrames);
}

void AudioProcessor::setAmplification(float level) {
    amplification_.store(std::clamp(level, 0.0f, 1.0f));
}

void AudioProcessor::setEqBands(const float* bands, int count) {
    eqBandCount_ = std::min(count, kMaxEqBands);
    for (int i = 0; i < eqBandCount_; i++) {
        eqGains_[i] = std::clamp(bands[i], -12.0f, 12.0f);
    }
    computeEqCoefficients();
}

void AudioProcessor::setNoiseSuppressionEnabled(bool enabled) {
    noiseSuppressionEnabled_.store(enabled);
}

void AudioProcessor::applyAmplification(float* buffer, int numFrames) {
    float gain = amplification_.load();
    float linearGain = 1.0f + gain * 3.0f;

    for (int i = 0; i < numFrames; i++) {
        buffer[i] *= linearGain;
    }
}

void AudioProcessor::applyEqualizer(float* buffer, int numFrames) {
    for (int band = 0; band < eqBandCount_; band++) {
        if (std::abs(eqGains_[band]) < 0.1f) continue;

        for (int i = 0; i < numFrames; i++) {
            buffer[i] = processBiquad(buffer[i], eqCoeffs_[band], eqStates_[band]);
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

void AudioProcessor::computeEqCoefficients() {
    for (int i = 0; i < eqBandCount_; i++) {
        float f0 = kCenterFreqs[i];
        float gainDb = eqGains_[i];
        float Q = 1.0f;

        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * M_PI * f0 / static_cast<float>(kSampleRate);
        float sinW0 = std::sin(w0);
        float cosW0 = std::cos(w0);
        float alpha = sinW0 / (2.0f * Q);

        float a0 = 1.0f + alpha / A;
        eqCoeffs_[i].b0 = (1.0f + alpha * A) / a0;
        eqCoeffs_[i].b1 = (-2.0f * cosW0) / a0;
        eqCoeffs_[i].b2 = (1.0f - alpha * A) / a0;
        eqCoeffs_[i].a1 = (-2.0f * cosW0) / a0;
        eqCoeffs_[i].a2 = (1.0f - alpha / A) / a0;
    }
}

float AudioProcessor::processBiquad(float input, BiquadCoeffs& c, BiquadState& s) {
    float output = c.b0 * input + c.b1 * s.x1 + c.b2 * s.x2
                   - c.a1 * s.y1 - c.a2 * s.y2;
    s.x2 = s.x1;
    s.x1 = input;
    s.y2 = s.y1;
    s.y1 = output;
    return output;
}
