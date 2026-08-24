#pragma once

#include <atomic>
#include <cmath>
#include <mutex>

class WdrcCompressor {
public:
    static constexpr int kNumBands = 10;
    static constexpr int kPresetSpeech = 0;
    static constexpr int kPresetMusic = 1;
    static constexpr int kPresetLoudEnv = 2;
    static constexpr int kPresetCustom = 3;

    WdrcCompressor();

    void process(float* buffer, int numFrames);

    void setEnabled(bool enabled);
    void setMakeupGainDb(float gainDb);
    void setPreset(int presetIndex);
    void setBandParams(int bandIndex, float thresholdDb, float ratio,
                       float attackMs, float releaseMs);
    void applyAudiogramProfile(const float* thresholdsDbHl, int count);

    struct BandGains {
        float gains[kNumBands] = {};
    };
    BandGains getActiveGains() const;

private:
    struct BiquadCoeffs {
        float b0 = 1, b1 = 0, b2 = 0;
        float a1 = 0, a2 = 0;
    };

    struct BiquadState {
        float x1 = 0, x2 = 0;
        float y1 = 0, y2 = 0;
    };

    struct BandParams {
        float thresholdDb = -30.0f;
        float ratio = 2.0f;
        float attackCoeff = 0.0f;
        float releaseCoeff = 0.0f;
    };

    struct WdrcSnapshot {
        BiquadCoeffs bandCoeffs[kNumBands];
        BandParams bandParams[kNumBands];
        float kneeWidthDb = 6.0f;
    };

    float processBiquad(float input, const BiquadCoeffs& c, BiquadState& s);
    void computeBandpassCoeffs(BiquadCoeffs& coeffs, float centerFreq, float Q);
    float computeGainDb(float inputLevelDb, const BandParams& params,
                        float kneeWidthDb) const;
    float msToCoeff(float ms) const;

    void applyPresetSpeech(WdrcSnapshot& snap);
    void applyPresetMusic(WdrcSnapshot& snap);
    void applyPresetLoudEnv(WdrcSnapshot& snap);
    void setBandParamsInSnapshot(WdrcSnapshot& snap, int band,
                                 float thresholdDb, float ratio,
                                 float attackMs, float releaseMs);

    std::atomic<bool> enabled_{false};
    std::atomic<float> makeupGainDb_{6.0f};
    std::atomic<float> activeGains_[kNumBands] = {};

    WdrcSnapshot snapshots_[2];
    std::atomic<int> activeIndex_{0};
    std::mutex writeMutex_;

    BiquadState bandStates_[kNumBands] = {};
    float envelopeDb_[kNumBands] = {};

    static constexpr int kSampleRate = 48000;
    static constexpr float kFloorDb = -96.0f;

    static constexpr float kCenterFreqs[kNumBands] = {
        125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f,
        4000.0f, 6000.0f, 8000.0f, 10000.0f, 12000.0f
    };
};
