package com.bloodsky.hmi;

import java.util.List;
import java.util.Locale;

/** Deterministic Russian voice-command allowlist for local BLOODSKY actions. */
public final class VoiceCommandParser {
    public enum Type { NEXT_SCREEN, PREVIOUS_SCREEN, OPEN_SCREEN, START_PROJECT, STOP_PROJECT, UNKNOWN }

    public static final class Command {
        public final Type type;
        public final int screenIndex;
        private Command(Type type, int screenIndex) { this.type = type; this.screenIndex = screenIndex; }
    }

    private VoiceCommandParser() {}

    public static Command parse(String phrase, List<String> screenNames) {
        String text = normalize(phrase);
        if (text.isEmpty()) return new Command(Type.UNKNOWN, -1);
        if (matches(text, "следующий экран", "дальше экран", "вперед экран", "вперёд экран"))
            return new Command(Type.NEXT_SCREEN, -1);
        if (matches(text, "предыдущий экран", "назад экран", "назад на экран"))
            return new Command(Type.PREVIOUS_SCREEN, -1);
        if (matches(text, "запусти проект", "запустить проект", "старт проекта", "начать работу", "запусти", "старт"))
            return new Command(Type.START_PROJECT, -1);
        if (matches(text, "останови проект", "остановить проект", "стоп проекта", "останови", "стоп", "режим редактирования"))
            return new Command(Type.STOP_PROJECT, -1);

        String target = text;
        String[] prefixes = {"перейди на экран ", "перейди экран ", "открой экран ", "открой ", "экран "};
        for (String prefix : prefixes) if (target.startsWith(prefix)) { target = target.substring(prefix.length()).trim(); break; }
        if (target.matches("\\d{1,2}")) {
            int number;
            try { number = Integer.parseInt(target); } catch (NumberFormatException invalid) { return new Command(Type.UNKNOWN, -1); }
            if (number > 0 && number <= (screenNames == null ? 0 : screenNames.size()))
                return new Command(Type.OPEN_SCREEN, number - 1);
        }
        if (screenNames != null) for (int i = 0; i < screenNames.size(); i++) {
            if (normalize(screenNames.get(i)).equals(target)) return new Command(Type.OPEN_SCREEN, i);
        }
        return new Command(Type.UNKNOWN, -1);
    }

    private static boolean matches(String text, String... values) {
        for (String value : values) if (text.equals(value)) return true;
        return false;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
}
