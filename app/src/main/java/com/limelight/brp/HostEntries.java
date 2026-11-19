package com.limelight.brp;

import com.limelight.grid.BrpAppKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What a host's app list offers beyond games: the Big Remote Play entries
 * ("01 - Monitor", "Desktop Low", "Steam Big Picture"). Built from the list the
 * client already fetches over the paired HTTPS channel; no new host service.
 *
 * A normal Sunshine host has none of these entries, so every Big Remote Play
 * action simply stays hidden there. A Game Window share offers no monitor
 * entries either (its private screen is the only one), so the monitor switch is
 * hidden there as well.
 *
 * Pure Java: no Android dependencies, unit-tested.
 */
public final class HostEntries {
    public static final class Entry {
        public final int appId;
        public final String name;
        public final boolean hdrSupported;
        /** Monitor number for "NN - Monitor" entries, otherwise 0. */
        public final int monitorNumber;

        public Entry(int appId, String name, boolean hdrSupported, int monitorNumber) {
            this.appId = appId;
            this.name = name;
            this.hdrSupported = hdrSupported;
            this.monitorNumber = monitorNumber;
        }
    }

    public final List<Entry> monitors;
    public final Entry desktop;
    public final Entry desktopLow;
    public final Entry steamBigPicture;

    private HostEntries(List<Entry> monitors, Entry desktop, Entry desktopLow, Entry steamBigPicture) {
        this.monitors = Collections.unmodifiableList(monitors);
        this.desktop = desktop;
        this.desktopLow = desktopLow;
        this.steamBigPicture = steamBigPicture;
    }

    public static HostEntries empty() {
        return new HostEntries(new ArrayList<Entry>(), null, null, null);
    }

    /** {@code ids}, {@code names} and {@code hdr} are parallel lists from the host's app list. */
    public static HostEntries from(List<Integer> ids, List<String> names, List<Boolean> hdr) {
        List<Entry> monitors = new ArrayList<>();
        Entry desktop = null, desktopLow = null, steam = null;

        for (int i = 0; i < ids.size(); i++) {
            String name = names.get(i);
            BrpAppKind kind = BrpAppKind.of(name);
            boolean hdrOk = hdr != null && i < hdr.size() && Boolean.TRUE.equals(hdr.get(i));

            switch (kind.kind) {
                case MONITOR:
                    // Only numbered entries are the host's screens; a game called
                    // "Monitor Tycoon" is not.
                    int number = parseNumber(kind.number);
                    if (number > 0) {
                        monitors.add(new Entry(ids.get(i), name, hdrOk, number));
                    }
                    break;
                case DESKTOP:
                    if (kind.lowBandwidth) {
                        if (desktopLow == null) desktopLow = new Entry(ids.get(i), name, hdrOk, 0);
                    } else if (desktop == null) {
                        desktop = new Entry(ids.get(i), name, hdrOk, 0);
                    }
                    break;
                case STEAM:
                    if (steam == null && name.toLowerCase(java.util.Locale.ROOT).contains("big picture")) {
                        steam = new Entry(ids.get(i), name, hdrOk, 0);
                    }
                    break;
                default:
                    break;
            }
        }

        Collections.sort(monitors, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Integer.compare(a.monitorNumber, b.monitorNumber);
            }
        });
        return new HostEntries(monitors, desktop, desktopLow, steam);
    }

    /** Switching screens needs at least two monitor entries. */
    public boolean canSwitchMonitor() {
        return monitors.size() >= 2;
    }

    /** The monitor entry being streamed, or null (Desktop, a game, or unknown). */
    public Entry currentMonitor(int currentAppId) {
        for (Entry e : monitors) {
            if (e.appId == currentAppId) {
                return e;
            }
        }
        return null;
    }

    private static int parseNumber(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
