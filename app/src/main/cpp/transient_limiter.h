#pragma once

#include <atomic>
#include <cmath>

class TransientLimiter {
public:
    TransientLimiter();

    void process(float* buffer, int numFrames);
    void setEnabled(bool enabled);
    void setThresholdDb(float thresholdDb);
    bool isActive() const;

private:
    std::atomic<bool> enabled_{false};
    std::atomic<float> thresholdDb_{-6.0f};
    std::atomic<bool> active_{false};

    static constexpr int kSampleRate = 48000;
    static constexpr int kLookAheadSamples = 96; // 2ms at 48kHz

    float delayLine_[kLookAheadSamples] = {};
    int delayWritePos_ = 0;

    float gainReduction_ = 1.0f;

    static constexpr float kAttackCoeff = 0.95f;   // ~5 samples to reach target
    static constexpr float kReleaseCoeff = 0.9995f; // ~100ms adaptive release

    float dbToLinear(float db) const;
};
