#pragma once

#include <atomic>
#include <random>
#include <cmath>

class TinnitusGenerator {
public:
    static constexpr int TYPE_WHITE = 0;
    static constexpr int TYPE_PINK = 1;
    static constexpr int TYPE_BROWN = 2;
    static constexpr int TYPE_PURE_TONE = 3;
    static constexpr int TYPE_NOTCH = 4;

    TinnitusGenerator();

    void generate(float* output, int numFrames);

    void setEnabled(bool enabled);
    void setSoundType(int type);
    void setVolume(float volume);
    void setFrequencyHz(float freqHz);

private:
    float generateWhiteSample();
    float generatePinkSample();
    float generateBrownSample();
    float generatePureToneSample();
    float generateNotchSample();
    void computeNotchCoefficients();

    std::atomic<bool> enabled_{false};
    std::atomic<int> soundType_{TYPE_WHITE};
    std::atomic<float> volume_{0.3f};
    std::atomic<float> frequencyHz_{4000.0f};

    std::mt19937 rng_;

    float pinkB0_ = 0, pinkB1_ = 0, pinkB2_ = 0;
    float brownState_ = 0;
    float phase_ = 0;

    struct NotchBiquad {
        float b0 = 1, b1 = 0, b2 = 0;
        float a1 = 0, a2 = 0;
        float x1 = 0, x2 = 0;
        float y1 = 0, y2 = 0;
    };
    NotchBiquad notch_;
    float lastNotchFreq_ = 0;

    static constexpr int kSampleRate = 48000;
    static constexpr float kMaxOutputLevel = 0.1f;
};
