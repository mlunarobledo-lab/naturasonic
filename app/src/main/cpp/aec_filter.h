#pragma once

#include <vector>
#include <atomic>
#include <cstring>

class AecFilter {
public:
    AecFilter();

    void process(float* captureBuffer, int numFrames);
    void feedReference(const float* outputBuffer, int numFrames);
    void setEnabled(bool enabled);
    bool isEnabled() const;
    void reset();

private:
    static constexpr int kFilterLength = 1024;
    static constexpr int kRefBufferSize = 4096;
    static constexpr float kStepSize = 0.05f;
    static constexpr float kEpsilon = 1e-8f;

    float weights_[kFilterLength] = {};
    float refBuffer_[kRefBufferSize] = {};
    int refWritePos_ = 0;

    std::atomic<bool> enabled_{false};
};
