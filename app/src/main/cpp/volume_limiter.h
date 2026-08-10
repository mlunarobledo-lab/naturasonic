#pragma once

#include <atomic>
#include <cmath>

class VolumeLimiter {
public:
    explicit VolumeLimiter(float limitDb = 85.0f);

    void process(float* buffer, int numFrames);
    void setLimitDb(float limitDb);

private:
    std::atomic<float> limitDb_;
    float envelope_ = 0.0f;

    static constexpr float kAttack = 0.001f;
    static constexpr float kRelease = 0.05f;
    static constexpr int kSampleRate = 48000;

    float dbToLinear(float db) const;
};
