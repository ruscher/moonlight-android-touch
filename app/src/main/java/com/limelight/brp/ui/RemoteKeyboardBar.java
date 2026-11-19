package com.limelight.brp.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.brp.ModifierKeys;
import com.limelight.brp.ModifierKeys.Modifier;

import java.util.HashSet;
import java.util.Set;

/**
 * The keys a phone keyboard does not have, as a bar over the stream: Esc, Tab,
 * Ctrl, Alt, Shift, Win, arrows, Home/End, Page Up/Down, Insert, Delete,
 * Backspace, Enter, Print Screen, and F1-F24 on a second row.
 *
 * Keys are sent as key down when the finger lands and key up when it lifts, so
 * the computer repeats a held arrow or Backspace as a real keyboard would.
 * Modifiers are held (down while the finger is down) or latched (tap on, tap
 * off), as chosen in the Control Center; a latched modifier is shown filled and
 * bold and TalkBack reads it as checked.
 *
 * Every key still down is released by {@link #releaseAll()}, which the stream
 * calls when the bar closes, the Control Center opens, focus is lost, the
 * screen rotates, the app goes to the background or the stream ends.
 *
 * The bar sits at the bottom and, on Android 11+, rises above the phone's
 * keyboard (IME insets). Before Android 11 those insets are not reported to a
 * full-screen window, so the bar sits at the top there instead.
 */
public class RemoteKeyboardBar {
    /** Windows virtual-key codes (what the protocol carries). */
    static final short VK_BACK = 0x08, VK_TAB = 0x09, VK_RETURN = 0x0D, VK_ESCAPE = 0x1B,
            VK_SPACE = 0x20, VK_PRIOR = 0x21, VK_NEXT = 0x22, VK_END = 0x23, VK_HOME = 0x24,
            VK_LEFT = 0x25, VK_UP = 0x26, VK_RIGHT = 0x27, VK_DOWN = 0x28, VK_SNAPSHOT = 0x2C,
            VK_INSERT = 0x2D, VK_DELETE = 0x2E, VK_F1 = 0x70, VK_F13 = 0x7C;

    public interface Listener {
        void onPhoneKeyboardRequested();
        void onCloseRequested();
    }

    private final Context context;
    private final ModifierKeys modifiers;
    private final ModifierKeys.KeySink sink;
    private final Listener listener;
    private final Set<Short> heldKeys = new HashSet<>();

    private final LinearLayout root;
    private final HorizontalScrollView functionRow;
    private final TextView[] modifierViews = new TextView[Modifier.values().length];
    private boolean sticky = true;
    private boolean haptics = true;
    private final boolean atTop;

    public RemoteKeyboardBar(Context context, FrameLayout parent, ModifierKeys.KeySink sink, Listener listener) {
        this.context = context;
        this.sink = sink;
        this.modifiers = new ModifierKeys(sink);
        this.listener = listener;
        this.atTop = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BrpUi.PANEL);
        int p = BrpUi.dp(context, 6);
        root.setPadding(p, p, p, p);
        root.setVisibility(View.GONE);
        root.setClickable(true); // touches on the bar never reach the game

