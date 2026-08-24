#pragma once

#include <atomic>
#include <cmath>
#include <mutex>

class AncPhaseInverter {
public:
    AncPhaseInverter();

    void process(float* buffer, int numFrames);

    void setEnabled(bool enabled);
    void setCancellationGain(float gain);
    void setLpEnabled(bool enabled);
    void setHpEnabled(bool enabled);
    void setLpCutoff(float cutoffHz);
    void setHpCutoff(float cutoffHz);

private:
    struct BiquadCoeffs {
        float b0 = 1, b1 = 0, b2 = 0;
        float a1 = 0, a2 = 0;
    };

    struct BiquadState {
        float x1 = 0, x2 = 0;
        float y1 = 0, y2 = 0;
    };

    struct FilterConfig {
        BiquadCoeffs lpCoeffs;
        BiquadCoeffs hpCoeffs;
        float lpCutoffHz = 200.0f;
        float hpCutoffHz = 4000.0f;
    };

    float processBiquad(float input, const BiquadCoeffs& c, BiquadState& s);
    void computeLpCoeffs(BiquadCoeffs& coeffs, float cutoffHz);
    void computeHpCoeffs(BiquadCoeffs& coeffs, float cutoffHz);

    std::atomic<bool> enabled_{false};
    std::atomic<float> cancellationGain_{0.5f};
    std::atomic<bool> lpEnabled_{true};
    std::atomic<bool> hpEnabled_{true};

    FilterConfig configs_[2];
    std::atomic<int> activeConfigIndex_{0};
    std::mutex configWriteMutex_;

    BiquadState lpState_;
    BiquadState hpState_;

    static constexpr int kSampleRate = 48000;
    static constexpr float kMinLpCutoff = 50.0f;
    static constexpr float kMaxLpCutoff = 500.0f;
    static constexpr float kMinHpCutoff = 2000.0f;
    static constexpr float kMaxHpCutoff = 8000.0f;
};
