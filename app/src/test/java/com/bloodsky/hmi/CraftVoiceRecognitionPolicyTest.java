package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CraftVoiceRecognitionPolicyTest {
    private static String[] phrases(String phrase, int index) {
        String[] values = new String[CraftVoiceRecognitionPolicy.PHRASE_COUNT];
        values[index] = phrase;
        return values;
    }

    @Test public void exactConfiguredPhraseIsMatchedIgnoringCasePunctuationAndYo() {
        assertEquals(4, CraftVoiceRecognitionPolicy.match(phrases("Вперёд насос!", 4), "вперед насос"));
    }

    @Test public void emptyDynamicAndAmbiguousPhrasesFailClosed() {
        String[] dynamic = phrases("Насос #12", 0);
        assertEquals(-1, CraftVoiceRecognitionPolicy.match(dynamic, "Насос 12"));
        assertEquals(-1, CraftVoiceRecognitionPolicy.match(phrases("", 0), ""));
        String[] duplicate = phrases("Насос", 1); duplicate[8] = "насос!";
        assertEquals(-1, CraftVoiceRecognitionPolicy.match(duplicate, "Насос"));
    }

    @Test public void acceptedPhraseSetsOnlyItsCraftRegisterBit() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        assertTrue(CraftVoiceRecognitionPolicy.apply(phrases("включи насос", 6), "Включи насос", 41, bank, 1L));
        assertTrue(bank.readBit(41, 6));
        assertFalse(bank.readBit(41, 5));
    }

    @Test public void invalidOrServiceRegisterDoesNotWrite() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        assertFalse(CraftVoiceRecognitionPolicy.apply(phrases("запуск", 0), "запуск", 9, bank, 1L));
        assertFalse(CraftVoiceRecognitionPolicy.apply(phrases("запуск", 0), "запуск", 2000, bank, 1L));
    }
}
