package com.limelight.brp;

import com.limelight.nvstream.input.ControllerPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Layouts for the controller drawn on the phone, as data.
 *
 * Positions use upstream's grid: 128 x 72 cells for a 16:9 screen, scaled by the
 * screen height. On a wider screen (a 20:9 phone) the extra width is given to
 * the gap in the middle: LEFT elements stay at the left edge, RIGHT elements move
 * with the right edge, CENTER elements move by half.
 *
 * Every layout keeps its own saved edits (move/resize), so trying FPS does not
 * undo the changes made to Pad. "Classic" is upstream's own layout and keeps the
 * edits people already saved before this version.
 *
 * Pure Java (no Android), unit-tested: no two elements of a layout overlap, all
 * fit in the grid, element ids are unique.
 */
public final class OscLayouts {
    // Element ids: the same numbers as VirtualControllerElement.EID_* (saved edits use them).
    public static final int EID_DPAD = 1, EID_LT = 2, EID_RT = 3, EID_LB = 4, EID_RB = 5,
            EID_A = 6, EID_B = 7, EID_X = 8, EID_Y = 9, EID_BACK = 10, EID_START = 11,
            EID_LS = 12, EID_RS = 13, EID_LSB = 14, EID_RSB = 15, EID_GDB = 16;

    public static final int GRID_W = 128, GRID_H = 72;

    public enum Layout {
        /** A modern gamepad: sticks top-left and bottom-right, like the controllers people hold. */
        PAD("pad", "OSC_pad"),
        /** Shooters: big aim stick and fire under the right thumb, aim (LT) under the left. */
        FPS("fps", "OSC_fps"),
        /** Racing: steering stick on the left, big brake and accelerator pedals on the right. */
        RACE("race", "OSC_race"),
        /** Fighting/arcade: big D-pad, two rows of face buttons and bumpers. */
        FIGHT("fight", "OSC_fight"),
        /** Moonlight's original layout (upstream code, its saved edits). */
        CLASSIC("classic", "OSC");

        public final String key;
        public final String prefsName;

        Layout(String key, String prefsName) {
            this.key = key;
            this.prefsName = prefsName;
        }

        public static Layout fromKey(String key, Layout fallback) {
            for (Layout l : values()) {
                if (l.key.equals(key)) {
                    return l;
                }
            }
            return fallback;
        }
    }

    public enum Kind { DPAD, BUTTON, LEFT_TRIGGER, RIGHT_TRIGGER, LEFT_STICK, RIGHT_STICK }

    public enum Anchor { LEFT, CENTER, RIGHT }

    public static final class Spec {
        public final int id;
        public final Kind kind;
        public final String label;
        /** ControllerPacket flag for buttons, 0 otherwise. */
        public final int flag;
        /** Buttons on the same layer let a finger slide from one to the other. */
        public final int layer;
        public final int x, y, w, h;
        public final Anchor anchor;

        Spec(int id, Kind kind, String label, int flag, int layer, Anchor anchor, int x, int y, int w, int h) {
            this.id = id;
            this.kind = kind;
            this.label = label;
            this.flag = flag;
            this.layer = layer;
            this.anchor = anchor;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Left edge in pixels for a screen {@code widthPx} wide, {@code heightPx} high. */
        public int leftPx(int widthPx, int heightPx) {
            float unit = heightPx / (float) GRID_H;
            int extra = Math.max(0, widthPx - Math.round(GRID_W * unit));
            int base = Math.round(x * unit);
            switch (anchor) {
                case RIGHT: return base + extra;
                case CENTER: return base + extra / 2;
                default: return base;
            }
        }

        public int topPx(int heightPx) {
            return Math.round(y * heightPx / (float) GRID_H);
        }

        public int widthPx(int heightPx) {
            return Math.round(w * heightPx / (float) GRID_H);
        }

        public int heightPx(int heightPx) {
            return Math.round(h * heightPx / (float) GRID_H);
        }
    }

    private OscLayouts() {
    }

    private static final Anchor L = Anchor.LEFT, C = Anchor.CENTER, R = Anchor.RIGHT;

