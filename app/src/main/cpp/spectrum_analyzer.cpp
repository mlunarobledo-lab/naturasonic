#include "spectrum_analyzer.h"
#include <algorithm>

static constexpr float kPi = 3.14159265358979323846f;

static void fftRadix2(float* real, float* imag, int n) {
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) {
            j ^= bit;
        }
        j ^= bit;
        if (i < j) {
            std::swap(real[i], real[j]);
            std::swap(imag[i], imag[j]);
        }
    }

    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * kPi / static_cast<float>(len);
        float wR = std::cos(ang);
        float wI = std::sin(ang);
        for (int i = 0; i < n; i += len) {
            float curR = 1.0f, curI = 0.0f;
            for (int j = 0; j < len / 2; j++) {
                float tR = curR * real[i + j + len / 2] - curI * imag[i + j + len / 2];
                float tI = curR * imag[i + j + len / 2] + curI * real[i + j + len / 2];
                real[i + j + len / 2] = real[i + j] - tR;
                imag[i + j + len / 2] = imag[i + j] - tI;
                real[i + j] += tR;
                imag[i + j] += tI;
                float newR = curR * wR - curI * wI;
                curI = curR * wI + curI * wR;
                curR = newR;
            }
        }
    }
}

SpectrumAnalyzer::SpectrumAnalyzer(int sampleRate)
    : sampleRate_(sampleRate) {

    std::memset(fftInput_, 0, sizeof(fftInput_));
    std::memset(fftReal_, 0, sizeof(fftReal_));
    std::memset(fftImag_, 0, sizeof(fftImag_));

    for (int i = 0; i < kNumBands; i++) {
        bandMagnitudes_[i].store(0.0f, std::memory_order_relaxed);
    }

    for (int i = 0; i < kFftSize; i++) {
        window_[i] = 0.5f * (1.0f - std::cos(2.0f * kPi * i / (kFftSize - 1)));
    }

    static const float freqs[kNumBands] = {
        125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f,
        4000.0f, 6000.0f, 8000.0f, 10000.0f, 12000.0f
    };
    std::memcpy(bandFreqs_, freqs, sizeof(freqs));

    float binWidth = static_cast<float>(sampleRate_) / static_cast<float>(kFftSize);

    for (int i = 0; i < kNumBands; i++) {
        float lo, hi;
        if (i == 0) {
            lo = 0.0f;
        } else {
            lo = std::sqrt(bandFreqs_[i - 1] * bandFreqs_[i]);
        }
        if (i == kNumBands - 1) {
            hi = static_cast<float>(sampleRate_) / 2.0f;
        } else {
            hi = std::sqrt(bandFreqs_[i] * bandFreqs_[i + 1]);
        }

        bandBinLow_[i] = std::max(1, static_cast<int>(lo / binWidth));
        bandBinHigh_[i] = std::min(kFftSize / 2 - 1, static_cast<int>(hi / binWidth));
        if (bandBinHigh_[i] < bandBinLow_[i]) {
            bandBinHigh_[i] = bandBinLow_[i];
        }
    }
}

void SpectrumAnalyzer::feedAudio(const float* data, int numFrames) {
    int copyCount = std::min(numFrames, kFftSize);
    int offset = kFftSize - copyCount;
    if (offset > 0) {
        std::memmove(fftInput_, fftInput_ + copyCount, offset * sizeof(float));
    }
    std::memcpy(fftInput_ + offset, data, copyCount * sizeof(float));

    computeFFT();
    computeBandMagnitudes();
}

void SpectrumAnalyzer::computeFFT() {
    for (int i = 0; i < kFftSize; i++) {
        fftReal_[i] = fftInput_[i] * window_[i];
        fftImag_[i] = 0.0f;
    }
    fftRadix2(fftReal_, fftImag_, kFftSize);
}

void SpectrumAnalyzer::computeBandMagnitudes() {
    float invN = 1.0f / static_cast<float>(kFftSize);
    for (int b = 0; b < kNumBands; b++) {
        float sum = 0.0f;
        int count = 0;
        for (int k = bandBinLow_[b]; k <= bandBinHigh_[b]; k++) {
            float mag = std::sqrt(fftReal_[k] * fftReal_[k] + fftImag_[k] * fftImag_[k]) * invN;
            sum += mag * mag;
            count++;
        }
        float rms = (count > 0) ? std::sqrt(sum / static_cast<float>(count)) : 0.0f;
        float db = (rms > 1e-10f) ? 20.0f * std::log10(rms) + 60.0f : 0.0f;
        float normalized = std::clamp(db / 60.0f, 0.0f, 1.0f);

        bandMagnitudes_[b].store(normalized, std::memory_order_relaxed);
    }
}

void SpectrumAnalyzer::getSpectrum(float* outBands) const {
    for (int i = 0; i < kNumBands; i++) {
        outBands[i] = bandMagnitudes_[i].load(std::memory_order_relaxed);
    }
}