        functionRow = row(functionKeys());
        functionRow.setVisibility(View.GONE);
        root.addView(functionRow);
        root.addView(row(mainKeys()));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                atTop ? Gravity.TOP : Gravity.BOTTOM);
        parent.addView(root, lp);

        if (!atTop) {
            followImeInsets();
        }
    }

    private void followImeInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            int nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setTranslationY(-Math.max(ime, nav));
            return insets;
        });
    }

    public android.view.ViewGroup view() {
        return root;
    }

    public boolean isVisible() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void setVisible(boolean visible) {
        if (!visible) {
            releaseAll();
        }
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            root.bringToFront();
            root.requestApplyInsets();
        }
    }

    public void setSticky(boolean sticky) {
        if (this.sticky != sticky) {
            releaseAll();
        }
        this.sticky = sticky;
    }

    public void setHaptics(boolean haptics) {
        this.haptics = haptics;
    }

    /** Modifier flags the host currently has down from this bar. */
    public byte modifierFlags() {
        return modifiers.flags();
    }

    public void shortcut(short keyCode, Modifier... mods) {
        modifiers.chord(keyCode, mods);
    }

    /** Lifts every key and modifier this bar holds on the host. Safe to call any time. */
    public void releaseAll() {
        for (Short k : heldKeys) {
            sink.key(k, false, modifiers.flags());
        }
        heldKeys.clear();
        modifiers.releaseAll();
        refreshModifierViews();
    }

    // ---- building

    private HorizontalScrollView row(View content) {
        HorizontalScrollView s = new HorizontalScrollView(context);
        s.setHorizontalScrollBarEnabled(false);
        s.setFillViewport(true);
        s.addView(content);
        return s;
    }

    private LinearLayout mainKeys() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);

        TextView ime = key(context.getString(R.string.brp_kbd_phone_keyboard_short));
        ime.setContentDescription(context.getString(R.string.brp_kbd_phone_keyboard));
        ime.setOnClickListener(v -> listener.onPhoneKeyboardRequested());
        l.addView(ime);

        l.addView(sendKey("Esc", VK_ESCAPE, null));
        l.addView(sendKey("Tab", VK_TAB, null));
        l.addView(modifierKey(Modifier.CTRL, "Ctrl"));
        l.addView(modifierKey(Modifier.ALT, "Alt"));
        l.addView(modifierKey(Modifier.SHIFT, "Shift"));
        l.addView(modifierKey(Modifier.META, "Win"));
        l.addView(sendKey("←", VK_LEFT, context.getString(R.string.brp_key_left)));
        l.addView(sendKey("↑", VK_UP, context.getString(R.string.brp_key_up)));
        l.addView(sendKey("↓", VK_DOWN, context.getString(R.string.brp_key_down)));
        l.addView(sendKey("→", VK_RIGHT, context.getString(R.string.brp_key_right)));
        l.addView(sendKey("Home", VK_HOME, null));
        l.addView(sendKey("End", VK_END, null));
        l.addView(sendKey("PgUp", VK_PRIOR, "Page Up"));
        l.addView(sendKey("PgDn", VK_NEXT, "Page Down"));
        l.addView(sendKey("Ins", VK_INSERT, "Insert"));
        l.addView(sendKey("Del", VK_DELETE, "Delete"));
        l.addView(sendKey("⌫", VK_BACK, "Backspace"));
        l.addView(sendKey("⏎", VK_RETURN, "Enter"));
        l.addView(sendKey("␣", VK_SPACE, context.getString(R.string.brp_key_space)));
        l.addView(sendKey("PrtSc", VK_SNAPSHOT, "Print Screen"));

        final TextView fn = key("Fn");
        fn.setOnClickListener(v -> {
            boolean show = functionRow.getVisibility() != View.VISIBLE;
            functionRow.setVisibility(show ? View.VISIBLE : View.GONE);
            BrpUi.setToggle(fn, "Fn", show);
            fn.setContentDescription(context.getString(R.string.brp_kbd_function_keys));
        });
        BrpUi.setToggle(fn, "Fn", false);
        fn.setContentDescription(context.getString(R.string.brp_kbd_function_keys));
        l.addView(fn);

        TextView close = key("✕");
        close.setContentDescription(context.getString(R.string.brp_kbd_hide_bar));
        close.setOnClickListener(v -> listener.onCloseRequested());
        l.addView(close);
        return l;
    }

    private LinearLayout functionKeys() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 12; i++) {
            l.addView(sendKey("F" + (i + 1), (short) (VK_F1 + i), null));
        }
        for (int i = 0; i < 12; i++) {
            l.addView(sendKey("F" + (i + 13), (short) (VK_F13 + i), null));
        }
        return l;
    }

    private TextView key(String label) {
        TextView b = BrpUi.button(context, label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int h = BrpUi.dp(context, 10);
        b.setPadding(h, 0, h, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, BrpUi.dp(context, 48));
        int m = BrpUi.dp(context, 3);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView sendKey(String label, final short code, String spoken) {
        TextView b = key(label);
        if (spoken != null) {
            b.setContentDescription(spoken);
        }
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    feedback(v);
                    if (heldKeys.add(code)) {
                        sink.key(code, true, modifiers.flags());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (heldKeys.remove(code)) {
                        sink.key(code, false, modifiers.flags());
                    }
                    return true;
                default:
                    return true;
            }
        });
        // D-pad centre, keyboard Enter and TalkBack double-tap
        b.setOnClickListener(v -> modifiers.tap(code));
        return b;
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView modifierKey(final Modifier m, final String label) {
        final TextView b = key(label);
        modifierViews[m.ordinal()] = b;
        BrpUi.setToggle(b, label, false);
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    feedback(v);
                    if (sticky) {
                        modifiers.toggle(m);
                    } else {
                        modifiers.press(m);
                    }
                    refreshModifierViews();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!sticky) {
                        modifiers.release(m);
                        refreshModifierViews();
                    }
                    return true;
                default:
                    return true;
            }
        });
        b.setOnClickListener(v -> {
            modifiers.toggle(m);
            refreshModifierViews();
        });
        return b;
    }

    private void refreshModifierViews() {
        for (Modifier m : Modifier.values()) {
            TextView v = modifierViews[m.ordinal()];
            if (v != null) {
                String label = m == Modifier.CTRL ? "Ctrl" : m == Modifier.ALT ? "Alt"
                        : m == Modifier.SHIFT ? "Shift" : "Win";
                boolean on = modifiers.isDown(m);
                BrpUi.setToggle(v, on ? label.toUpperCase(java.util.Locale.ROOT) : label, on);
                v.setContentDescription(label);
            }
        }
    }

    private void feedback(View v) {
        if (haptics) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }
}
