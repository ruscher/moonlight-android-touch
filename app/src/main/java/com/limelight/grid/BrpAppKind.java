package com.limelight.grid;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the entries a Big Remote Play computer offers ("Desktop", "Desktop Low",
 * "Steam Big Picture", "01 - Monitor", ...) from their names, so a card without box
 * art can still show a recognisable icon. Pure Java: no Android dependencies.
 */
public final class BrpAppKind {
    public enum Kind { DESKTOP, MONITOR, STEAM, GAME }

    // "01 - Monitor", "02 – DP-1": Big Remote Play numbers its monitors with two digits
    private static final Pattern NUMBERED = Pattern.compile("^\\s*0?(\\d{1,2})\\s+[-–—]\\s+\\S.*$");
    // "Monitor 2", "Display 1"
    private static final Pattern MONITOR_NUMBER = Pattern.compile("\\b(?:monitor|display|screen)\\s*0*(\\d{1,2})\\b");
    private static final Pattern LOW = Pattern.compile("\\blow\\b");

    public final Kind kind;
    /** Monitor number to draw on the icon, or null. */
    public final String number;
    /** True for low-bandwidth variants such as "Desktop Low" (never for games). */
    public final boolean lowBandwidth;

    private BrpAppKind(Kind kind, String number, boolean lowBandwidth) {
        this.kind = kind;
        this.number = number;
        this.lowBandwidth = lowBandwidth;
    }

    public static BrpAppKind of(String appName) {
        String name = appName == null ? "" : appName.trim().toLowerCase(Locale.ROOT);
        boolean low = LOW.matcher(name).find();

        if (name.contains("steam")) {
            return new BrpAppKind(Kind.STEAM, null, low);
        }
        if (name.contains("desktop")) {
            return new BrpAppKind(Kind.DESKTOP, null, low);
        }

        Matcher numbered = NUMBERED.matcher(name);
        if (numbered.matches()) {
            return new BrpAppKind(Kind.MONITOR, numbered.group(1), low);
        }
        Matcher monitor = MONITOR_NUMBER.matcher(name);
        if (monitor.find()) {
            return new BrpAppKind(Kind.MONITOR, monitor.group(1), low);
        }
        if (name.contains("monitor") || name.contains("display")) {
            return new BrpAppKind(Kind.MONITOR, null, low);
        }

        // A game called "Low ..." is not a low-bandwidth variant
        return new BrpAppKind(Kind.GAME, null, false);
    }
}
