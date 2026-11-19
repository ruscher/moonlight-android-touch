package com.limelight.brp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QualityAndGestureTest {
    @Test
    public void presetsScaleWithDefault() {
        // Moonlight's default for 1080p60 is 20 Mbps.
        assertEquals(8000, QualityPresets.bitrateFor(QualityPresets.Preset.DATA_SAVER, 20000));
        assertEquals(20000, QualityPresets.bitrateFor(QualityPresets.Preset.BALANCED, 20000));
        assertEquals(30000, QualityPresets.bitrateFor(QualityPresets.Preset.HIGH, 20000));
        assertEquals(50000, QualityPresets.bitrateFor(QualityPresets.Preset.ULTRA, 20000));
        // 4K120 Ultra is capped at the slider maximum.
        assertEquals(QualityPresets.MAX_KBPS, QualityPresets.bitrateFor(QualityPresets.Preset.ULTRA, 113000));
    }

    @Test
    public void presetRecognisedOnlyWhenClose() {
        assertEquals(QualityPresets.Preset.HIGH, QualityPresets.presetFor(30000, 20000));
        assertEquals(QualityPresets.Preset.BALANCED, QualityPresets.presetFor(21000, 20000));
        assertNull(QualityPresets.presetFor(40000, 20000));
    }

    @Test
    public void lowerStepsDownConservatively() {
        QualityPresets.Step s = QualityPresets.lower(20000, 1080, 60);
        assertEquals(12000, s.bitrateKbps);
        assertEquals(0, s.maxHeight);
        assertEquals(0, s.maxFps);

        s = QualityPresets.lower(8000, 1440, 120);
        assertEquals(5000, s.bitrateKbps);
        assertEquals(720, s.maxHeight);
        assertEquals(60, s.maxFps);

        // Never below 1.5 Mbps, never above what it was.
        assertEquals(1500, QualityPresets.lower(2000, 720, 30).bitrateKbps);
        assertEquals(1000, QualityPresets.lower(1000, 720, 30).bitrateKbps);
    }

    @Test
    public void formatsMbps() {
        assertEquals("24 Mbps", QualityPresets.formatMbps(24000));
        assertEquals("1.5 Mbps", QualityPresets.formatMbps(1500));
    }

    @Test
    public void statsQualityInWords() {
        assertEquals(StreamStats.Quality.EXCELLENT, stats(0, 5, 1).quality());
        assertEquals(StreamStats.Quality.GOOD, stats(0.5f, 12, 2).quality());
        assertEquals(StreamStats.Quality.FAIR, stats(0, 80, 5).quality());
        assertEquals(StreamStats.Quality.POOR, stats(8, 20, 5).quality());
        assertEquals("60 FPS · 12 ms · HEVC · 0.5%", stats(0.5f, 12, 2).compactLine());
    }

    private static StreamStats stats(float loss, int rtt, int var) {
        return new StreamStats(1920, 1080, 60, 60, loss, rtt, var, 3, -1, "HEVC", "c2.qti.hevc.decoder", 0);
    }

    @Test
    public void tapNeverOpens() {
        PullDownGesture g = new PullDownGesture(120, 40, 0.8f);
        g.down(100, 10, 0);
        assertFalse(g.move(102, 14, 50));
        assertFalse(g.up(102, 14, 80));
    }

    @Test
    public void longPullOpensOnce() {
        PullDownGesture g = new PullDownGesture(120, 40, 0.8f);
        g.down(100, 10, 0);
        assertFalse(g.move(100, 80, 200));
        assertTrue(g.move(105, 140, 400));
        assertFalse(g.move(105, 200, 500));
        assertFalse(g.up(105, 200, 600));
    }

    @Test
    public void quickFlickOpens() {
        PullDownGesture g = new PullDownGesture(120, 40, 0.8f);
        g.down(100, 10, 0);
        g.move(100, 40, 30);
        assertTrue(g.up(100, 70, 50)); // 60 px in 50 ms
    }

    @Test
    public void slowShortPullDoesNotOpen() {
        PullDownGesture g = new PullDownGesture(120, 40, 0.8f);
        g.down(100, 10, 0);
        assertFalse(g.up(100, 70, 600));
    }

    @Test
    public void sidewaysSlideIsIgnored() {
        PullDownGesture g = new PullDownGesture(120, 40, 0.8f);
        g.down(100, 10, 0);
        assertFalse(g.move(200, 20, 50));
        assertFalse(g.move(200, 200, 100)); // tracking stopped
        assertFalse(g.up(200, 200, 120));
    }
}
