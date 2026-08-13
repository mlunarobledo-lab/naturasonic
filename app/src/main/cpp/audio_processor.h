#pragma once

#include <atomic>
#include <cmath>
#include <mutex>

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

    struct BiquadState {
        float x1 = 0, x2 = 0;
        float y1 = 0, y2 = 0;
    };

    struct BiquadCoeffs {
        float b0 = 1, b1 = 0, b2 = 0;
        float a1 = 0, a2 = 0;
    };

    struct EqSnapshot {
        float gains[kMaxEqBands] = {};
        BiquadCoeffs coeffs[kMaxEqBands] = {};
        int bandCount = 5;
    };

    EqSnapshot eqSnapshots_[2];
    std::atomic<int> activeEqIndex_{0};
    std::mutex eqWriteMutex_;

    BiquadState eqStates_[kMaxEqBands] = {};

    void computeEqCoefficients(EqSnapshot& snap);
    float processBiquad(float input, const BiquadCoeffs& c, BiquadState& s);

    static constexpr float kNoiseGateThreshold = 0.002f;
    static constexpr float kNoiseGateRelease = 0.995f;
    float noiseGateEnvelope_ = 0.0f;

    static constexpr int kSampleRate = 48000;

    static constexpr float kCenterFreqs[] = {
        125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f,
        4000.0f, 6000.0f, 8000.0f, 10000.0f, 12000.0f
    };
};
