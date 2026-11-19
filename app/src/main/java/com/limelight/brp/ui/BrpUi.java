package com.limelight.brp.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

/**
 * Look of the stream overlays (Control Center, key bar, handle), drawn in code so
 * nothing here depends on the stream activity's legacy theme.
 *
 * Colours are the desktop app's dark "Gamer" palette (BRP-UI.md): the stream is
 * always on a dark picture, so the overlays are always dark. Text contrast on the
 * card colour meets WCAG AA. State is never colour only: toggles also say
 * "On"/"Off" to TalkBack and selected buttons get a check mark or bold text.
 */
public final class BrpUi {
    public static final int SCRIM = 0x99000000;
    public static final int PANEL = 0xF2151A35;
    public static final int CARD = 0xFF232846;
    public static final int CARD_PRESSED = 0xFF2F3560;
    public static final int TEXT = 0xFFECEEFF;
    public static final int TEXT_2 = 0xFFB8BDDA;
    public static final int ACCENT = 0xFFA48BFF;
    public static final int ACCENT_FILL = 0xFF6F4AFF;
    public static final int FOCUS = 0xFF22D3EE;
    public static final int GOOD = 0xFF8FF0A4;
    public static final int WARN = 0xFFF8E45C;
    public static final int BAD = 0xFFFF9C94;
    public static final int RIPPLE = 0x33FFFFFF;

    private BrpUi() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    private static GradientDrawable round(int color, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            g.setStroke(strokePx, strokeColor);
        }
        return g;
    }

    /** Card background: selected = accent fill, focused = cyan ring (D-pad, TV, keyboard). */
    public static Drawable buttonBackground(Context c, float radiusDp) {
        float r = dp(c, radiusDp);
        int ring = dp(c, 3);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected},
                round(ACCENT_FILL, r, ring, FOCUS));
        s.addState(new int[]{android.R.attr.state_focused}, round(CARD, r, ring, FOCUS));
        s.addState(new int[]{android.R.attr.state_selected}, round(ACCENT_FILL, r, 0, 0));
        s.addState(new int[]{android.R.attr.state_activated}, round(ACCENT_FILL, r, 0, 0));
        s.addState(new int[]{}, round(CARD, r, 0, 0));
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), s, round(0xFFFFFFFF, r, 0, 0));
    }

    /** A 48dp-high button. */
    public static TextView button(Context c, CharSequence text) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setMinWidth(dp(c, 48));
        int h = dp(c, 16);
        b.setPadding(h, dp(c, 8), h, dp(c, 8));
        b.setBackground(buttonBackground(c, 12));
        b.setFocusable(true);
        b.setClickable(true);
        return b;
    }

    /** The highlighted action of a page. */
    public static TextView primaryButton(Context c, CharSequence text) {
        TextView b = button(c, text);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setActivated(true);
        return b;
    }

    /**
     * A button that is on or off: selected look, a check mark in the text, and
     * TalkBack hears it as a checkable control in its current state.
     */
    public static void setToggle(TextView b, CharSequence label, boolean on) {
        b.setText(on ? "✓ " + label : label);
        b.setSelected(on);
        b.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        b.setContentDescription(label);
        b.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView heading(Context c, CharSequence s) {
        TextView t = text(c, s, 13, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setAllCaps(true);
        t.setPadding(0, dp(c, 16), 0, dp(c, 6));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            t.setAccessibilityHeading(true);
        }
        return t;
    }

    public static Drawable panelBackground(Context c) {
        float r = dp(c, 22);
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return g;
    }

    public static Drawable tile(Context c) {
        return round(CARD, dp(c, 12), 0, 0);
    }

    public static Drawable pill(Context c, int color) {
        return round(color, dp(c, 999), 0, 0);
    }
}
