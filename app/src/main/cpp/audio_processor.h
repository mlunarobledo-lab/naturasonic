#pragma once

#include <vector>
#include <atomic>
#include <cmath>

class AudioProcessor {
public:
    AudioProcessor();

    void process(float* buffer, int numFrames);

    void setAmplification(float level);
    void setEqBands(const float* bands, int count);
    void setNoiseSuppressionEnabled(bool enabled);

private:
    void applyAmplification(float* buffer, int numFrames);
    void applyEqualizer(float* buffer, int numFrames);
    void applyNoiseGate(float* buffer, int numFrames);

    std::atomic<float> amplification_{0.5f};
    std::atomic<bool> noiseSuppressionEnabled_{false};

    static constexpr int kMaxEqBands = 10;
    float eqGains_[kMaxEqBands] = {};
    int eqBandCount_ = 5;

    struct BiquadState {
        float x1 = 0, x2 = 0;
        float y1 = 0, y2 = 0;
    };

    struct BiquadCoeffs {
        float b0 = 1, b1 = 0, b2 = 0;
        float a1 = 0, a2 = 0;
    };

    BiquadState eqStates_[kMaxEqBands] = {};
    BiquadCoeffs eqCoeffs_[kMaxEqBands] = {};

    void computeEqCoefficients();
    float processBiquad(float input, BiquadCoeffs& c, BiquadState& s);

    static constexpr float kNoiseGateThreshold = 0.002f;
    static constexpr float kNoiseGateRelease = 0.995f;
    float noiseGateEnvelope_ = 0.0f;

    static constexpr int kSampleRate = 48000;

    static constexpr float kCenterFreqs[] = {
        125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f,
        4000.0f, 6000.0f, 8000.0f, 10000.0f, 12000.0f
    };
};
