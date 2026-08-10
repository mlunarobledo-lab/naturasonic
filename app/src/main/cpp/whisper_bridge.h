#pragma once

#include <vector>
#include <string>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include "whisper.h"

class WhisperBridge {
public:
    WhisperBridge();
    ~WhisperBridge();

    bool initModel(const char* modelPath);
    void releaseModel();

    void startCapture();
    std::string stopCapture();

    // Called from audio thread — must be fast, minimal locking
    void feedAudio(const float* data, int numFrames);

    std::string getLatestText();
    bool isCapturing() const { return capturing_.load(); }
    bool isModelLoaded() const { return ctx_ != nullptr; }

private:
    void processingLoop();
    void processSegment(const float* samples48k, int numSamples48k);
    void resample48to16(const float* input, int inputFrames,
                        std::vector<float>& output);

    struct whisper_context* ctx_ = nullptr;

    // Audio accumulation buffer (48kHz samples from Oboe)
    std::vector<float> audioBuffer_;
    std::mutex audioMutex_;

    // Transcription results
    std::string latestText_;
    std::string accumulatedText_;
    std::mutex textMutex_;

    // Processing thread
    std::thread processingThread_;
    std::atomic<bool> capturing_{false};
    std::atomic<bool> threadRunning_{false};
    std::condition_variable cv_;
    std::mutex cvMutex_;

    static constexpr int OBOE_RATE = 48000;
    static constexpr int WHISPER_RATE = 16000;
    static constexpr int RESAMPLE_RATIO = 3;
    static constexpr int SEGMENT_SECONDS = 10;
    static constexpr int SEGMENT_SAMPLES_48K = OBOE_RATE * SEGMENT_SECONDS;
    static constexpr int SEGMENT_SAMPLES_16K = WHISPER_RATE * SEGMENT_SECONDS;
    static constexpr int MIN_SAMPLES_16K = WHISPER_RATE; // 1 second minimum
    static constexpr int BUFFER_RESERVE = OBOE_RATE * 30; // 30s pre-alloc
};
