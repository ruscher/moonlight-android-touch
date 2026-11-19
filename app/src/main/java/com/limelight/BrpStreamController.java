package com.limelight;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.touch.TouchContext;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.brp.HostEntries;
import com.limelight.brp.ModifierKeys;
import com.limelight.brp.QualityPresets;
import com.limelight.brp.StreamStats;
import com.limelight.brp.TextChunker;
import com.limelight.brp.ui.RemoteKeyboardBar;
import com.limelight.brp.ui.StreamControlCenter;
import com.limelight.brp.ui.StreamSession;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Big Remote Play's layer over the stream activity: the Control Center, the key
 * bar, the statistics overlay modes, the host's entries (screens, Desktop Low,
 * Steam Big Picture) and controlled reconnects.
 *
 * {@link Game} keeps the protocol, decoder and input paths as upstream wrote
 * them and calls a handful of hooks here; this class never talks to the decoder.
 *
 * Settings that need a new stream (bitrate, resolution, frame rate, codec, the
 * entry being streamed) are applied by a controlled reconnect: the current
 * stream is stopped, every held input is released, and the stream activity is
 * started again with the new settings or entry. Sunshine has no way to change
 * them inside a running session, so the UI never pretends it does.
 */
public class BrpStreamController implements StreamSession {
    /** Optional: text for the spinner of a stream started by a reconnect. */
    public static final String EXTRA_REASON = "BrpReason";

    private static final String PREF_PERF_MODE = "brp_perf_overlay_mode";
    private static final String PREF_STICKY = "brp_sticky_modifiers";

    private final Game game;
    private final String host;
    private final int port, httpsPort;
    private final String uniqueId;
    private final X509Certificate serverCert;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final StreamControlCenter controlCenter;
    private final RemoteKeyboardBar keyboardBar;

    private volatile HostEntries hostEntries;
    private volatile boolean hostEntriesFailed;
    private int perfMode;
    private boolean stretched;
    private boolean oscWasVisible;
    private boolean grabWasOn;
    private volatile boolean leaving;
    private boolean stopped;

    private final Runnable minimalOverlayTick = new Runnable() {
        @Override
        public void run() {
            if (perfMode != PERF_MINIMAL) {
                return;
            }
            StreamStats st = stats();
            if (st != null && !game.isHidingOverlays) {
                game.performanceOverlayView.setText(st.compactLine());
            }
            handler.postDelayed(this, 1000);
        }
    };

    BrpStreamController(Game game, String host, int port, int httpsPort, String uniqueId, X509Certificate serverCert) {
        this.game = game;
        this.host = host;
        this.port = port;
        this.httpsPort = httpsPort;
        this.uniqueId = uniqueId;
        this.serverCert = serverCert;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(game);
        this.stretched = game.prefConfig.stretchVideo;

        FrameLayout parent = (FrameLayout) game.streamView.getParent();

        keyboardBar = new RemoteKeyboardBar(game, parent,
                (code, down, flags) -> {
                    if (game.conn != null) {
                        // The protocol carries Windows virtual keys with 0x80 in the high byte.
                        game.conn.sendKeyboardInput((short) (0x8000 | (code & 0xFF)),
                                down ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP, flags, (byte) 0);
                    }
                },
                new RemoteKeyboardBar.Listener() {
                    @Override
                    public void onPhoneKeyboardRequested() {
                        showPhoneKeyboard();
                    }

                    @Override
                    public void onCloseRequested() {
                        setKeyboardBarVisible(false);
                    }
                });
        keyboardBar.setSticky(prefs.getBoolean(PREF_STICKY, true));
        keyboardBar.setHaptics(game.prefConfig.vibrateOsc);

        controlCenter = new StreamControlCenter(game, parent, this);

        int savedMode = prefs.getInt(PREF_PERF_MODE, -1);
        if (game.prefConfig.enablePerfOverlay) {
            perfMode = PERF_DETAILED;
        } else if (savedMode == PERF_MINIMAL) {
            perfMode = PERF_MINIMAL;
        } else {
            perfMode = PERF_OFF;
        }
    }

    // ================================================================ hooks from Game

    /** The stream is up: show the handle, start the minimal overlay, read the host's entries. */
    void onConnected() {
        controlCenter.setHandleVisible(!isInPip());
        applyPerfMode();
        loadHostEntries();
    }

