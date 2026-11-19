package com.limelight.brp;

/**
 * Bitrate presets and the "lower quality" step.
 *
 * Sunshine and moonlight-common-c have no way to change the bitrate of a running
 * session: the bitrate is negotiated in RTSP when the stream starts. Every change
 * here is therefore saved and applied by reconnecting, and the UI says so.
 *
 * Presets are multiples of Moonlight's own default for the resolution and frame
 * rate ({@code PreferenceConfiguration.getDefaultBitrate}), so they scale with the
 * stream instead of being fixed numbers.
 *
 * Pure Java: no Android dependencies, unit-tested.
 */
public final class QualityPresets {
    /** Same limits as the settings slider (Kbps). */
    public static final int MIN_KBPS = 500;
    public static final int MAX_KBPS = 150000;

    public enum Preset {
        DATA_SAVER(0.4f),
        BALANCED(1.0f),
        HIGH(1.5f),
        ULTRA(2.5f);

        public final float factor;

        Preset(float factor) {
            this.factor = factor;
        }
    }

    private QualityPresets() {
    }

    public static int clamp(int kbps) {
        return Math.max(MIN_KBPS, Math.min(MAX_KBPS, kbps));
    }

    /** Kbps for a preset, rounded to 0.5 Mbps. */
    public static int bitrateFor(Preset preset, int defaultKbps) {
        int raw = Math.round(defaultKbps * preset.factor);
        return clamp(Math.round(raw / 500f) * 500);
    }

    /** The preset whose bitrate is closest to {@code kbps}, or null when none is within 10%. */
    public static Preset presetFor(int kbps, int defaultKbps) {
        Preset best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Preset p : Preset.values()) {
            int diff = Math.abs(bitrateFor(p, defaultKbps) - kbps);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return bestDiff <= kbps / 10 ? best : null;
    }

    /** Result of {@link #lower}: what to reconnect with. */
    public static final class Step {
        public final int bitrateKbps;
        /** Height to drop to (e.g. 720), or 0 to keep the resolution. */
        public final int maxHeight;
        /** Frame rate to drop to, or 0 to keep it. */
        public final int maxFps;

        Step(int bitrateKbps, int maxHeight, int maxFps) {
            this.bitrateKbps = bitrateKbps;
            this.maxHeight = maxHeight;
            this.maxFps = maxFps;
        }
    }

    /**
     * One conservative step down after a bad connection: 40% less bitrate (never
     * below 1.5 Mbps). Once the bitrate is already low, also stop asking for more
     * than 720p and 60 FPS, because a starved high resolution looks worse than a
     * clean lower one and costs the decoder more.
     */
    public static Step lower(int bitrateKbps, int height, int fps) {
        int next = Math.max(1500, Math.round(bitrateKbps * 0.6f / 500f) * 500);
        next = clamp(Math.min(next, bitrateKbps));
        boolean starved = next <= 5000;
        int maxHeight = starved && height > 720 ? 720 : 0;
        int maxFps = starved && fps > 60 ? 60 : 0;
        return new Step(next, maxHeight, maxFps);
    }

    /** "24 Mbps", "1.5 Mbps". */
    public static String formatMbps(int kbps) {
        if (kbps % 1000 == 0) {
            return (kbps / 1000) + " Mbps";
        }
        return String.format(java.util.Locale.ROOT, "%.1f Mbps", kbps / 1000f);
    }
}
