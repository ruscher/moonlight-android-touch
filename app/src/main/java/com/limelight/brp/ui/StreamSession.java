package com.limelight.brp.ui;

import com.limelight.brp.HostEntries;
import com.limelight.brp.StreamStats;

/**
 * What the stream overlays may read and ask of the running stream. Implemented
 * by the stream activity; keeps the overlays free of protocol code and the
 * activity free of overlay code.
 *
 * Every method is called on the UI thread.
 */
public interface StreamSession {
    // --- What is playing
    String pcName();
    String appName();
    int appId();
    /** Last second of measurements, or null before the first frames. */
    StreamStats stats();

    // --- Video settings (saved settings, applied by reconnecting)
    int bitrateKbps();
    String resolutionSetting();   // "1920x1080", "Native", ...
    String fpsSetting();          // "60"
    String videoFormatSetting();  // "auto", "neverh265", "forceh265", "forceav1"
    int defaultBitrateKbps(String resolution, String fps);
    boolean hevcSupported();
    boolean av1Supported();
    /** Highest refresh rate this screen offers, in Hz. */
    int maxDisplayRefreshRate();
    void applyVideoAndReconnect(String resolution, String fps, int bitrateKbps, String videoFormat);
    void lowerQualityAndReconnect();

    // --- Display
    /** False when the stream already has the screen's aspect ratio (nothing to fit). */
    boolean canStretch();
    boolean isStretched();
    void setStretched(boolean stretched);
    /** The host's entries, or null while they are being read. */
    HostEntries hostEntries();
    /** True when the list could not be read (the overlay then says so). */
    boolean hostEntriesFailed();
    void switchTo(HostEntries.Entry entry);

    // --- Input
    /** The Control Center is about to cover the stream: release everything held. */
    void suspendInput();
    void resumeInput();
    void showPhoneKeyboard();
    boolean isKeyboardBarVisible();
    void setKeyboardBarVisible(boolean visible);
    boolean stickyModifiers();
    void setStickyModifiers(boolean sticky);
    /**
     * Types the text copied on the phone on the computer (the protocol's UTF-8
     * text channel). Returns the number of characters sent, 0 when the phone's
     * clipboard has no text.
     */
    int typePhoneClipboard();
    /** Sends a shortcut; modifiers already latched on the key bar stay as they are. */
    void sendShortcut(short keyCode, com.limelight.brp.ModifierKeys.Modifier... modifiers);

    // --- On-screen controller
    boolean onScreenControllerVisible();
    void setOnScreenControllerVisible(boolean visible);
    void editOnScreenControllerLayout();
    /** Layout key ({@link com.limelight.brp.OscLayouts.Layout#key}) of the controller on the phone. */
    String onScreenControllerLayout();
    /** Switches layout, shows the controller; saved for the next streams. */
    void setOnScreenControllerLayout(String key);
    /** Forgets the moves and resizes of the current layout. */
    void resetOnScreenControllerLayout();
    int onScreenControllerOpacity();
    void setOnScreenControllerOpacity(int percent);
    /** Native touch on: the host may draw its own controller (Touch controls). */
    boolean nativeTouchEnabled();

    // --- Overlay and session
    int PERF_OFF = 0, PERF_MINIMAL = 1, PERF_DETAILED = 2;
    int perfOverlayMode();
    void setPerfOverlayMode(int mode);
    boolean pipSupported();
    void enterPip();
    void reconnect();
    void disconnect();
    void quitOnHost();
}
