package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsIncomingCommandTest {
    @Test public void acceptsExactSenderAndSignedNumericCommand() {
        SmsIncomingCommand.Result result = SmsIncomingCommand.parse(
                "+7 (999) 123-45-67", "+79991234567", "Уставка", "Уставка -12",
                SmsIncomingCommand.Mode.REGISTER_NUMBER);
        assertNotNull(result);
        assertEquals(65524, result.registerValue);
    }

    @Test public void rejectsWrongSenderCommandAmbiguityAndOutOfRange() {
        assertNull(parse("+79990000000", "Уставка 1"));
        assertNull(parse("+79991234567", "уставка 1"));
        assertNull(parse("+79991234567", "Уставка 1 2"));
        assertNull(parse("+79991234567", "Уставка 32768"));
        assertNull(parse("+79991234567", "Уставка -32769"));
    }

    @Test public void bitModeRequiresExactMessageAndWhitelistedSender() {
        SmsIncomingCommand.Result bit = SmsIncomingCommand.parse("+79991234567", "+79991234567", "ВКЛ",
                "ВКЛ", SmsIncomingCommand.Mode.SET_BIT, 7);
        assertNotNull(bit);
        assertEquals(0xff7f, bit.writeAndMask);
        assertEquals(0x0080, bit.writeOrMask);
        assertNull(SmsIncomingCommand.parse("+79991234567", "+79991234567", "ВКЛ",
                "ВКЛ 1", SmsIncomingCommand.Mode.SET_BIT));
        assertNull(SmsIncomingCommand.parse("+79991234567", "+79991234567", "ВКЛ",
                "ВКЛ", SmsIncomingCommand.Mode.SET_BIT, 16));
    }

    @Test public void senderMustBeUnambiguousInternationalNumber() {
        assertTrue(SmsIncomingCommand.isValidSender("+79991234567"));
        assertFalse(SmsIncomingCommand.isValidSender("123"));
        assertFalse(SmsIncomingCommand.isValidSender("+7 999 123 45 6x"));
    }

    private static SmsIncomingCommand.Result parse(String sender, String body) {
        return SmsIncomingCommand.parse("+79991234567", sender, "Уставка", body,
                SmsIncomingCommand.Mode.REGISTER_NUMBER);
    }
}
