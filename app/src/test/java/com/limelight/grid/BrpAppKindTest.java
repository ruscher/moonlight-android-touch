package com.limelight.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrpAppKindTest {
    @Test
    public void desktopEntries() {
        BrpAppKind desktop = BrpAppKind.of("Desktop");
        assertEquals(BrpAppKind.Kind.DESKTOP, desktop.kind);
        assertFalse(desktop.lowBandwidth);
        assertNull(desktop.number);

        BrpAppKind low = BrpAppKind.of("Desktop Low");
        assertEquals(BrpAppKind.Kind.DESKTOP, low.kind);
        assertTrue(low.lowBandwidth);
    }

    @Test
    public void steamBigPicture() {
        assertEquals(BrpAppKind.Kind.STEAM, BrpAppKind.of("Steam Big Picture").kind);
        assertEquals(BrpAppKind.Kind.STEAM, BrpAppKind.of("  steam  ").kind);
    }

    @Test
    public void numberedMonitors() {
        BrpAppKind first = BrpAppKind.of("01 - Monitor");
        assertEquals(BrpAppKind.Kind.MONITOR, first.kind);
        assertEquals("1", first.number);

        BrpAppKind second = BrpAppKind.of("02 - Monitor");
        assertEquals("2", second.number);

        BrpAppKind tenth = BrpAppKind.of("10 – DP-1");
        assertEquals(BrpAppKind.Kind.MONITOR, tenth.kind);
        assertEquals("10", tenth.number);

        BrpAppKind named = BrpAppKind.of("Monitor 3");
        assertEquals(BrpAppKind.Kind.MONITOR, named.kind);
        assertEquals("3", named.number);

        BrpAppKind lowMonitor = BrpAppKind.of("01 - Monitor Low");
        assertTrue(lowMonitor.lowBandwidth);
    }

    @Test
    public void gamesStayGames() {
        assertEquals(BrpAppKind.Kind.GAME, BrpAppKind.of("Celeste").kind);
        assertEquals(BrpAppKind.Kind.GAME, BrpAppKind.of("007: Legends").kind);
        assertEquals(BrpAppKind.Kind.GAME, BrpAppKind.of("2048").kind);
        // A game whose title has the word "Low" is not a low-bandwidth variant
        assertFalse(BrpAppKind.of("Low Road").lowBandwidth);
        assertFalse(BrpAppKind.of("Below Zero").lowBandwidth);
    }

    @Test
    public void emptyOrMissingName() {
        assertEquals(BrpAppKind.Kind.GAME, BrpAppKind.of("").kind);
        assertEquals(BrpAppKind.Kind.GAME, BrpAppKind.of(null).kind);
    }
}
