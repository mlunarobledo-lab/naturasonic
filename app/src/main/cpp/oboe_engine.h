#pragma once

#include <oboe/Oboe.h>
#include <vector>
#include <string>
#include <mutex>
#include <atomic>
#include "audio_processor.h"
#include "volume_limiter.h"
#include "whisper_bridge.h"

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
    std::vector<float> getYamnetAudioBuffer();

    bool initWhisper(const char* modelPath);
    void releaseWhisper();
    void startWhisperCapture();
    std::string stopWhisperCapture();
    std::string getWhisperText();
    bool isWhisperCapturing() const;
    bool isWhisperModelLoaded() const;

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
    WhisperBridge whisperBridge_;

    std::vector<float> captureBuffer_;
    std::vector<float> latestBuffer_;
    std::mutex bufferMutex_;

    std::vector<float> yamnetBuffer_;
    size_t yamnetWritePos_ = 0;
    bool yamnetBufferFull_ = false;
    std::mutex yamnetMutex_;

    std::atomic<bool> running_{false};

    static constexpr int kSampleRate = 48000;
    static constexpr int kChannelCount = 1;
    static constexpr int kFramesPerBuffer = 256;
    static constexpr size_t kYamnetBufferSize = 48000;
};
