#include "volume_limiter.h"
#include <algorithm>

VolumeLimiter::VolumeLimiter(float limitDb)
    : limitDb_(limitDb) {}

void VolumeLimiter::process(float* buffer, int numFrames) {
    float threshold = dbToLinear(limitDb_.load());

    float attackCoeff = 1.0f - std::exp(-1.0f / (kAttack * kSampleRate));
    float releaseCoeff = 1.0f - std::exp(-1.0f / (kRelease * kSampleRate));

    for (int i = 0; i < numFrames; i++) {
        float absVal = std::abs(buffer[i]);

        if (absVal > envelope_) {
            envelope_ += attackCoeff * (absVal - envelope_);
        } else {
            envelope_ += releaseCoeff * (absVal - envelope_);
        }

        if (envelope_ > threshold) {
            float gain = threshold / envelope_;
            buffer[i] *= gain;
        }
    }
}

void VolumeLimiter::setLimitDb(float limitDb) {
    limitDb_.store(std::clamp(limitDb, 40.0f, 100.0f));
}

float VolumeLimiter::dbToLinear(float db) const {
    return std::pow(10.0f, (db - 94.0f) / 20.0f);
}
