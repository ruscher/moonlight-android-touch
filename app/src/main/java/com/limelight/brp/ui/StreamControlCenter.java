package com.limelight.brp.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.brp.HostEntries;
import com.limelight.brp.ModifierKeys.Modifier;
import com.limelight.brp.PullDownGesture;
import com.limelight.brp.QualityPresets;
import com.limelight.brp.StreamStats;

import java.util.Locale;

/**
 * The Big Remote Play Stream Control Center: a sheet pulled down from the top
 * of the stream (or opened with Back) with the session's state and everything
 * that can be changed during it.
 *
 * It lives in the stream activity's own window, over the same SurfaceView: it
 * never touches the decoder or its surface, and opening it does not recreate
 * the activity. While it is open the scrim takes every touch, and the stream is
 * first asked to release everything held ({@link StreamSession#suspendInput()}),
 * so no finger, on-screen button or key stays pressed on the computer. A
 * physical controller keeps playing; its D-pad also moves through the menu.
 *
 * What it shows is what the session can really do: a setting that needs a new
 * stream says so and offers "Apply and reconnect"; the screen switch appears
 * only when the host offers two or more monitor entries.
 */
public class StreamControlCenter {
    private static final int TAB_QUICK = 0, TAB_KEYBOARD = 1, TAB_CONTROLLER = 2,
            TAB_DISPLAY = 3, TAB_VIDEO = 4, TAB_SESSION = 5;

    private final Activity activity;
    private final StreamSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout scrim;
    private final LinearLayout panel;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final TextView subtitle;
    private final TextView qualityPill;
    private final TextView[] tabs = new TextView[6];
    private final View handle;

    private boolean open;
    private int currentTab = TAB_QUICK;

    // Quick page values refreshed every second while open
    private TextView vQuality, vLatency, vResolution, vFps, vCodec, vDecode, vDrops, vHost;

    // Video page: pending (unapplied) choices
    private String pendingResolution, pendingFps, pendingFormat;
    private int pendingBitrate;

