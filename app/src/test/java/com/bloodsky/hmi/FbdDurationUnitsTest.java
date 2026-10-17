package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdDurationUnitsTest {
    @Test public void milliSecondSecondMinuteModesHaveExactRoundTrips() {
        assertEquals(2345, FbdDurationUnits.parse("2345", "MS", 2345));
        assertEquals("2.345", FbdDurationUnits.display(2345, "SEK"));
        assertEquals(2345, FbdDurationUnits.parse("2,345", "SEK", 1000));
        assertEquals("0.039083333333", FbdDurationUnits.display(2345, "MIN"));
        assertEquals("0.039083333333", FbdDurationUnits.display(2345, "MIN"));
        assertEquals(2345, FbdDurationUnits.parse("0.039083333333", "MIN", 2345));
        assertEquals("0.000016666667", FbdDurationUnits.display(1, "MIN"));
        assertEquals(1, FbdDurationUnits.parse("0.000016666667", "MIN", 1));
        assertEquals(3600000, FbdDurationUnits.parse("60", "MIN", 3600000));
        assertEquals("1", FbdDurationUnits.display(1000, "SEK"));
        assertEquals("MS", FbdDurationUnits.normalize("UNKNOWN"));
    }

    @Test public void editedDurationsCannotRoundFractionalMillisecondsOrOverflow() {
        for (String[] pair : new String[][]{
                {"0", "MS"}, {"-1", "MS"}, {"3600001", "MS"},
                {"0.1", "MS"}, {"0.0005", "SEK"}, {"1.5", "MIN"},
                {"1.0000000001", "MS"}, {"1e50", "MS"}, {"NaN", "SEK"},
                {"Infinity", "MIN"}, {"", "MS"}, {"  ", "MS"},
                {"1,2,3", "SEK"}, {"6e-10", "MIN"}
        }) {
            try {
                FbdDurationUnits.parse(pair[0], pair[1], 1000);
                fail("Must reject invalid duration " + pair[0] + " " + pair[1]);
            } catch (IllegalArgumentException expected) {}
        }
        assertEquals(1500, FbdDurationUnits.parse("1.5", "SEK", 1));
        assertEquals(30000, FbdDurationUnits.parse("0,5", "MIN", 1));
    }

    @Test public void modeChangesPreserveUnrelatedMetadata() throws Exception {
        for (String mode : FbdDurationUnits.modes()) {
            String type = FbdDurationUnits.typeForMode(mode);
            assertEquals(mode, FbdDurationUnits.modeForType(type));
            MainActivity.Widget widget = new MainActivity.Widget();
            widget.type = type;
            widget.timerPresetMs = 2345;
            widget.extraFields.put("bloodskyFbdTimeUnit", "SEK");
            widget.extraFields.put("originalUnknownField", "still here");
            MainActivity.Widget round = MainActivity.widgetFromJson(MainActivity.widgetToJson(widget));
            assertEquals(2345, round.timerPresetMs);
            assertEquals("SEK", round.extraFields.optString("bloodskyFbdTimeUnit"));
            assertEquals("still here", round.extraFields.optString("originalUnknownField"));
        }
    }

    @Test public void sourceSkinIsRestrictedToDefaultStylesAndKnownSourceBlocks() {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "FBD И";
        widget.w = 160;
        widget.h = 80;
        assertTrue(FbdSourceCanvasSkin.shouldUse(widget));
        assertEquals(.25f, FbdSourceCanvasSkin.inputY(widget.type, "IN1"), .00001f);
        assertEquals(.75f, FbdSourceCanvasSkin.inputY(widget.type, "IN2"), .00001f);
        widget.type = "FBD XOR";
        assertTrue(FbdSourceCanvasSkin.shouldUse(widget));
        assertEquals(.5f, FbdSourceCanvasSkin.inputY(widget.type, "IN1"), .00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY(widget.type, "IN2"), .00001f);
        widget.type = "FBD TP";
        assertEquals(.5f, FbdSourceCanvasSkin.inputY(widget.type, "IN"), .00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY(widget.type, "PT"), .00001f);
        widget.type = "FBD CTU";
        assertFalse(FbdSourceCanvasSkin.shouldUse(widget));
        widget.type = "FBD И";
        widget.backgroundColor = 0xff123456;
        assertFalse(FbdSourceCanvasSkin.shouldUse(widget));
    }
}
