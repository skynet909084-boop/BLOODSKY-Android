package com.bloodsky.hmi;

import java.text.Normalizer;
import java.util.Locale;

/** Fail-closed matching for the explicitly started, local Craft voice widget. */
public final class CraftVoiceRecognitionPolicy {
    public static final int PHRASE_COUNT = 16;
    private CraftVoiceRecognitionPolicy() { }

    /** Return the unique configured literal phrase index, or -1 for no/ambiguous/dynamic match. */
    public static int match(String[] configured, String recognized) {
        if (configured == null || configured.length != PHRASE_COUNT) return -1;
        String candidate = normalize(recognized);
        if (candidate.isEmpty()) return -1;
        int matched = -1;
        for (int i = 0; i < configured.length; i++) {
            String phrase = configured[i];
            if (phrase == null || phrase.indexOf('#') >= 0) continue;
            if (candidate.equals(normalize(phrase))) {
                if (matched >= 0) return -1;
                matched = i;
            }
        }
        return matched;
    }

    public static boolean validProjectRegister(int address) {
        return address >= CraftSystemRegisterBank.FIRST_USER_REGISTER
                && address <= CraftSystemRegisterBank.LAST_USER_REGISTER;
    }

    /** Craft sets the bit matching the phrase slot in the component's configured register. */
    public static boolean apply(String[] configured, String recognized, int address,
                                 CraftSystemRegisterBank bank, long nowMillis) {
        int bit = match(configured, recognized);
        if (bit < 0 || !validProjectRegister(address) || bank == null) return false;
        return CraftSystemRegisterAccess.writeBool(address, bit, true, nowMillis, bank);
    }

    private static String normalize(String phrase) {
        if (phrase == null) return "";
        return Normalizer.normalize(phrase, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
}
