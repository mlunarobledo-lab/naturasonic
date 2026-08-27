#pragma once

#include <atomic>
#include <cmath>
#include <cstring>

class SpectrumAnalyzer {
public:
    static constexpr int kFftSize = 512;
    static constexpr int kNumBands = 10;

    SpectrumAnalyzer(int sampleRate);

    void feedAudio(const float* data, int numFrames);

    void getSpectrum(float* outBands) const;

private:
    void computeFFT();
    void computeBandMagnitudes();

    int sampleRate_;

    float fftInput_[kFftSize];
    float fftReal_[kFftSize];
    float fftImag_[kFftSize];
    float window_[kFftSize];

    float bandFreqs_[kNumBands];
    int bandBinLow_[kNumBands];
    int bandBinHigh_[kNumBands];

    std::atomic<float> bandMagnitudes_[kNumBands];
};
