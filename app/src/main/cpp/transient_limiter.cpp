#include "transient_limiter.h"
#include <algorithm>
#include <cstring>

TransientLimiter::TransientLimiter() {
    std::memset(delayLine_, 0, sizeof(delayLine_));
}

void TransientLimiter::process(float* buffer, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) {
        return;
    }

    float threshold = dbToLinear(thresholdDb_.load(std::memory_order_relaxed));
    bool wasActive = false;

    // Two-pass look-ahead algorithm:
    // Pass 1: scan input for the peak in the look-ahead window to pre-compute gain
    // Pass 2: apply gain to delayed samples

    for (int i = 0; i < numFrames; i++) {
        float inputSample = buffer[i];
        float absInput = std::abs(inputSample);

        // Read the oldest sample from delay line (this is what we output)
        float delayedSample = delayLine_[delayWritePos_];

        // Write the new sample into the delay line
        delayLine_[delayWritePos_] = inputSample;
        delayWritePos_ = (delayWritePos_ + 1) % kLookAheadSamples;

        // Compute target gain based on the incoming (future) sample
        float targetGain = 1.0f;
        if (absInput > threshold) {
            targetGain = threshold / absInput;
            wasActive = true;
        }

        // Smooth gain transition
        if (targetGain < gainReduction_) {
            // Attack: fast ramp down (multiply to converge quickly)
            gainReduction_ = gainReduction_ * kAttackCoeff + targetGain * (1.0f - kAttackCoeff);
        } else {
            // Release: slow ramp up
            gainReduction_ = gainReduction_ * kReleaseCoeff + targetGain * (1.0f - kReleaseCoeff);
            if (gainReduction_ > 0.999f) {
                gainReduction_ = 1.0f;
            }
        }

        buffer[i] = delayedSample * gainReduction_;
    }

    active_.store(wasActive || gainReduction_ < 0.999f, std::memory_order_relaxed);
}

void TransientLimiter::setEnabled(bool enabled) {
    if (enabled) {
        enabled_.store(true, std::memory_order_relaxed);
    } else {
        enabled_.store(false, std::memory_order_relaxed);
        gainReduction_ = 1.0f;
        std::memset(delayLine_, 0, sizeof(delayLine_));
        delayWritePos_ = 0;
        active_.store(false, std::memory_order_relaxed);
    }
}

void TransientLimiter::setThresholdDb(float thresholdDb) {
    thresholdDb_.store(std::clamp(thresholdDb, -20.0f, 0.0f), std::memory_order_relaxed);
}

bool TransientLimiter::isActive() const {
    return active_.load(std::memory_order_relaxed);
}

float TransientLimiter::dbToLinear(float db) const {
    return std::pow(10.0f, db / 20.0f);
}