    /** Back (navigation bar, gesture, remote). Returns true when handled here. */
    boolean onBack() {
        if (leaving) {
            return false;
        }
        // Back opens the Control Center (which has Disconnect) instead of
        // dropping the stream; Back again closes it.
        controlCenter.toggle();
        return true;
    }

    boolean isControlCenterOpen() {
        return controlCenter.isOpen();
    }

    /**
     * A key went down while the Control Center is open. Gamepad B and Escape
     * close it, A clicks the focused item; arrows and D-pad return false so
     * Android moves focus through the menu. Nothing reaches the computer.
     */
    boolean handleMenuKeyDown(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            case KeyEvent.KEYCODE_BACK:
                // Navigation Back arrives through onBackPressed (virtual key flag);
                // a controller's Back/Select button comes here.
                controlCenter.close();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_START:
                controlCenter.close();
                return true;
            case KeyEvent.KEYCODE_BUTTON_A: {
                View focused = game.getCurrentFocus();
                if (focused != null && focused != game.streamView) {
                    focused.performClick();
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_TAB:
                return false;
            default:
                return true;
        }
    }

    /** Focus lost, rotation, background: nothing may stay pressed. */
    void releaseHeldKeys() {
        keyboardBar.releaseAll();
    }

    void onConfigurationChanged() {
        keyboardBar.releaseAll();
        if (isInPip()) {
            controlCenter.closeNow();
            keyboardBar.setVisible(false);
            controlCenter.setHandleVisible(false);
            game.performanceOverlayView.setVisibility(View.GONE);
        } else {
            controlCenter.closeNow();
            // The on-screen controller was just rebuilt on top: keep the bar and handle above it.
            if (keyboardBar.isVisible()) {
                keyboardBar.view().bringToFront();
            }
            controlCenter.setHandleVisible(game.connected);
            applyPerfMode();
        }
    }

    /** The stream is ending (activity stopping or connection lost). */
    void onStreamStopping() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        keyboardBar.releaseAll();
        controlCenter.closeNow();
        controlCenter.setHandleVisible(false);
    }

    /** Modifiers latched on the key bar, added to every key the stream sends. */
    byte keyboardModifierFlags() {
        return keyboardBar.modifierFlags();
    }

