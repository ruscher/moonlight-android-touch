package com.limelight.brp;

import java.util.Locale;

/**
 * One second of stream measurements, taken by the decoder from the numbers it
 * already keeps for the performance overlay. Immutable; published once per
 * second whether or not the overlay is shown, so the Control Center and the
 * connection-lost screen can show real values.
 *
 * Pure Java: no Android dependencies, unit-tested.
 */
public final class StreamStats {
    public final int width, height;
    public final float receivedFps, renderedFps;
    /** Frames lost on the network, percent of the window. */
    public final float networkDropPercent;
    public final int rttMs, rttVarianceMs;
    public final float decodeMs;
    /** Average host processing latency, or -1 when the host does not report it. */
    public final float hostLatencyMs;
    public final String codec;
    public final String decoderName;
    public final long timestampMs;

    public StreamStats(int width, int height, float receivedFps, float renderedFps,
                       float networkDropPercent, int rttMs, int rttVarianceMs,
                       float decodeMs, float hostLatencyMs, String codec, String decoderName,
                       long timestampMs) {
        this.width = width;
        this.height = height;
        this.receivedFps = receivedFps;
        this.renderedFps = renderedFps;
        this.networkDropPercent = networkDropPercent;
        this.rttMs = rttMs;
        this.rttVarianceMs = rttVarianceMs;
        this.decodeMs = decodeMs;
        this.hostLatencyMs = hostLatencyMs;
        this.codec = codec;
        this.decoderName = decoderName;
        this.timestampMs = timestampMs;
    }

    public enum Quality { EXCELLENT, GOOD, FAIR, POOR }

    /**
     * Connection quality in words. Thresholds are deliberately loose: a 30 ms
     * round trip on Wi-Fi is excellent for streaming, packet loss matters more
     * than latency.
     */
    public Quality quality() {
        if (networkDropPercent >= 5 || rttMs >= 150) {
            return Quality.POOR;
        }
        if (networkDropPercent >= 1.5f || rttMs >= 60 || rttVarianceMs >= 25) {
            return Quality.FAIR;
        }
        if (networkDropPercent >= 0.3f || rttMs >= 20) {
            return Quality.GOOD;
        }
        return Quality.EXCELLENT;
    }

    /** One line for the minimal overlay: "60 FPS · 12 ms · HEVC". */
    public String compactLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(Math.round(renderedFps)).append(" FPS");
        if (rttMs > 0) {
            sb.append(" · ").append(rttMs).append(" ms");
        }
        if (codec != null) {
            sb.append(" · ").append(codec);
        }
        if (networkDropPercent >= 0.1f) {
            sb.append(" · ").append(String.format(Locale.ROOT, "%.1f%%", networkDropPercent));
        }
        return sb.toString();
    }
}
