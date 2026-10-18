package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class VoiceCommandParserTest {
    @Test public void recognizesAllowlistedProjectAndNavigationCommands() {
        assertEquals(VoiceCommandParser.Type.START_PROJECT, VoiceCommandParser.parse("Запусти проект", null).type);
        assertEquals(VoiceCommandParser.Type.STOP_PROJECT, VoiceCommandParser.parse("Стоп", null).type);
        assertEquals(VoiceCommandParser.Type.NEXT_SCREEN, VoiceCommandParser.parse("Следующий экран", null).type);
        VoiceCommandParser.Command screen = VoiceCommandParser.parse("Перейди на экран 2", Arrays.asList("Пульт", "Насосная"));
        assertEquals(VoiceCommandParser.Type.OPEN_SCREEN, screen.type);
        assertEquals(1, screen.screenIndex);
    }

    @Test public void resolvesScreenNamesAndDoesNotExecuteUnknownPhrases() {
        VoiceCommandParser.Command named = VoiceCommandParser.parse("Открой насосная", Arrays.asList("Пульт", "Насосная"));
        assertEquals(VoiceCommandParser.Type.OPEN_SCREEN, named.type);
        assertEquals(1, named.screenIndex);
        assertEquals(VoiceCommandParser.Type.UNKNOWN, VoiceCommandParser.parse("включи неизвестный клапан", Arrays.asList("Пульт")).type);
        assertEquals(VoiceCommandParser.Type.UNKNOWN, VoiceCommandParser.parse("Экран 9", Arrays.asList("Пульт")).type);
    }
}
