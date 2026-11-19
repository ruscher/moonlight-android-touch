package com.limelight.brp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HostEntriesTest {
    private static HostEntries of(String... names) {
        Integer[] ids = new Integer[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = 100 + i;
        }
        return HostEntries.from(Arrays.asList(ids), Arrays.asList(names), null);
    }

    @Test
    public void bigRemotePlayHostWithThreeScreens() {
        HostEntries e = of("Desktop", "03 - Monitor", "Desktop Low", "01 - Monitor",
                "Steam Big Picture", "02 - Monitor", "Hollow Knight");
        assertTrue(e.canSwitchMonitor());
        List<HostEntries.Entry> m = e.monitors;
        assertEquals(3, m.size());
        assertEquals(1, m.get(0).monitorNumber);
        assertEquals(103, m.get(0).appId);
        assertEquals(3, m.get(2).monitorNumber);
        assertEquals(100, e.desktop.appId);
        assertEquals(102, e.desktopLow.appId);
        assertEquals(104, e.steamBigPicture.appId);
        assertEquals(2, e.currentMonitor(105).monitorNumber);
        assertNull(e.currentMonitor(100)); // Desktop is not a numbered screen
    }

    @Test
    public void oneScreenHidesTheSwitch() {
        HostEntries e = of("Desktop", "01 - Monitor");
        assertFalse(e.canSwitchMonitor());
    }

    @Test
    public void plainSunshineHasNothingExtra() {
        HostEntries e = of("Desktop", "Steam Big Picture");
        assertFalse(e.canSwitchMonitor());
        assertNull(e.desktopLow);
        assertEquals(101, e.steamBigPicture.appId);
    }

    @Test
    public void gameWindowShareOffersNoMonitors() {
        HostEntries e = of("Game Window");
        assertTrue(e.monitors.isEmpty());
        assertFalse(e.canSwitchMonitor());
    }

    @Test
    public void gamesNamedLikeMonitorsAreNotScreens() {
        HostEntries e = of("Monitor Tycoon", "Steam");
        assertTrue(e.monitors.isEmpty());
        assertNull(e.steamBigPicture); // plain "Steam" is not Big Picture
    }
}