    /**
     * The elements of a layout. {@code flipFaceButtons} swaps A/B and X/Y as the
     * setting does; {@code guide} adds the Guide button. CLASSIC returns an empty
     * list: it is built by upstream's own code.
     */
    public static List<Spec> specs(Layout layout, boolean flipFaceButtons, boolean guide) {
        List<Spec> s = new ArrayList<>();
        int a = flipFaceButtons ? ControllerPacket.B_FLAG : ControllerPacket.A_FLAG;
        int b = flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG;
        int x = flipFaceButtons ? ControllerPacket.Y_FLAG : ControllerPacket.X_FLAG;
        int y = flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG;
        String la = flipFaceButtons ? "B" : "A", lb = flipFaceButtons ? "A" : "B";
        String lx = flipFaceButtons ? "Y" : "X", ly = flipFaceButtons ? "X" : "Y";

        switch (layout) {
            case PAD:
                s.add(stick(EID_LS, Kind.LEFT_STICK, L, 4, 4, 26));
                s.add(dpad(L, 20, 38, 28));
                s.add(trigger(EID_LT, Kind.LEFT_TRIGGER, "LT", L, 2, 58, 14, 11));
                s.add(button(EID_LB, "LB", ControllerPacket.LB_FLAG, 1, C, 34, 2, 22, 8));
                s.add(button(EID_RB, "RB", ControllerPacket.RB_FLAG, 1, C, 72, 2, 22, 8));
                s.add(button(EID_BACK, "BACK", ControllerPacket.BACK_FLAG, 2, C, 40, 24, 14, 7));
                s.add(button(EID_START, "START", ControllerPacket.PLAY_FLAG, 3, C, 74, 24, 14, 7));
                if (guide) {
                    s.add(button(EID_GDB, "GUIDE", ControllerPacket.SPECIAL_BUTTON_FLAG, 4, C, 57, 34, 14, 7));
                }
                s.add(button(EID_Y, ly, y, 1, R, 107, 3, 10, 10));
                s.add(button(EID_X, lx, x, 1, R, 96, 14, 10, 10));
                s.add(button(EID_B, lb, b, 1, R, 118, 14, 10, 10));
                s.add(button(EID_A, la, a, 1, R, 107, 25, 10, 10));
                s.add(stick(EID_RS, Kind.RIGHT_STICK, R, 86, 38, 26));
                s.add(trigger(EID_RT, Kind.RIGHT_TRIGGER, "RT", R, 114, 58, 14, 11));
                break;

            case FPS:
                // Left thumb: move (big stick low), aim (LT) and grenade (LB) above it.
                s.add(stick(EID_LS, Kind.LEFT_STICK, L, 6, 34, 30));
                s.add(trigger(EID_LT, Kind.LEFT_TRIGGER, "LT", L, 2, 6, 16, 12));
                s.add(button(EID_LB, "LB", ControllerPacket.LB_FLAG, 1, L, 22, 6, 14, 9));
                s.add(button(EID_LSB, "L3", ControllerPacket.LS_CLK_FLAG, 5, L, 40, 56, 10, 10));
                s.add(dpad(L, 40, 32, 18));
                s.add(button(EID_BACK, "BACK", ControllerPacket.BACK_FLAG, 2, C, 44, 2, 14, 7));
                s.add(button(EID_START, "START", ControllerPacket.PLAY_FLAG, 3, C, 70, 2, 14, 7));
                if (guide) {
                    s.add(button(EID_GDB, "GUIDE", ControllerPacket.SPECIAL_BUTTON_FLAG, 4, C, 57, 12, 14, 7));
                }
                // Right thumb: aim stick, fire (RT) on top, jump (A) big at the edge.
                s.add(stick(EID_RS, Kind.RIGHT_STICK, R, 76, 30, 26));
                s.add(trigger(EID_RT, Kind.RIGHT_TRIGGER, "RT", R, 110, 6, 16, 12));
                s.add(button(EID_RB, "RB", ControllerPacket.RB_FLAG, 1, R, 92, 6, 14, 9));
                s.add(button(EID_Y, ly, y, 1, R, 104, 22, 10, 10));
                s.add(button(EID_X, lx, x, 1, R, 104, 38, 10, 10));
                s.add(button(EID_B, lb, b, 1, R, 116, 34, 10, 10));
                s.add(button(EID_A, la, a, 1, R, 112, 50, 14, 14));
                s.add(button(EID_RSB, "R3", ControllerPacket.RS_CLK_FLAG, 6, R, 96, 58, 10, 10));
                break;

            case RACE:
                // Steering: a big stick on the left; shift down (LB) above it.
                s.add(stick(EID_LS, Kind.LEFT_STICK, L, 4, 30, 34));
                s.add(button(EID_LB, "LB", ControllerPacket.LB_FLAG, 1, L, 4, 6, 16, 10));
                s.add(dpad(L, 42, 46, 20));
                s.add(button(EID_BACK, "BACK", ControllerPacket.BACK_FLAG, 2, C, 44, 2, 14, 7));
                s.add(button(EID_START, "START", ControllerPacket.PLAY_FLAG, 3, C, 70, 2, 14, 7));
                if (guide) {
                    s.add(button(EID_GDB, "GUIDE", ControllerPacket.SPECIAL_BUTTON_FLAG, 4, C, 57, 12, 14, 7));
                }
                // Pedals: brake (LT) and a bigger accelerator (RT) under the right thumb.
                s.add(trigger(EID_LT, Kind.LEFT_TRIGGER, "LT", R, 86, 40, 16, 26));
                s.add(trigger(EID_RT, Kind.RIGHT_TRIGGER, "RT", R, 106, 34, 20, 32));
                s.add(button(EID_RB, "RB", ControllerPacket.RB_FLAG, 1, R, 86, 4, 12, 10));
                s.add(button(EID_Y, ly, y, 1, R, 100, 4, 10, 10));
                s.add(button(EID_B, lb, b, 1, R, 112, 4, 10, 10));
                s.add(button(EID_X, lx, x, 1, R, 100, 16, 10, 10));
                s.add(button(EID_A, la, a, 1, R, 112, 16, 10, 10));
                break;

            case FIGHT:
                // A big D-pad and an arcade panel: X Y RB over A B RT, LB and LT on top.
                s.add(dpad(L, 6, 28, 36));
                s.add(button(EID_BACK, "BACK", ControllerPacket.BACK_FLAG, 2, C, 44, 2, 14, 7));
                s.add(button(EID_START, "START", ControllerPacket.PLAY_FLAG, 3, C, 70, 2, 14, 7));
                if (guide) {
                    s.add(button(EID_GDB, "GUIDE", ControllerPacket.SPECIAL_BUTTON_FLAG, 4, C, 57, 12, 14, 7));
                }
                s.add(button(EID_LB, "LB", ControllerPacket.LB_FLAG, 1, R, 99, 14, 13, 12));
                s.add(trigger(EID_LT, Kind.LEFT_TRIGGER, "LT", R, 114, 14, 13, 12));
                s.add(button(EID_X, lx, x, 1, R, 84, 30, 13, 13));
                s.add(button(EID_Y, ly, y, 1, R, 99, 30, 13, 13));
                s.add(button(EID_RB, "RB", ControllerPacket.RB_FLAG, 1, R, 114, 30, 13, 13));
                s.add(button(EID_A, la, a, 1, R, 84, 45, 13, 13));
                s.add(button(EID_B, lb, b, 1, R, 99, 45, 13, 13));
                s.add(trigger(EID_RT, Kind.RIGHT_TRIGGER, "RT", R, 114, 45, 13, 13));
                break;

            default:
                return Collections.emptyList();
        }
        return s;
    }

    private static Spec button(int id, String label, int flag, int layer, Anchor an, int x, int y, int w, int h) {
        return new Spec(id, Kind.BUTTON, label, flag, layer, an, x, y, w, h);
    }

    private static Spec trigger(int id, Kind kind, String label, Anchor an, int x, int y, int w, int h) {
        return new Spec(id, kind, label, 0, 1, an, x, y, w, h);
    }

    private static Spec stick(int id, Kind kind, Anchor an, int x, int y, int size) {
        return new Spec(id, kind, null, 0, 0, an, x, y, size, size);
    }

    private static Spec dpad(Anchor an, int x, int y, int size) {
        return new Spec(EID_DPAD, Kind.DPAD, null, 0, 0, an, x, y, size, size);
    }
}