    private boolean isInPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && game.isInPictureInPictureMode();
    }

    // ================================================================ StreamSession: state

    @Override public String pcName() { return game.pcName != null ? game.pcName : ""; }
    @Override public String appName() { return game.app != null ? game.app.getAppName() : ""; }
    @Override public int appId() { return game.app != null ? game.app.getAppId() : 0; }
    @Override public StreamStats stats() { return game.decoderRenderer != null ? game.decoderRenderer.getLatestStats() : null; }

    // ================================================================ video

    @Override public int bitrateKbps() { return game.prefConfig.bitrate; }

    @Override
    public String resolutionSetting() {
        return prefs.getString("list_resolution", PreferenceConfiguration.DEFAULT_RESOLUTION);
    }

    @Override
    public String fpsSetting() {
        return prefs.getString("list_fps", PreferenceConfiguration.DEFAULT_FPS);
    }

    @Override
    public String videoFormatSetting() {
        return prefs.getString("video_format", "auto");
    }

    @Override
    public int defaultBitrateKbps(String resolution, String fps) {
        if (PreferenceConfiguration.RES_NATIVE.equals(resolution)) {
            resolution = game.prefConfig.width + "x" + game.prefConfig.height;
        }
        try {
            return PreferenceConfiguration.getDefaultBitrate(resolution, fps);
        } catch (RuntimeException e) {
            return game.prefConfig.bitrate;
        }
    }

    @Override public boolean hevcSupported() { return game.decoderRenderer != null && game.decoderRenderer.isHevcSupported(); }
    @Override public boolean av1Supported() { return game.decoderRenderer != null && game.decoderRenderer.isAv1Supported(); }

    @Override
    public int maxDisplayRefreshRate() {
        float max = 60;
        Display display = game.getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (Display.Mode m : display.getSupportedModes()) {
                max = Math.max(max, m.getRefreshRate());
            }
        } else {
            max = Math.max(max, display.getRefreshRate());
        }
        return Math.round(max);
    }

    @Override
    public void applyVideoAndReconnect(String resolution, String fps, int bitrateKbps, String videoFormat) {
        // Saved as the person's settings (the same keys as the Settings screen),
        // then applied by starting the stream again.
        // Only what changed is written, so settings the person never touched keep
        // following the app's defaults.
        SharedPreferences.Editor e = prefs.edit();
        if (!resolution.equals(resolutionSetting())) e.putString("list_resolution", resolution);
        if (!fps.equals(fpsSetting())) e.putString("list_fps", fps);
        if (bitrateKbps != bitrateKbps()) e.putInt("seekbar_bitrate_kbps", QualityPresets.clamp(bitrateKbps));
        if (!videoFormat.equals(videoFormatSetting())) e.putString("video_format", videoFormat);
        e.apply(); // in memory at once; the next stream in this process reads it
        relaunch(game.app.getAppId(), game.app.getAppName(), game.app.isHdrSupported(),
                game.getString(R.string.brp_reconnecting_new_settings));
    }

    @Override
    public void lowerQualityAndReconnect() {
        QualityPresets.Step step = QualityPresets.lower(game.prefConfig.bitrate,
                game.prefConfig.height, game.prefConfig.fps);
        SharedPreferences.Editor e = prefs.edit().putInt("seekbar_bitrate_kbps", step.bitrateKbps);
        if (step.maxHeight == 720) {
            e.putString("list_resolution", PreferenceConfiguration.RES_720P);
        }
        if (step.maxFps == 60) {
            e.putString("list_fps", "60");
        }
        e.apply();
        relaunch(game.app.getAppId(), game.app.getAppName(), game.app.isHdrSupported(),
                game.getString(R.string.brp_reconnecting_lower, QualityPresets.formatMbps(step.bitrateKbps)));
    }

    // ================================================================ display

    @Override
    public boolean canStretch() {
        View parent = (View) game.streamView.getParent();
        if (parent == null || parent.getHeight() == 0 || game.prefConfig.height == 0) {
            return false;
        }
        double screen = (double) parent.getWidth() / parent.getHeight();
        double stream = (double) game.prefConfig.width / game.prefConfig.height;
        return Math.abs(screen - stream) > 0.01;
    }

    @Override public boolean isStretched() { return stretched; }

    @Override
    public void setStretched(boolean stretch) {
        // Only the view's size changes; the surface is resized, not recreated,
        // so the decoder keeps running. Touch coordinates follow the view.
        stretched = stretch;
        if (stretch) {
            game.streamView.getHolder().setFixedSize(game.prefConfig.width, game.prefConfig.height);
            game.streamView.setDesiredAspectRatio(0);
        } else {
            game.streamView.setDesiredAspectRatio((double) game.prefConfig.width / game.prefConfig.height);
        }
        game.streamView.requestLayout();
    }

    @Override public HostEntries hostEntries() { return hostEntries; }
    @Override public boolean hostEntriesFailed() { return hostEntriesFailed; }

    private void loadHostEntries() {
        if (hostEntries != null || host == null) {
            return;
        }
        // The same paired HTTPS request the app list screen makes (pinned server
        // certificate, client certificate); no shell, no new port.
        new Thread(() -> {
            HostEntries result = null;
            try {
                NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple(host, port), httpsPort,
                        uniqueId, serverCert, PlatformBinding.getCryptoProvider(game));
                List<NvApp> apps = http.getAppList();
                List<Integer> ids = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<Boolean> hdr = new ArrayList<>();
                for (NvApp a : apps) {
                    ids.add(a.getAppId());
                    names.add(a.getAppName());
                    hdr.add(a.isHdrSupported());
                }
                result = HostEntries.from(ids, names, hdr);
            } catch (Exception e) {
                LimeLog.warning("Big Remote Play: could not read the host's entries: " + e.getClass().getSimpleName());
            }
            final HostEntries r = result;
            game.runOnUiThread(() -> {
                hostEntries = r;
                hostEntriesFailed = r == null;
                controlCenter.onHostEntriesChanged();
            });
        }, "BRP host entries").start();
    }

    @Override
    public void switchTo(HostEntries.Entry entry) {
        relaunch(entry.appId, entry.name, entry.hdrSupported,
                game.getString(R.string.brp_switching_to, entry.name));
    }

    // ================================================================ input

    @Override
    public void suspendInput() {
        // Release everything that could be held on the computer before the
        // overlay takes the touches.
        game.cancelAllNativeTouches();
        for (TouchContext tc : game.touchContextMap) {
            if (tc != null) {
                tc.cancelTouch();
            }
        }
        keyboardBar.releaseAll();
        VirtualController osc = game.virtualController;
        oscWasVisible = osc != null && osc.isVisible();
        if (osc != null) {
            osc.releaseAll();
            osc.hide();
        }
        // Give the mouse pointer back to Android so a mouse can use the menu,
        // but keep input grabbed: key releases and stick centring of physical
        // keyboards and controllers must still reach the computer.
        grabWasOn = game.grabbedInput;
        if (grabWasOn) {
            game.setPointerCaptureForMenu(false);
        }
        // Keys of the bar under the scrim must not be reachable by D-pad focus.
        keyboardBar.view().setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    @Override
    public void resumeInput() {
        if (oscWasVisible && game.virtualController != null && !isInPip()) {
            game.virtualController.show();
        }
        if (grabWasOn && game.connected) {
            game.setPointerCaptureForMenu(true);
        }
        keyboardBar.view().setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (keyboardBar.isVisible()) {
            keyboardBar.view().bringToFront();
        }
        controlCenter.setHandleVisible(game.connected && !isInPip());
        game.streamView.requestFocus();
        game.hideSystemUi(250);
    }

    @Override
    public void showPhoneKeyboard() {
        game.streamView.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) game.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(game.streamView, 0);
        // Some keyboards ignore showSoftInput for a view without an input
        // connection; fall back to the toggle upstream uses (three-finger tap).
        handler.postDelayed(() -> {
            if (!imm.isActive(game.streamView)) {
                game.toggleKeyboard();
            }
        }, 150);
    }

    @Override public boolean isKeyboardBarVisible() { return keyboardBar.isVisible(); }

    @Override
    public void setKeyboardBarVisible(boolean visible) {
        keyboardBar.setVisible(visible);
        if (visible) {
            controlCenter.setHandleVisible(game.connected && !isInPip());
        }
    }

    @Override public boolean stickyModifiers() { return prefs.getBoolean(PREF_STICKY, true); }

    @Override
    public void setStickyModifiers(boolean sticky) {
        prefs.edit().putBoolean(PREF_STICKY, sticky).apply();
        keyboardBar.setSticky(sticky);
    }

    @Override
    public int typePhoneClipboard() {
        ClipboardManager cm = (ClipboardManager) game.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0 || game.conn == null) {
            return 0;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(game);
        if (text == null || text.length() == 0) {
            return 0;
        }
        final TextChunker chunks = TextChunker.split(text);
        // Paced off the UI thread so a long text never fills the input queue.
        new Thread(() -> {
            for (String chunk : chunks.chunks) {
                if (leaving || game.conn == null || !game.connected) {
                    return;
                }
                game.conn.sendUtf8Text(chunk);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "BRP clipboard").start();
        if (chunks.truncated || chunks.skippedChars > 0) {
            Toast.makeText(game, game.getString(R.string.brp_kbd_clipboard_partial,
                    chunks.sentChars, chunks.skippedChars), Toast.LENGTH_LONG).show();
        }
        return chunks.sentChars;
    }

    @Override
    public void sendShortcut(short keyCode, ModifierKeys.Modifier... modifiers) {
        keyboardBar.shortcut(keyCode, modifiers);
    }

    // ================================================================ on-screen controller

    @Override
    public boolean onScreenControllerVisible() {
        // While the Control Center is open the controller is hidden on purpose;
        // report what it will be once the menu closes.
        return controlCenter.isOpen() ? oscWasVisible
                : game.virtualController != null && game.virtualController.isVisible();
    }

    @Override
    public void setOnScreenControllerVisible(boolean visible) {
        if (visible && game.virtualController == null) {
            // Off in Settings: create it now and count gamepad 1 as attached,
            // as upstream does when it starts with the controller on.
            game.prefConfig.onscreenController = true;
            game.virtualController = new VirtualController(game.controllerHandler,
                    (FrameLayout) game.streamView.getParent(), game);
            game.virtualController.refreshLayout();
            if (controlCenter.isOpen()) {
                game.virtualController.hide(); // never above the open menu
            }
        }
        if (game.virtualController == null) {
            return;
        }
        game.virtualController.setHiddenByUser(!visible);
        if (!visible) {
            game.virtualController.releaseAll();
        }
        if (controlCenter.isOpen()) {
            oscWasVisible = visible; // applied when the menu closes
        } else if (visible) {
            game.virtualController.show();
        } else {
            game.virtualController.hide();
        }
    }

    @Override
    public void editOnScreenControllerLayout() {
        if (game.virtualController != null) {
            game.virtualController.show();
            game.virtualController.cycleEditMode();
        }
    }

    @Override public int onScreenControllerOpacity() { return game.prefConfig.oscOpacity; }

    @Override
    public void setOnScreenControllerOpacity(int percent) {
        game.prefConfig.oscOpacity = percent;
        prefs.edit().putInt("seekbar_osc_opacity", percent).apply();
        if (game.virtualController != null) {
            game.virtualController.setOpacity(percent);
        }
    }

    @Override public boolean nativeTouchEnabled() { return game.prefConfig.nativeTouch; }

    // ================================================================ overlay, session

    @Override public int perfOverlayMode() { return perfMode; }

    @Override
    public void setPerfOverlayMode(int mode) {
        perfMode = mode;
        prefs.edit()
                .putInt(PREF_PERF_MODE, mode)
                .putBoolean("checkbox_enable_perf_overlay", mode == PERF_DETAILED)
                .apply();
        applyPerfMode();
    }

    private void applyPerfMode() {
        if (stopped) {
            return;
        }
        // The decoder reads this flag every second to decide whether to build the
        // detailed text; the minimal line is built here from the same numbers.
        game.prefConfig.enablePerfOverlay = perfMode == PERF_DETAILED;
        handler.removeCallbacks(minimalOverlayTick);
        if (game.isHidingOverlays) {
            return;
        }
        if (perfMode == PERF_OFF) {
            game.performanceOverlayView.setVisibility(View.GONE);
        } else {
            game.performanceOverlayView.setVisibility(View.VISIBLE);
            if (perfMode == PERF_MINIMAL) {
                handler.post(minimalOverlayTick);
            }
        }
    }

    @Override
    public boolean pipSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                game.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    @Override
    public void enterPip() {
        if (pipSupported()) {
            game.enterPip();
        }
    }

    @Override
    public void reconnect() {
        relaunch(game.app.getAppId(), game.app.getAppName(), game.app.isHdrSupported(),
                game.getString(R.string.brp_reconnecting));
    }

    @Override
    public void disconnect() {
        leaving = true;
        controlCenter.closeNow();
        game.finish();
    }

    @Override
    public void quitOnHost() {
        leaving = true;
        controlCenter.closeNow();
        onStreamStopping();
        game.displayedFailureDialog = true;
        game.stopConnection();
        final String appName = appName();
        new Thread(() -> {
            String message;
            try {
                NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple(host, port), httpsPort,
                        uniqueId, serverCert, PlatformBinding.getCryptoProvider(game));
                message = game.getString(http.quitApp() ? R.string.brp_quit_done : R.string.brp_quit_failed, appName);
            } catch (Exception e) {
                message = game.getString(R.string.brp_quit_failed, appName);
            }
            final String m = message;
            game.runOnUiThread(() -> {
                Toast.makeText(game.getApplicationContext(), m, Toast.LENGTH_LONG).show();
                game.finish();
            });
        }, "BRP quit").start();
    }

    /**
     * Controlled reconnect: release input, stop this stream, start the stream
     * activity again for {@code appId}. The new stream waits in NvConnection
     * until this one has stopped; if a different entry is running on the
     * computer, NvConnection closes it before launching the new one.
     */
    void relaunch(int appId, String appName, boolean hdr, String reason) {
        if (leaving) {
            return;
        }
        leaving = true;
        controlCenter.closeNow();
        suspendInput();
        onStreamStopping();
        game.displayedFailureDialog = true; // no error dialog from the stream we are leaving

        Intent intent = new Intent(game.getIntent());
        intent.putExtra(Game.EXTRA_APP_ID, appId);
        intent.putExtra(Game.EXTRA_APP_NAME, appName);
        intent.putExtra(Game.EXTRA_APP_HDR, hdr);
        intent.putExtra(EXTRA_REASON, reason);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        // Start the next stream only after this one has ended on the host
        // (NvConnection launches over HTTP before its start/stop semaphore).
        game.stopConnectionThen(() -> game.relaunchWith(intent));
    }

    boolean isLeaving() {
        return leaving;
    }
}
