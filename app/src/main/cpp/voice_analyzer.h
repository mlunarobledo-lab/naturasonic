#pragma once

#include <atomic>
#include <mutex>
#include <thread>
#include <vector>

struct VoiceMetrics {
    float pitchHz = 0.0f;
    float jitterPercent = 0.0f;
    float shimmerPercent = 0.0f;
    bool isVoiced = false;
};

class VoiceAnalyzer {
public:
    VoiceAnalyzer();
    ~VoiceAnalyzer();

    void start();
    void stop();

    void feedAudio(const float* data, int numFrames);
    VoiceMetrics getMetrics() const;

private:
    void analysisLoop();
    float detectPitch(const float* data, int length) const;
    float computeDifferenceFunction(const float* data, int length, int lag) const;
    VoiceMetrics computeJitterShimmer(const float* data, int length, float pitchHz) const;

    static constexpr int kSampleRate = 48000;
    static constexpr int kBufferSize = 96000; // 2s at 48kHz
    static constexpr int kMinPitchHz = 50;
    static constexpr int kMaxPitchHz = 500;
    static constexpr int kMinLag = kSampleRate / kMaxPitchHz; // 96 samples
    static constexpr int kMaxLag = kSampleRate / kMinPitchHz; // 960 samples
    static constexpr float kYinThreshold = 0.15f;
    static constexpr int kAnalysisIntervalMs = 500;

    std::vector<float> ringBuffer_;
    size_t writePos_ = 0;
    bool bufferFull_ = false;
    std::mutex bufferMutex_;

    VoiceMetrics currentMetrics_;
    mutable std::mutex metricsMutex_;

    std::thread analysisThread_;
    std::atomic<bool> running_{false};
};
