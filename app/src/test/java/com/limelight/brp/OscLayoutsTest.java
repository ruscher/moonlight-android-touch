package com.limelight.brp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OscLayoutsTest {
    // 16:9 (the grid itself), a 20:9 phone and a 4:3 tablet, all 1080 high.
    private static final int[][] SCREENS = {{1920, 1080}, {2400, 1080}, {1440, 1080}};

    private static final OscLayouts.Layout[] DRAWN = {
            OscLayouts.Layout.PAD, OscLayouts.Layout.FPS, OscLayouts.Layout.RACE, OscLayouts.Layout.FIGHT};

    @Test
    public void noElementsOverlapAndAllFitOnScreen() {
        for (OscLayouts.Layout layout : DRAWN) {
            for (boolean guide : new boolean[]{false, true}) {
                List<OscLayouts.Spec> specs = OscLayouts.specs(layout, false, guide);
                for (int[] screen : SCREENS) {
                    int w = screen[0], h = screen[1];
                    if (w * 9 < h * 16) {
                        continue; // narrower than 16:9: upstream's grid does not shrink either
                    }
                    for (int i = 0; i < specs.size(); i++) {
                        OscLayouts.Spec a = specs.get(i);
                        int ax = a.leftPx(w, h), ay = a.topPx(h), aw = a.widthPx(h), ah = a.heightPx(h);
                        assertTrue(layout + " " + a.id + " off screen", ax >= 0 && ay >= 0
                                && ax + aw <= w + 2 && ay + ah <= h + 2);
                        for (int j = i + 1; j < specs.size(); j++) {
                            OscLayouts.Spec b = specs.get(j);
                            int bx = b.leftPx(w, h), by = b.topPx(h), bw = b.widthPx(h), bh = b.heightPx(h);
                            boolean overlap = ax + 1 < bx + bw && bx + 1 < ax + aw && ay + 1 < by + bh && by + 1 < ay + ah;
                            assertFalse(layout + " at " + w + "x" + h + ": " + a.id + " overlaps " + b.id, overlap);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void idsAreUniqueAndEssentialsPresent() {
        for (OscLayouts.Layout layout : DRAWN) {
            Set<Integer> ids = new HashSet<>();
            for (OscLayouts.Spec s : OscLayouts.specs(layout, false, true)) {
                assertTrue(layout + " duplicate id " + s.id, ids.add(s.id));
            }
            for (int required : new int[]{OscLayouts.EID_A, OscLayouts.EID_B, OscLayouts.EID_X, OscLayouts.EID_Y,
                    OscLayouts.EID_START, OscLayouts.EID_BACK, OscLayouts.EID_GDB, OscLayouts.EID_DPAD,
                    OscLayouts.EID_LT, OscLayouts.EID_RT, OscLayouts.EID_LB, OscLayouts.EID_RB}) {
                assertTrue(layout + " lacks " + required, ids.contains(required));
            }
        }
    }

    @Test
    public void flippedFaceButtonsSwapLabelsNotPositions() {
        OscLayouts.Spec a = find(OscLayouts.specs(OscLayouts.Layout.PAD, false, false), OscLayouts.EID_A);
        OscLayouts.Spec f = find(OscLayouts.specs(OscLayouts.Layout.PAD, true, false), OscLayouts.EID_A);
        assertEquals("A", a.label);
        assertEquals("B", f.label);
        assertEquals(a.x, f.x);
    }

    @Test
    public void eachLayoutHasItsOwnSavedEditsAndClassicKeepsUpstreams() {
        Set<String> names = new HashSet<>();
        for (OscLayouts.Layout l : OscLayouts.Layout.values()) {
            assertTrue(names.add(l.prefsName));
        }
        assertEquals("OSC", OscLayouts.Layout.CLASSIC.prefsName);
        assertTrue(OscLayouts.specs(OscLayouts.Layout.CLASSIC, false, true).isEmpty());
        assertEquals(OscLayouts.Layout.FPS, OscLayouts.Layout.fromKey("fps", OscLayouts.Layout.PAD));
        assertEquals(OscLayouts.Layout.PAD, OscLayouts.Layout.fromKey("nope", OscLayouts.Layout.PAD));
    }

    private static OscLayouts.Spec find(List<OscLayouts.Spec> specs, int id) {
        for (OscLayouts.Spec s : specs) {
            if (s.id == id) {
                return s;
            }
        }
        throw new AssertionError("missing " + id);
    }
}
