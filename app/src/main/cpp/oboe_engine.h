#pragma once

#include <oboe/Oboe.h>
#include <vector>
#include <mutex>
#include <atomic>
#include "audio_processor.h"
#include "volume_limiter.h"

class NaturaSonicEngine : public oboe::AudioStreamDataCallback,
                           public oboe::AudioStreamErrorCallback {
public:
    NaturaSonicEngine();
    ~NaturaSonicEngine();

    bool start();
    void stop();

    void setAmplification(float level);
    void setEqBands(const float* bands, int count);
    void setNoiseSuppressionEnabled(bool enabled);
    void setVolumeLimitDb(float limitDb);

    std::vector<float> getLatestAudioBuffer();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;

private:
    void openInputStream();
    void openOutputStream();

    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> outputStream_;

    AudioProcessor processor_;
    VolumeLimiter limiter_;

    std::vector<float> captureBuffer_;
    std::vector<float> latestBuffer_;
    std::mutex bufferMutex_;

    std::atomic<bool> running_{false};

    static constexpr int kSampleRate = 48000;
    static constexpr int kChannelCount = 1;
    static constexpr int kFramesPerBuffer = 256;
};
