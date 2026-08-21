#include "dosimetry_analyzer.h"
#include <cmath>
#include <complex>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

DosimetryAnalyzer::DosimetryAnalyzer() {
    initAWeightingCoeffs();
}

void DosimetryAnalyzer::initAWeightingCoeffs() {
    // IEC 61672 A-weighting analog prototype pole frequencies (Hz)
    const double f1 = 20.598997;
    const double f2 = 107.65265;
    const double f3 = 737.86223;
    const double f4 = 12194.217;
    const double c = 2.0 * kSampleRate;

    // Pre-warp analog frequencies via bilinear transform
    double w1 = c * tan(M_PI * f1 / kSampleRate);
    double w2 = c * tan(M_PI * f2 / kSampleRate);
    double w3 = c * tan(M_PI * f3 / kSampleRate);
    double w4 = c * tan(M_PI * f4 / kSampleRate);

    // Section 1: H(s) = s^2 / (s + w1)^2
    double p1 = (w1 - c) / (w1 + c);
    double g1 = c / (c + w1);
    coeffs_[0].b0 = g1 * g1;
    coeffs_[0].b1 = -2.0 * g1 * g1;
    coeffs_[0].b2 = g1 * g1;
    coeffs_[0].a1 = 2.0 * p1;
    coeffs_[0].a2 = p1 * p1;

    // Section 2: H(s) = s / ((s + w2)(s + w3))
    double d2 = c * c + (w2 + w3) * c + w2 * w3;
    coeffs_[1].b0 = c / d2;
    coeffs_[1].b1 = 0.0;
    coeffs_[1].b2 = -c / d2;
    coeffs_[1].a1 = (-2.0 * c * c + 2.0 * w2 * w3) / d2;
    coeffs_[1].a2 = (c * c - (w2 + w3) * c + w2 * w3) / d2;

    // Section 3: H(s) = s / (s + w4)^2
    double d3 = (c + w4) * (c + w4);
    coeffs_[2].b0 = c / d3;
    coeffs_[2].b1 = 0.0;
    coeffs_[2].b2 = -c / d3;
    coeffs_[2].a1 = 2.0 * (w4 * w4 - c * c) / d3;
    coeffs_[2].a2 = (c - w4) * (c - w4) / d3;

    // Normalize gain to 0 dB at 1 kHz
    double omega = 2.0 * M_PI * 1000.0 / kSampleRate;
    std::complex<double> z1(cos(omega), -sin(omega));
    std::complex<double> z2 = z1 * z1;

    std::complex<double> totalH(1.0, 0.0);
    for (int i = 0; i < kNumSections; i++) {
        std::complex<double> num = coeffs_[i].b0 + coeffs_[i].b1 * z1 + coeffs_[i].b2 * z2;
        std::complex<double> den = 1.0 + coeffs_[i].a1 * z1 + coeffs_[i].a2 * z2;
        totalH *= num / den;
    }
    gain_ = static_cast<float>(1.0 / std::abs(totalH));

    resetFilters();
}

void DosimetryAnalyzer::resetFilters() {
    for (int i = 0; i < kNumSections; i++) {
        state_[i].w1 = 0.0f;
        state_[i].w2 = 0.0f;
    }
}

float DosimetryAnalyzer::processSample(float x) {
    // Direct Form II Transposed cascade
    float y = x;
    for (int i = 0; i < kNumSections; i++) {
        float out = static_cast<float>(coeffs_[i].b0) * y + state_[i].w1;
        state_[i].w1 = static_cast<float>(coeffs_[i].b1) * y
                     - static_cast<float>(coeffs_[i].a1) * out + state_[i].w2;
        state_[i].w2 = static_cast<float>(coeffs_[i].b2) * y
                     - static_cast<float>(coeffs_[i].a2) * out;
        y = out;
    }
    return y * gain_;
}

void DosimetryAnalyzer::start() {
    resetFilters();
    windowSumSq_ = 0.0f;
    windowCount_ = 0;
    leqSumSq_ = 0.0;
    leqTotalCount_ = 0;
    instantDba_.store(-100.0f, std::memory_order_relaxed);
    leq_.store(-100.0f, std::memory_order_relaxed);
    peakDba_.store(-100.0f, std::memory_order_relaxed);
    active_.store(true, std::memory_order_release);
}

void DosimetryAnalyzer::stop() {
    active_.store(false, std::memory_order_release);
}

void DosimetryAnalyzer::feedAudio(const float* data, int numFrames) {
    if (!active_.load(std::memory_order_acquire)) return;

    for (int i = 0; i < numFrames; i++) {
        float filtered = processSample(data[i]);
        windowSumSq_ += filtered * filtered;
        windowCount_++;

        if (windowCount_ >= kWindowSize) {
            float rms = sqrtf(windowSumSq_ / static_cast<float>(windowCount_));
            float offset = calibrationOffset_.load(std::memory_order_relaxed);
            float dba = (rms > 1e-10f)
                ? (20.0f * log10f(rms) + offset)
                : -100.0f;

            instantDba_.store(dba, std::memory_order_relaxed);

            leqSumSq_ += static_cast<double>(windowSumSq_);
            leqTotalCount_ += windowCount_;
            double leqRms = sqrt(leqSumSq_ / static_cast<double>(leqTotalCount_));
            float leqDba = (leqRms > 1e-10)
                ? static_cast<float>(20.0 * log10(leqRms) + offset)
                : -100.0f;
            leq_.store(leqDba, std::memory_order_relaxed);

            float currentPeak = peakDba_.load(std::memory_order_relaxed);
            if (dba > currentPeak) {
                peakDba_.store(dba, std::memory_order_relaxed);
            }

            windowSumSq_ = 0.0f;
            windowCount_ = 0;
        }
    }
}

DosimetryData DosimetryAnalyzer::getData() const {
    return {
        instantDba_.load(std::memory_order_relaxed),
        leq_.load(std::memory_order_relaxed),
        peakDba_.load(std::memory_order_relaxed)
    };
}

void DosimetryAnalyzer::setCalibrationOffset(float offsetDb) {
    calibrationOffset_.store(offsetDb, std::memory_order_relaxed);
}
