#pragma once

#include <oboe/Oboe.h>
#include <vector>
#include <string>
#include <mutex>
#include <atomic>
#include <chrono>
#include "audio_processor.h"
#include "volume_limiter.h"
#include "whisper_bridge.h"
#include "voice_analyzer.h"
#include "aec_filter.h"
#include "dosimetry_analyzer.h"

struct LatencyStats {
    float dspMinUs = 0;
    float dspMaxUs = 0;
    float dspAvgUs = 0;
    int64_t frameCount = 0;
};

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
    void setNoiseGateMode(int mode);
    void setVolumeLimitDb(float limitDb);
    void applyProfile(const float* bands, int count, float amplification, int noiseGateMode);
    void setOutputMuted(bool muted);
    void setHeadTrackingEnabled(bool enabled);
    void setHeadTrackingAngles(float azimuthDeg, float pitchDeg, float sensitivity);
    void setAttentionAgcEnabled(bool enabled);
    void setAttentionGainOffsets(const float* offsets, int count);

    static constexpr int kAecOff = 0;
    static constexpr int kAecSoftware = 1;
    static constexpr int kAecSystem = 2;

    void setAecMode(int mode);
    int getAudioSessionId() const;

    std::vector<float> getLatestAudioBuffer();
    std::vector<float> getYamnetAudioBuffer();
    LatencyStats getLatencyStats() const;

    void startVoiceAnalyzer();
    void stopVoiceAnalyzer();
    VoiceMetrics getVoiceMetrics() const;

    void startDosimetry();
    void stopDosimetry();
    DosimetryData getDosimetryData() const;
    void setCalibrationOffset(float offsetDb);

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
    VoiceAnalyzer voiceAnalyzer_;
    AecFilter aecFilter_;
    DosimetryAnalyzer dosimetryAnalyzer_;
    std::atomic<int> aecMode_{kAecOff};

    std::vector<float> captureBuffer_;
    std::vector<float> latestBuffer_;
    std::mutex bufferMutex_;

    std::vector<float> yamnetBuffer_;
    size_t yamnetWritePos_ = 0;
    bool yamnetBufferFull_ = false;
    std::mutex yamnetMutex_;

    std::atomic<bool> running_{false};
    std::atomic<bool> outputMuted_{false};

    static constexpr int kLatencyWindowSize = 256;
    float latencyHistoryUs_[kLatencyWindowSize] = {};
    int latencyWritePos_ = 0;
    std::atomic<int64_t> totalFrameCount_{0};

    static constexpr int kSampleRate = 48000;
    static constexpr int kChannelCount = 1;
    static constexpr int kFramesPerBuffer = 256;
    static constexpr size_t kYamnetBufferSize = 48000;
};
