#include "tinnitus_generator.h"
#include <algorithm>

TinnitusGenerator::TinnitusGenerator()
    : rng_(std::random_device{}()) {
    computeNotchCoefficients();
}

void TinnitusGenerator::generate(float* output, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    float vol = volume_.load(std::memory_order_relaxed) * kMaxOutputLevel;
    if (vol <= 0.0f) return;

    int type = soundType_.load(std::memory_order_relaxed);

    for (int i = 0; i < numFrames; i++) {
        float sample = 0.0f;
        switch (type) {
            case TYPE_WHITE:     sample = generateWhiteSample(); break;
            case TYPE_PINK:      sample = generatePinkSample(); break;
            case TYPE_BROWN:     sample = generateBrownSample(); break;
            case TYPE_PURE_TONE: sample = generatePureToneSample(); break;
            case TYPE_NOTCH:     sample = generateNotchSample(); break;
            default:             sample = generateWhiteSample(); break;
        }
        output[i] += sample * vol;
    }
}

void TinnitusGenerator::setEnabled(bool enabled) {
    enabled_.store(enabled, std::memory_order_relaxed);
    if (!enabled) {
        pinkB0_ = pinkB1_ = pinkB2_ = 0;
        brownState_ = 0;
        phase_ = 0;
        notch_.x1 = notch_.x2 = notch_.y1 = notch_.y2 = 0;
    }
}

void TinnitusGenerator::setSoundType(int type) {
    soundType_.store(std::clamp(type, 0, 4), std::memory_order_relaxed);
    pinkB0_ = pinkB1_ = pinkB2_ = 0;
    brownState_ = 0;
    phase_ = 0;
    notch_.x1 = notch_.x2 = notch_.y1 = notch_.y2 = 0;
}

void TinnitusGenerator::setVolume(float volume) {
    volume_.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
}

void TinnitusGenerator::setFrequencyHz(float freqHz) {
    float clamped = std::clamp(freqHz, 500.0f, 16000.0f);
    frequencyHz_.store(clamped, std::memory_order_relaxed);
    computeNotchCoefficients();
}

float TinnitusGenerator::generateWhiteSample() {
    std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
    return dist(rng_);
}

float TinnitusGenerator::generatePinkSample() {
    float white = generateWhiteSample();
    pinkB0_ = 0.99765f * pinkB0_ + white * 0.0990460f;
    pinkB1_ = 0.96300f * pinkB1_ + white * 0.2965164f;
    pinkB2_ = 0.57000f * pinkB2_ + white * 1.0526913f;
    return (pinkB0_ + pinkB1_ + pinkB2_ + white * 0.1848f) * 0.22f;
}

float TinnitusGenerator::generateBrownSample() {
    float white = generateWhiteSample();
    brownState_ += 0.02f * white;
    brownState_ = std::clamp(brownState_, -1.0f, 1.0f);
    return brownState_ * 3.5f;
}

float TinnitusGenerator::generatePureToneSample() {
    float freq = frequencyHz_.load(std::memory_order_relaxed);
    float sample = std::sin(phase_);
    phase_ += 2.0f * static_cast<float>(M_PI) * freq / static_cast<float>(kSampleRate);
    if (phase_ > 2.0f * static_cast<float>(M_PI)) {
        phase_ -= 2.0f * static_cast<float>(M_PI);
    }
    return sample;
}

float TinnitusGenerator::generateNotchSample() {
    float pink = generatePinkSample();
    float x0 = pink;
    float y0 = notch_.b0 * x0 + notch_.b1 * notch_.x1 + notch_.b2 * notch_.x2
               - notch_.a1 * notch_.y1 - notch_.a2 * notch_.y2;
    notch_.x2 = notch_.x1;
    notch_.x1 = x0;
    notch_.y2 = notch_.y1;
    notch_.y1 = y0;
    return y0;
}

void TinnitusGenerator::computeNotchCoefficients() {
    float freq = frequencyHz_.load(std::memory_order_relaxed);
    if (freq == lastNotchFreq_) return;
    lastNotchFreq_ = freq;

    float Q = 1.41f;
    float w0 = 2.0f * static_cast<float>(M_PI) * freq / static_cast<float>(kSampleRate);
    float cosW0 = std::cos(w0);
    float sinW0 = std::sin(w0);
    float alpha = sinW0 / (2.0f * Q);

    float a0 = 1.0f + alpha;
    notch_.b0 = 1.0f / a0;
    notch_.b1 = -2.0f * cosW0 / a0;
    notch_.b2 = 1.0f / a0;
    notch_.a1 = -2.0f * cosW0 / a0;
    notch_.a2 = (1.0f - alpha) / a0;
}
