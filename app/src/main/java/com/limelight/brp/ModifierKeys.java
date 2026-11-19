package com.limelight.brp;

/**
 * Ctrl, Alt, Shift and Meta pressed from the on-screen key bar.
 *
 * A modifier is either held (pressed while the finger is down) or latched (a tap
 * presses it, the next tap releases it). Either way the host sees a real key
 * down for the modifier, so any key typed afterwards, including from the phone's
 * own keyboard, arrives as a shortcut. {@link #releaseAll()} sends a key up for
 * every modifier still down; the stream calls it whenever input may be lost
 * (bar closed, menu opened, focus lost, rotation, disconnect).
 *
 * Pure Java: no Android dependencies, unit-tested.
 */
public final class ModifierKeys {
    /** Where key events go (the stream connection in the app, a recorder in tests). */
    public interface KeySink {
        void key(short windowsKeyCode, boolean down, byte modifierFlags);
    }

    public enum Modifier {
        // Windows virtual-key codes for the left-hand keys, and the protocol's flag bits
        // (KeyboardPacket.MODIFIER_*).
        SHIFT((short) 0xA0, (byte) 0x01),
        CTRL((short) 0xA2, (byte) 0x02),
        ALT((short) 0xA4, (byte) 0x04),
        META((short) 0x5B, (byte) 0x08);

        public final short keyCode;
        public final byte flag;

        Modifier(short keyCode, byte flag) {
            this.keyCode = keyCode;
            this.flag = flag;
        }
    }

    private final KeySink sink;
    private byte down; // flags of modifiers currently down on the host

    public ModifierKeys(KeySink sink) {
        this.sink = sink;
    }

    /** Flags of the modifiers the host currently has down. */
    public byte flags() {
        return down;
    }

    public boolean isDown(Modifier m) {
        return (down & m.flag) != 0;
    }

    public void press(Modifier m) {
        if (isDown(m)) {
            return;
        }
        down |= m.flag;
        sink.key(m.keyCode, true, down);
    }

    public void release(Modifier m) {
        if (!isDown(m)) {
            return;
        }
        down &= ~m.flag;
        sink.key(m.keyCode, false, down);
    }

    /** Latched behaviour: a tap flips the modifier. Returns the new state. */
    public boolean toggle(Modifier m) {
        if (isDown(m)) {
            release(m);
            return false;
        }
        press(m);
        return true;
    }

    /** Presses and releases a non-modifier key with the modifiers currently down. */
    public void tap(short keyCode) {
        sink.key(keyCode, true, down);
        sink.key(keyCode, false, down);
    }

    /**
     * A shortcut such as Ctrl+Alt+Del: the listed modifiers that are not already
     * down are pressed, the key is tapped, and only those modifiers are released
     * again (in reverse order), so latched modifiers stay as the person left them.
     */
    public void chord(short keyCode, Modifier... modifiers) {
        byte added = 0;
        for (Modifier m : modifiers) {
            if (!isDown(m)) {
                press(m);
                added |= m.flag;
            }
        }
        if (keyCode != 0) {
            tap(keyCode);
        }
        for (int i = modifiers.length - 1; i >= 0; i--) {
            if ((added & modifiers[i].flag) != 0) {
                release(modifiers[i]);
            }
        }
    }

    /** Sends a key up for every modifier still down. Safe to call at any time. */
    public void releaseAll() {
        Modifier[] all = Modifier.values();
        for (int i = all.length - 1; i >= 0; i--) {
            release(all[i]);
        }
    }
}
