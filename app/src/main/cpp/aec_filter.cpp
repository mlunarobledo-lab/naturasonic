#include "aec_filter.h"
#include <cmath>
#include <algorithm>

AecFilter::AecFilter() {
    reset();
}

void AecFilter::process(float* captureBuffer, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    for (int i = 0; i < numFrames; i++) {
        int refPos = (refWritePos_ - 1 - i + kRefBufferSize) % kRefBufferSize;

        float echoEstimate = 0.0f;
        float refPower = 0.0f;

        for (int j = 0; j < kFilterLength; j++) {
            int idx = (refPos - j + kRefBufferSize) % kRefBufferSize;
            float refSample = refBuffer_[idx];
            echoEstimate += weights_[j] * refSample;
            refPower += refSample * refSample;
        }

        float error = captureBuffer[i] - echoEstimate;

        float norm = kStepSize / (refPower + kEpsilon);
        for (int j = 0; j < kFilterLength; j++) {
            int idx = (refPos - j + kRefBufferSize) % kRefBufferSize;
            weights_[j] += norm * error * refBuffer_[idx];
        }

        captureBuffer[i] = error;
    }
}

void AecFilter::feedReference(const float* outputBuffer, int numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    for (int i = 0; i < numFrames; i++) {
        refBuffer_[refWritePos_] = outputBuffer[i];
        refWritePos_ = (refWritePos_ + 1) % kRefBufferSize;
    }
}

void AecFilter::setEnabled(bool enabled) {
    if (enabled && !enabled_.load(std::memory_order_relaxed)) {
        reset();
    }
    enabled_.store(enabled, std::memory_order_relaxed);
}

bool AecFilter::isEnabled() const {
    return enabled_.load(std::memory_order_relaxed);
}

void AecFilter::reset() {
    std::memset(weights_, 0, sizeof(weights_));
    std::memset(refBuffer_, 0, sizeof(refBuffer_));
    refWritePos_ = 0;
}
