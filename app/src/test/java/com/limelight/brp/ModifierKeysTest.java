package com.limelight.brp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ModifierKeysTest {
    private final List<String> sent = new ArrayList<>();
    private ModifierKeys keys;

    @Before
    public void setUp() {
        sent.clear();
        keys = new ModifierKeys(new ModifierKeys.KeySink() {
            @Override
            public void key(short code, boolean down, byte flags) {
                sent.add(String.format("%02X%s/%d", code, down ? "v" : "^", flags));
            }
        });
    }

    @Test
    public void latchedCtrlStaysDownUntilTappedAgain() {
        assertTrue(keys.toggle(ModifierKeys.Modifier.CTRL));
        keys.tap((short) 0x43); // C
        assertTrue(keys.isDown(ModifierKeys.Modifier.CTRL));
        assertFalse(keys.toggle(ModifierKeys.Modifier.CTRL));
        assertEquals(0, keys.flags());
        assertEquals("[A2v/2, 43v/2, 43^/2, A2^/0]", sent.toString());
    }

    @Test
    public void pressTwiceSendsOneDown() {
        keys.press(ModifierKeys.Modifier.SHIFT);
        keys.press(ModifierKeys.Modifier.SHIFT);
        keys.release(ModifierKeys.Modifier.SHIFT);
        keys.release(ModifierKeys.Modifier.SHIFT);
        assertEquals("[A0v/1, A0^/0]", sent.toString());
    }

    @Test
    public void releaseAllLiftsEveryModifier() {
        keys.press(ModifierKeys.Modifier.CTRL);
        keys.press(ModifierKeys.Modifier.ALT);
        keys.press(ModifierKeys.Modifier.META);
        sent.clear();
        keys.releaseAll();
        assertEquals(0, keys.flags());
        assertEquals(3, sent.size());
        for (String s : sent) {
            assertTrue(s, s.contains("^"));
        }
        // Nothing left to release.
        sent.clear();
        keys.releaseAll();
        assertTrue(sent.isEmpty());
    }

    @Test
    public void chordKeepsLatchedModifiers() {
        keys.toggle(ModifierKeys.Modifier.CTRL);
        sent.clear();
        keys.chord((short) 0x2E, ModifierKeys.Modifier.CTRL, ModifierKeys.Modifier.ALT); // Ctrl+Alt+Del
        // Ctrl was already down: only Alt is pressed and released by the chord.
        assertEquals("[A4v/6, 2Ev/6, 2E^/6, A4^/2]", sent.toString());
        assertTrue(keys.isDown(ModifierKeys.Modifier.CTRL));
    }

    @Test
    public void chordWithoutKeyTapsModifiersOnly() {
        keys.chord((short) 0, ModifierKeys.Modifier.META); // Super
        assertEquals("[5Bv/8, 5B^/0]", sent.toString());
    }
}
