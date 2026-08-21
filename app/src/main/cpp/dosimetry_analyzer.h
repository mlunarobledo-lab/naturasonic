#pragma once

#include <atomic>
#include <cmath>

struct DosimetryData {
    float instantDba = 0.0f;
    float leq = 0.0f;
    float peakDba = 0.0f;
};

class DosimetryAnalyzer {
public:
    DosimetryAnalyzer();

    void start();
    void stop();
    void feedAudio(const float* data, int numFrames);
    DosimetryData getData() const;
    void setCalibrationOffset(float offsetDb);

private:
    static constexpr int kSampleRate = 48000;
    static constexpr int kWindowSize = 4800; // 100ms fast weighting
    static constexpr int kNumSections = 3;

    struct BiquadCoeffs {
        double b0, b1, b2;
        double a1, a2;
    };

    struct BiquadState {
        float w1 = 0.0f, w2 = 0.0f;
    };

    void initAWeightingCoeffs();
    float processSample(float x);
    void resetFilters();

    BiquadCoeffs coeffs_[kNumSections];
    BiquadState state_[kNumSections];
    float gain_ = 1.0f;

    float windowSumSq_ = 0.0f;
    int windowCount_ = 0;

    double leqSumSq_ = 0.0;
    int64_t leqTotalCount_ = 0;

    std::atomic<float> instantDba_{-100.0f};
    std::atomic<float> leq_{-100.0f};
    std::atomic<float> peakDba_{-100.0f};

    std::atomic<float> calibrationOffset_{94.0f};
    std::atomic<bool> active_{false};
};