    // Two-tap confirmation for destructive actions
    private TextView armedButton;
    private CharSequence armedLabel;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!open) {
                return;
            }
            refreshStats();
            handler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    public StreamControlCenter(Activity activity, FrameLayout parent, StreamSession session) {
        this.activity = activity;
        this.session = session;

        // ---- Scrim: covers the stream, takes every touch, tap outside closes.
        scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(BrpUi.SCRIM);
        scrim.setVisibility(View.GONE);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> close());
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Panel: a top sheet, at most 760dp wide, at most ~92% of the height.
        panel = new CappedLinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(BrpUi.panelBackground(activity));
        panel.setClickable(true); // touches on the panel do not close it
        int pad = BrpUi.dp(activity, 16);
        panel.setPadding(pad, BrpUi.dp(activity, 8), pad, pad);
        panel.setElevation(BrpUi.dp(activity, 12));
        scrim.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // Grabber: dragging it up closes, like the handle that opened it.
        View grabber = new View(activity);
        grabber.setBackground(BrpUi.pill(activity, 0x66FFFFFF));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(BrpUi.dp(activity, 40), BrpUi.dp(activity, 4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = BrpUi.dp(activity, 8);
        panel.addView(grabber, glp);

        // ---- Header: title, what is playing, quality in words, close.
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = BrpUi.text(activity, activity.getString(R.string.brp_cc_title), 18, BrpUi.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            title.setAccessibilityHeading(true);
        }
        subtitle = BrpUi.text(activity, "", 13, BrpUi.TEXT_2);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        qualityPill = BrpUi.text(activity, "", 13, BrpUi.TEXT);
        qualityPill.setBackground(BrpUi.pill(activity, BrpUi.CARD));
        qualityPill.setPadding(BrpUi.dp(activity, 12), BrpUi.dp(activity, 6), BrpUi.dp(activity, 12), BrpUi.dp(activity, 6));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.setMarginEnd(BrpUi.dp(activity, 8));
        header.addView(qualityPill, qlp);

        TextView closeButton = BrpUi.button(activity, "✕");
        closeButton.setContentDescription(activity.getString(R.string.brp_cc_close));
        closeButton.setOnClickListener(v -> close());
        header.addView(closeButton);
        panel.addView(header);

        // ---- Tabs
        HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] tabNames = {R.string.brp_tab_quick, R.string.brp_tab_keyboard, R.string.brp_tab_controller,
                R.string.brp_tab_display, R.string.brp_tab_video, R.string.brp_tab_session};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView t = BrpUi.button(activity, activity.getString(tabNames[i]));
            t.setOnClickListener(v -> selectTab(index));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginEnd(BrpUi.dp(activity, 6));
            tabRow.addView(t, tlp);
            tabs[i] = t;
        }
        tabScroll.addView(tabRow);
        LinearLayout.LayoutParams tslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tslp.topMargin = BrpUi.dp(activity, 12);
        panel.addView(tabScroll, tslp);

        // ---- Page content
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, BrpUi.dp(activity, 4), 0, BrpUi.dp(activity, 4));
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- Handle: a small pill near the top edge. Only a deliberate pull opens.
        handle = new View(activity);
        android.graphics.drawable.GradientDrawable pillShape = new android.graphics.drawable.GradientDrawable();
        pillShape.setColor(0x88FFFFFF);
        pillShape.setCornerRadius(BrpUi.dp(activity, 3));
        int insetH = BrpUi.dp(activity, 12), insetTop = BrpUi.dp(activity, 10);
        handle.setBackground(new android.graphics.drawable.InsetDrawable(pillShape,
                insetH, insetTop, insetH, BrpUi.dp(activity, 30) - insetTop - BrpUi.dp(activity, 5)));
        handle.setAlpha(0.6f);
        handle.setContentDescription(activity.getString(R.string.brp_cc_open));
        handle.setFocusable(false);
        // TalkBack double-tap (accessibility click) opens it; touches go through the gesture.
        handle.setOnClickListener(v -> open());
        final PullDownGesture gesture = new PullDownGesture(
                BrpUi.dp(activity, 56), BrpUi.dp(activity, 24), BrpUi.dp(activity, 1) * 0.6f);
        handle.setOnTouchListener((v, e) -> {
            float y = e.getRawY(), x = e.getRawX();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gesture.down(x, y, e.getEventTime());
                    v.setAlpha(1f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (gesture.move(x, y, e.getEventTime())) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        open();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setAlpha(0.6f);
                    if (gesture.up(x, y, e.getEventTime())) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        open();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(0.6f);
                    gesture.cancel();
                    return true;
                default:
                    return true;
            }
        });
        handle.setVisibility(View.GONE);
        // 56x30dp touch area; a few dp below the edge so the system's own top
        // swipe (status bar) keeps working above it.
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                BrpUi.dp(activity, 72), BrpUi.dp(activity, 30), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = BrpUi.dp(activity, 4);
        parent.addView(handle, hlp);
    }

    // ------------------------------------------------------------------ state

    public boolean isOpen() {
        return open;
    }

    /** The handle is shown while a stream is up and the app is not in picture-in-picture. */
    public void setHandleVisible(boolean visible) {
        boolean touch = activity.getPackageManager().hasSystemFeature("android.hardware.touchscreen");
        handle.setVisibility(visible && touch ? View.VISIBLE : View.GONE);
        if (visible) {
            handle.bringToFront();
        }
    }

    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    public void open() {
        if (open) {
            return;
        }
        open = true;
        session.suspendInput();

        applySafeInsets();
        scrim.bringToFront();
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();

        panel.post(() -> {
            panel.setTranslationY(-panel.getHeight());
            panel.animate().translationY(0).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).withLayer().start();
        });

        selectTab(currentTab);
        handler.removeCallbacks(tick);
        handler.post(tick);
        tabs[currentTab].requestFocus();
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        disarm();
        handler.removeCallbacks(tick);
        panel.animate().translationY(-panel.getHeight()).setDuration(160).withLayer().start();
        scrim.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (!open) {
                scrim.setVisibility(View.GONE);
            }
        }).start();
        session.resumeInput();
    }

    /** Closes immediately, e.g. when entering picture-in-picture or leaving the stream. */
    public void closeNow() {
        if (!open) {
            return;
        }
        open = false;
        handler.removeCallbacks(tick);
        scrim.animate().cancel();
        panel.animate().cancel();
        scrim.setVisibility(View.GONE);
        session.resumeInput();
    }

    /** The host's entries were read: refresh what depends on them. */
    public void onHostEntriesChanged() {
        if (open && (currentTab == TAB_DISPLAY || currentTab == TAB_QUICK)) {
            selectTab(currentTab);
        }
    }

    private void applySafeInsets() {
        int w = activity.getWindow().getDecorView().getWidth();
        int max = BrpUi.dp(activity, 760);
        int side = BrpUi.dp(activity, 8);
        int left = 0, right = 0, top = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets wi = activity.getWindow().getDecorView().getRootWindowInsets();
            if (wi != null && wi.getDisplayCutout() != null) {
                left = wi.getDisplayCutout().getSafeInsetLeft();
                right = wi.getDisplayCutout().getSafeInsetRight();
                top = wi.getDisplayCutout().getSafeInsetTop();
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.width = Math.min(max, Math.max(0, w - left - right - 2 * side));
        lp.topMargin = top;
        panel.setLayoutParams(lp);
        int h = activity.getWindow().getDecorView().getHeight();
        ((CappedLinearLayout) panel).setMaxHeight(h > 0 ? (int) ((h - top) * 0.94f) : 0);
    }

    // ------------------------------------------------------------------ tabs

    private void selectTab(int index) {
        currentTab = index;
        disarm();
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == index);
            tabs[i].setTypeface(i == index ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        content.removeAllViews();
        vQuality = vLatency = vResolution = vFps = vCodec = vDecode = vDrops = vHost = null;
        switch (index) {
            case TAB_QUICK: buildQuick(); break;
            case TAB_KEYBOARD: buildKeyboard(); break;
            case TAB_CONTROLLER: buildController(); break;
            case TAB_DISPLAY: buildDisplay(); break;
            case TAB_VIDEO: buildVideo(); break;
            case TAB_SESSION: buildSession(); break;
            default: break;
        }
        scroll.scrollTo(0, 0);
        refreshStats();
    }

    // ------------------------------------------------------------------ helpers

    private String s(int id, Object... args) {
        return activity.getString(id, args);
    }

    private FlowLayout flow() {
        FlowLayout f = new FlowLayout(activity, BrpUi.dp(activity, 8), BrpUi.dp(activity, 8));
        content.addView(f, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return f;
    }

    private void heading(int id) {
        content.addView(BrpUi.heading(activity, s(id)));
    }

    private TextView note(CharSequence text) {
        TextView t = BrpUi.text(activity, text, 13, BrpUi.TEXT_2);
        t.setPadding(0, BrpUi.dp(activity, 6), 0, BrpUi.dp(activity, 2));
        content.addView(t);
        return t;
    }

    private TextView action(FlowLayout f, CharSequence label, View.OnClickListener l) {
        TextView b = BrpUi.button(activity, label);
        b.setOnClickListener(l);
        f.addView(b);
        return b;
    }

    private TextView toggle(FlowLayout f, final CharSequence label, boolean on, final ToggleListener l) {
        final TextView b = BrpUi.button(activity, label);
        BrpUi.setToggle(b, label, on);
        b.setOnClickListener(v -> {
            boolean next = !v.isSelected();
            BrpUi.setToggle(b, label, next);
            l.onToggle(next);
        });
        f.addView(b);
        return b;
    }

    private interface ToggleListener {
        void onToggle(boolean on);
    }

    /** One choice among several (resolution, codec...). */
    private void choices(FlowLayout f, String[] labels, String[] values, String current, final ChoiceListener l) {
        final TextView[] views = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final TextView b = BrpUi.button(activity, labels[i]);
            BrpUi.setToggle(b, labels[i], values[i].equals(current));
            b.setOnClickListener(v -> {
                for (int j = 0; j < views.length; j++) {
                    BrpUi.setToggle(views[j], labels[j], j == idx);
                }
                l.onChoice(values[idx]);
            });
            views[i] = b;
            f.addView(b);
        }
    }

    private interface ChoiceListener {
        void onChoice(String value);
    }

    /**
     * First tap arms the button ("Tap again to …"), a second tap within 4 s runs
     * the action. Avoids a dialog (which would take window focus from the stream).
     */
    private void confirmThen(TextView b, CharSequence armed, Runnable action) {
        if (armedButton == b) {
            disarm();
            action.run();
            return;
        }
        disarm();
        armedButton = b;
        armedLabel = b.getText();
        b.setText(armed);
        b.setTextColor(BrpUi.WARN);
        b.announceForAccessibility(armed);
        handler.postDelayed(this::disarm, 4000);
    }

    private void disarm() {
        if (armedButton != null) {
            armedButton.setText(armedLabel);
            armedButton.setTextColor(BrpUi.TEXT);
            armedButton = null;
        }
    }

    private TextView tile(FlowLayout f, int labelId) {
        LinearLayout t = new LinearLayout(activity);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(BrpUi.tile(activity));
        int p = BrpUi.dp(activity, 12);
        t.setPadding(p, BrpUi.dp(activity, 8), p, BrpUi.dp(activity, 8));
        t.setFocusable(true); // TalkBack and D-pad read "label, value" together
        TextView label = BrpUi.text(activity, s(labelId), 12, BrpUi.TEXT_2);
        TextView value = BrpUi.text(activity, "—", 16, BrpUi.TEXT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(label);
        t.addView(value);
        f.addView(t);
        return value;
    }

    private String currentScreenName() {
        HostEntries entries = session.hostEntries();
        if (entries != null) {
            HostEntries.Entry m = entries.currentMonitor(session.appId());
            if (m != null) {
                return m.name;
            }
        }
        return session.appName();
    }

    // ------------------------------------------------------------------ Quick

    private void buildQuick() {
        heading(R.string.brp_quick_actions);
        FlowLayout f = flow();
        toggle(f, s(R.string.brp_action_keyboard), session.isKeyboardBarVisible(), on -> {
            session.setKeyboardBarVisible(on);
            if (on) {
                close();
                session.showPhoneKeyboard();
            }
        });
        toggle(f, s(R.string.brp_action_controller), session.onScreenControllerVisible(),
                session::setOnScreenControllerVisible);
        final String[] perfLabels = {s(R.string.brp_perf_off), s(R.string.brp_perf_minimal), s(R.string.brp_perf_detailed)};
        final TextView stats = action(f, s(R.string.brp_action_statistics_mode, perfLabels[session.perfOverlayMode()]), null);
        stats.setOnClickListener(v -> {
            int next = (session.perfOverlayMode() + 1) % 3;
            session.setPerfOverlayMode(next);
            stats.setText(s(R.string.brp_action_statistics_mode, perfLabels[next]));
        });
        HostEntries entries = session.hostEntries();
        if (entries != null && entries.canSwitchMonitor()) {
            action(f, s(R.string.brp_action_switch_screen), v -> selectTab(TAB_DISPLAY));
        }
        if (session.canStretch()) {
            toggle(f, s(R.string.brp_action_stretch), session.isStretched(), session::setStretched);
        }
        final TextView fix = action(f, s(R.string.brp_action_lower_quality), null);
        fix.setOnClickListener(v -> confirmThen(fix, s(R.string.brp_confirm_reconnect),
                session::lowerQualityAndReconnect));
        TextView disconnect = BrpUi.primaryButton(activity, s(R.string.brp_action_disconnect));
        disconnect.setOnClickListener(v -> session.disconnect());
        f.addView(disconnect);
        note(s(R.string.brp_quick_note_disconnect));

        heading(R.string.brp_stat_heading);
        FlowLayout tiles = flow();
        tiles.setChildWidth(BrpUi.dp(activity, 150));
        vQuality = tile(tiles, R.string.brp_stat_connection);
        vLatency = tile(tiles, R.string.brp_stat_network_latency);
        vResolution = tile(tiles, R.string.brp_stat_resolution);
        vFps = tile(tiles, R.string.brp_stat_fps);
        vCodec = tile(tiles, R.string.brp_stat_codec);
        TextView bitrate = tile(tiles, R.string.brp_stat_bitrate);
        bitrate.setText(QualityPresets.formatMbps(session.bitrateKbps()));
        vDecode = tile(tiles, R.string.brp_stat_decode);
        vDrops = tile(tiles, R.string.brp_stat_drops);
        vHost = tile(tiles, R.string.brp_stat_host_latency);

    }

    private void refreshStats() {
        StreamStats st = session.stats();
        subtitle.setText(s(R.string.brp_cc_subtitle, session.pcName(), currentScreenName()));
        if (st == null) {
            qualityPill.setText(s(R.string.brp_quality_measuring));
            qualityPill.setTextColor(BrpUi.TEXT_2);
            return;
        }
        String q;
        int color;
        switch (st.quality()) {
            case EXCELLENT: q = s(R.string.brp_quality_excellent); color = BrpUi.GOOD; break;
            case GOOD: q = s(R.string.brp_quality_good); color = BrpUi.GOOD; break;
            case FAIR: q = s(R.string.brp_quality_fair); color = BrpUi.WARN; break;
            default: q = s(R.string.brp_quality_poor); color = BrpUi.BAD; break;
        }
        // The dot repeats the word; the word carries the meaning.
        qualityPill.setText(st.rttMs > 0 ? s(R.string.brp_quality_pill_rtt, q, st.rttMs) : s(R.string.brp_quality_pill, q));
        qualityPill.setTextColor(color);

        if (vQuality == null) {
            return;
        }
        vQuality.setText(q);
        vQuality.setTextColor(color);
        vLatency.setText(st.rttMs > 0 ? s(R.string.brp_value_rtt, st.rttMs, st.rttVarianceMs) : "—");
        vResolution.setText(s(R.string.brp_value_resolution, st.width, st.height));
        vFps.setText(s(R.string.brp_value_fps, Math.round(st.renderedFps), Math.round(st.receivedFps)));
        vCodec.setText(st.codec != null ? st.codec : "—");
        vDecode.setText(String.format(Locale.getDefault(), "%.1f ms", st.decodeMs));
        vDrops.setText(String.format(Locale.getDefault(), "%.1f %%", st.networkDropPercent));
        vHost.setText(st.hostLatencyMs >= 0
                ? String.format(Locale.getDefault(), "%.1f ms", st.hostLatencyMs)
                : s(R.string.brp_value_not_reported));
    }

    // ------------------------------------------------------------------ Keyboard

    private void buildKeyboard() {
        FlowLayout f = flow();
        TextView ime = BrpUi.primaryButton(activity, s(R.string.brp_kbd_phone_keyboard));
        ime.setOnClickListener(v -> {
            session.setKeyboardBarVisible(true);
            close();
            session.showPhoneKeyboard();
        });
        f.addView(ime);
        toggle(f, s(R.string.brp_kbd_extra_keys), session.isKeyboardBarVisible(), on -> {
            session.setKeyboardBarVisible(on);
            if (on) {
                close();
            }
        });
        final TextView paste = action(f, s(R.string.brp_kbd_type_clipboard), null);
        paste.setOnClickListener(v -> {
            int n = session.typePhoneClipboard();
            paste.setText(n > 0 ? activity.getResources().getQuantityString(R.plurals.brp_kbd_typed_n, n, n)
                    : s(R.string.brp_kbd_clipboard_empty));
            paste.announceForAccessibility(paste.getText());
        });
        note(s(R.string.brp_kbd_clipboard_note));

        heading(R.string.brp_kbd_modifiers);
        FlowLayout m = flow();
        choices(m, new String[]{s(R.string.brp_kbd_mod_latch), s(R.string.brp_kbd_mod_hold)},
                new String[]{"latch", "hold"}, session.stickyModifiers() ? "latch" : "hold",
                value -> session.setStickyModifiers("latch".equals(value)));
        note(s(R.string.brp_kbd_modifiers_note));

        heading(R.string.brp_kbd_shortcuts);
        FlowLayout sc = flow();
        shortcut(sc, "Ctrl+C", (short) 0x43, Modifier.CTRL);
        shortcut(sc, "Ctrl+V", (short) 0x56, Modifier.CTRL);
        shortcut(sc, "Ctrl+X", (short) 0x58, Modifier.CTRL);
        shortcut(sc, "Ctrl+Z", (short) 0x5A, Modifier.CTRL);
        shortcut(sc, "Ctrl+A", (short) 0x41, Modifier.CTRL);
        shortcut(sc, "Alt+Tab", (short) 0x09, Modifier.ALT);
        shortcut(sc, "Alt+F4", (short) 0x73, Modifier.ALT);
        shortcut(sc, s(R.string.brp_key_super), (short) 0, Modifier.META);
        shortcut(sc, s(R.string.brp_key_super) + "+D", (short) 0x44, Modifier.META);
        shortcut(sc, s(R.string.brp_key_super) + "+E", (short) 0x45, Modifier.META);
        shortcut(sc, "Ctrl+Alt+Del", (short) 0x2E, Modifier.CTRL, Modifier.ALT);
        note(s(R.string.brp_kbd_shortcuts_note));
    }

    private void shortcut(FlowLayout f, final String label, final short key, final Modifier... mods) {
        final TextView b = action(f, label, null);
        b.setOnClickListener(v -> {
            session.sendShortcut(key, mods);
            subtitle.setText(s(R.string.brp_kbd_sent, label));
        });
    }

    // ------------------------------------------------------------------ Controller

    private void buildController() {
        heading(R.string.brp_osc_layout);
        FlowLayout layouts = flow();
        layouts.setChildWidth(BrpUi.dp(activity, 168));
        final com.limelight.brp.OscLayouts.Layout[] all = com.limelight.brp.OscLayouts.Layout.values();
        final int[] names = {R.string.brp_osc_layout_pad, R.string.brp_osc_layout_fps, R.string.brp_osc_layout_race,
                R.string.brp_osc_layout_fight, R.string.brp_osc_layout_classic};
        final int[] notes = {R.string.brp_osc_layout_pad_note, R.string.brp_osc_layout_fps_note,
                R.string.brp_osc_layout_race_note, R.string.brp_osc_layout_fight_note,
                R.string.brp_osc_layout_classic_note};
        String current = session.onScreenControllerLayout();
        final TextView layoutNote = BrpUi.text(activity, "", 13, BrpUi.TEXT_2);
        final LinearLayout[] cards = new LinearLayout[all.length];
        for (int i = 0; i < all.length; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(BrpUi.buttonBackground(activity, 14));
            int p = BrpUi.dp(activity, 8);
            card.setPadding(p, p, p, p);
            card.setFocusable(true);
            card.setClickable(true);
            if (all[i] == com.limelight.brp.OscLayouts.Layout.CLASSIC) {
                TextView classic = BrpUi.text(activity, "Moonlight", 14, BrpUi.TEXT_2);
                classic.setGravity(Gravity.CENTER);
                classic.setMinHeight(BrpUi.dp(activity, 68));
                card.addView(classic, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                card.addView(new OscLayoutPreview(activity, all[i]), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final TextView name = BrpUi.text(activity, s(names[i]), 15, BrpUi.TEXT);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, BrpUi.dp(activity, 6), 0, 0);
            card.addView(name);
            boolean on = all[i].key.equals(current);
            card.setSelected(on);
            name.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            name.setText(on ? s(R.string.brp_checked, s(names[i])) : s(names[i]));
            card.setContentDescription(s(names[i]));
            if (on) {
                layoutNote.setText(s(notes[i]));
            }
            card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            card.setOnClickListener(v -> {
                session.setOnScreenControllerLayout(all[idx].key);
                for (int j = 0; j < cards.length; j++) {
                    boolean sel = j == idx;
                    cards[j].setSelected(sel);
                    TextView n = (TextView) cards[j].getChildAt(1);
                    n.setText(sel ? s(R.string.brp_checked, s(names[j])) : s(names[j]));
                    n.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
                layoutNote.setText(s(notes[idx]));
                v.announceForAccessibility(s(names[idx]));
            });
            cards[i] = card;
            layouts.addView(card);
        }
        layoutNote.setPadding(0, BrpUi.dp(activity, 6), 0, BrpUi.dp(activity, 2));
        content.addView(layoutNote);

        heading(R.string.brp_osc_this_phone);
        FlowLayout f = flow();
        toggle(f, s(R.string.brp_osc_show), session.onScreenControllerVisible(),
                session::setOnScreenControllerVisible);
        action(f, s(R.string.brp_osc_edit), v -> {
            if (!session.onScreenControllerVisible()) {
                session.setOnScreenControllerVisible(true);
            }
            close();
            session.editOnScreenControllerLayout();
        });
        final TextView reset = action(f, s(R.string.brp_osc_reset), null);
        reset.setOnClickListener(v -> confirmThen(reset, s(R.string.brp_osc_reset_confirm),
                session::resetOnScreenControllerLayout));
        note(s(R.string.brp_osc_edit_note));

        heading(R.string.brp_osc_opacity);
        final TextView value = BrpUi.text(activity, s(R.string.brp_value_percent, session.onScreenControllerOpacity()), 15, BrpUi.TEXT);
        content.addView(value);
        SeekBar bar = new SeekBar(activity);
        bar.setMax(100);
        bar.setProgress(session.onScreenControllerOpacity());
        bar.setContentDescription(s(R.string.brp_osc_opacity));
        bar.setMinimumHeight(BrpUi.dp(activity, 48));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int v = Math.max(10, p);
                value.setText(s(R.string.brp_value_percent, v));
                if (fromUser) {
                    session.setOnScreenControllerOpacity(v);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        content.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, BrpUi.dp(activity, 48)));

        heading(R.string.brp_osc_which);
        note(s(session.nativeTouchEnabled() ? R.string.brp_osc_two_controllers_touch_on
                : R.string.brp_osc_two_controllers_touch_off));
        note(s(R.string.brp_osc_more_in_settings));
    }

    // ------------------------------------------------------------------ Display

    private void buildDisplay() {
        heading(R.string.brp_display_current);
        note(currentScreenName());

        HostEntries entries = session.hostEntries();
        heading(R.string.brp_display_screens);
        if (entries == null) {
            note(s(session.hostEntriesFailed() ? R.string.brp_display_unreadable : R.string.brp_display_loading));
        } else if (!entries.canSwitchMonitor()) {
            note(s(R.string.brp_display_single));
        } else {
            FlowLayout f = flow();
            for (final HostEntries.Entry e : entries.monitors) {
                boolean current = e.appId == session.appId();
                final TextView b = BrpUi.button(activity, e.name);
                BrpUi.setToggle(b, e.name, current);
                if (!current) {
                    b.setOnClickListener(v -> confirmSwitch(b, e));
                }
                f.addView(b);
            }
            note(s(R.string.brp_display_switch_note));
        }

        if (entries != null && (entries.desktopLow != null || entries.steamBigPicture != null
                || entries.desktop != null)) {
            heading(R.string.brp_display_other_entries);
            FlowLayout o = flow();
            for (final HostEntries.Entry e : new HostEntries.Entry[]{entries.desktop, entries.desktopLow, entries.steamBigPicture}) {
                if (e == null || e.appId == session.appId()) {
                    continue;
                }
                final TextView b = BrpUi.button(activity, e.name);
                b.setOnClickListener(v -> confirmSwitch(b, e));
                o.addView(b);
            }
            if (entries.desktopLow != null) {
                note(s(R.string.brp_display_desktop_low_note));
            }
        }

        if (session.canStretch()) {
            heading(R.string.brp_display_picture);
            FlowLayout p = flow();
            choices(p, new String[]{s(R.string.brp_display_fit), s(R.string.brp_display_stretch)},
                    new String[]{"fit", "stretch"}, session.isStretched() ? "stretch" : "fit",
                    v -> session.setStretched("stretch".equals(v)));
        }
    }

    private void confirmSwitch(TextView b, HostEntries.Entry e) {
        HostEntries entries = session.hostEntries();
        boolean currentIsEntry = entries != null && (entries.currentMonitor(session.appId()) != null
                || (entries.desktop != null && entries.desktop.appId == session.appId())
                || (entries.desktopLow != null && entries.desktopLow.appId == session.appId()));
        if (currentIsEntry) {
            // Leaving a screen entry closes nothing the person would lose.
            session.switchTo(e);
        } else {
            // A game would be closed on the computer to start the other entry.
            confirmThen(b, s(R.string.brp_confirm_switch_closes, session.appName()), () -> session.switchTo(e));
        }
    }

    // ------------------------------------------------------------------ Video

    private void buildVideo() {
        pendingResolution = session.resolutionSetting();
        pendingFps = session.fpsSetting();
        pendingFormat = session.videoFormatSetting();
        pendingBitrate = session.bitrateKbps();

        StreamStats st = session.stats();
        heading(R.string.brp_video_now);
        note(st == null ? s(R.string.brp_quality_measuring)
                : s(R.string.brp_video_now_value, s(R.string.brp_value_resolution, st.width, st.height), Math.round(st.renderedFps),
                st.codec != null ? st.codec : "—", QualityPresets.formatMbps(session.bitrateKbps()),
                st.decoderName != null ? st.decoderName : "—"));

        final TextView apply = BrpUi.primaryButton(activity, s(R.string.brp_video_apply));
        apply.setEnabled(false);
        apply.setAlpha(0.5f);
        final Runnable changed = () -> {
            boolean dirty = !pendingResolution.equals(session.resolutionSetting())
                    || !pendingFps.equals(session.fpsSetting())
                    || !pendingFormat.equals(session.videoFormatSetting())
                    || pendingBitrate != session.bitrateKbps();
            apply.setEnabled(dirty);
            apply.setAlpha(dirty ? 1f : 0.5f);
        };

        // Bitrate
        heading(R.string.brp_video_bitrate);
        final TextView bitrateValue = BrpUi.text(activity, QualityPresets.formatMbps(pendingBitrate), 16, BrpUi.TEXT);
        bitrateValue.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(bitrateValue);
        final SeekBar bar = new SeekBar(activity);
        // 0.5 Mbps steps from 0.5 to 150 Mbps
        bar.setMax((QualityPresets.MAX_KBPS - QualityPresets.MIN_KBPS) / 500);
        bar.setProgress((pendingBitrate - QualityPresets.MIN_KBPS) / 500);
        bar.setContentDescription(s(R.string.brp_video_bitrate));
        content.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, BrpUi.dp(activity, 48)));

        FlowLayout presets = flow();
        final QualityPresets.Preset[] ps = QualityPresets.Preset.values();
        final String[] presetLabels = {s(R.string.brp_preset_data_saver), s(R.string.brp_preset_balanced),
                s(R.string.brp_preset_high), s(R.string.brp_preset_ultra)};
        final TextView[] presetViews = new TextView[ps.length];
        final Runnable markPreset = () -> {
            QualityPresets.Preset p = QualityPresets.presetFor(pendingBitrate,
                    session.defaultBitrateKbps(pendingResolution, pendingFps));
            for (int i = 0; i < ps.length; i++) {
                BrpUi.setToggle(presetViews[i], presetLabels[i] + " · " + QualityPresets.formatMbps(
                        QualityPresets.bitrateFor(ps[i], session.defaultBitrateKbps(pendingResolution, pendingFps))),
                        ps[i] == p);
            }
        };
        for (int i = 0; i < ps.length; i++) {
            final int idx = i;
            presetViews[i] = action(presets, presetLabels[i], v -> {
                pendingBitrate = QualityPresets.bitrateFor(ps[idx],
                        session.defaultBitrateKbps(pendingResolution, pendingFps));
                bar.setProgress((pendingBitrate - QualityPresets.MIN_KBPS) / 500);
                bitrateValue.setText(QualityPresets.formatMbps(pendingBitrate));
                markPreset.run();
                changed.run();
            });
        }
        markPreset.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                pendingBitrate = QualityPresets.MIN_KBPS + p * 500;
                bitrateValue.setText(QualityPresets.formatMbps(pendingBitrate));
                markPreset.run();
                changed.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        // Resolution
        heading(R.string.brp_video_resolution);
        FlowLayout res = flow();
        choices(res, new String[]{"720p", "1080p", "1440p", "4K", s(R.string.brp_video_native)},
                new String[]{"1280x720", "1920x1080", "2560x1440", "3840x2160", "Native"},
                pendingResolution, v -> {
                    pendingResolution = v;
                    markPreset.run();
                    changed.run();
                });

        // Frame rate: only rates this screen can show (30 and 60 always).
        heading(R.string.brp_video_fps);
        FlowLayout fps = flow();
        int maxHz = session.maxDisplayRefreshRate();
        java.util.List<String> rates = new java.util.ArrayList<>();
        for (String r : new String[]{"30", "60", "90", "120", "144"}) {
            int hz = Integer.parseInt(r);
            if (hz <= 60 || hz <= maxHz + 1 || r.equals(pendingFps)) {
                rates.add(r);
            }
        }
        String[] rateArr = rates.toArray(new String[0]);
        String[] rateLabels = new String[rateArr.length];
        for (int i = 0; i < rateArr.length; i++) {
            rateLabels[i] = rateArr[i] + " FPS";
        }
        choices(fps, rateLabels, rateArr, pendingFps, v -> {
            pendingFps = v;
            markPreset.run();
            changed.run();
        });

        // Codec: only what this device can decode.
        heading(R.string.brp_video_codec);
        FlowLayout codec = flow();
        java.util.List<String> cl = new java.util.ArrayList<>(), cv = new java.util.ArrayList<>();
        cl.add(s(R.string.brp_video_codec_auto)); cv.add("auto");
        cl.add("H.264"); cv.add("neverh265");
        if (session.hevcSupported()) { cl.add("HEVC"); cv.add("forceh265"); }
        if (session.av1Supported()) { cl.add("AV1"); cv.add("forceav1"); }
        choices(codec, cl.toArray(new String[0]), cv.toArray(new String[0]), pendingFormat, v -> {
            pendingFormat = v;
            changed.run();
        });
        if (!session.av1Supported()) {
            note(s(R.string.brp_video_no_av1));
        }

        TextView why = note(s(R.string.brp_video_reconnect_note));
        why.setTextColor(BrpUi.WARN);
        apply.setOnClickListener(v -> session.applyVideoAndReconnect(
                pendingResolution, pendingFps, pendingBitrate, pendingFormat));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = BrpUi.dp(activity, 8);
        content.addView(apply, alp);
    }

    // ------------------------------------------------------------------ Session

    private void buildSession() {
        FlowLayout f = flow();
        TextView resume = BrpUi.primaryButton(activity, s(R.string.brp_session_resume));
        resume.setOnClickListener(v -> close());
        f.addView(resume);
        action(f, s(R.string.brp_session_reconnect), v -> session.reconnect());
        action(f, s(R.string.brp_action_disconnect), v -> session.disconnect());
        final TextView quit = action(f, s(R.string.brp_session_quit), null);
        quit.setOnClickListener(v -> confirmThen(quit, s(R.string.brp_confirm_quit, session.appName()),
                session::quitOnHost));
        action(f, "Ctrl+Alt+Del", v -> {
            session.sendShortcut((short) 0x2E, Modifier.CTRL, Modifier.ALT);
            subtitle.setText(s(R.string.brp_kbd_sent, "Ctrl+Alt+Del"));
        });
        if (session.pipSupported()) {
            action(f, s(R.string.brp_session_pip), v -> {
                closeNow();
                session.enterPip();
            });
        }
        note(s(R.string.brp_session_note));

        heading(R.string.brp_session_statistics);
        FlowLayout p = flow();
        choices(p, new String[]{s(R.string.brp_perf_off), s(R.string.brp_perf_minimal), s(R.string.brp_perf_detailed)},
                new String[]{"0", "1", "2"}, String.valueOf(session.perfOverlayMode()),
                v -> session.setPerfOverlayMode(Integer.parseInt(v)));
    }

    // ------------------------------------------------------------------ layout

    /** A vertical LinearLayout that never grows taller than {@link #setMaxHeight}. */
    private static final class CappedLinearLayout extends LinearLayout {
        private int maxHeight;

        CappedLinearLayout(android.content.Context c) {
            super(c);
        }

        void setMaxHeight(int px) {
            maxHeight = px;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (maxHeight > 0) {
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int capped = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                        ? maxHeight : Math.min(size, maxHeight);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
