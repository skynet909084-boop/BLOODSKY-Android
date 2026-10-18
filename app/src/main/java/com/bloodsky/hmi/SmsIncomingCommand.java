package com.bloodsky.hmi;

/** Fail-closed validation for the sender/command part of Craft incoming-SMS control. */
public final class SmsIncomingCommand {
    public enum Mode { REGISTER_NUMBER, SET_BIT }

    public static final class Result {
        public final Mode mode;
        /** 16-bit Modbus representation; negative input is encoded as two's complement. */
        public final int registerValue;
        private Result(Mode mode, int registerValue) {
            this.mode = mode;
            this.registerValue = registerValue;
        }
    }

    private SmsIncomingCommand() { }

    public static Result parse(String allowedSender, String receivedSender, String command,
                               String body, Mode mode) {
        if (mode == null || !samePhone(allowedSender, receivedSender)
                || command == null || command.trim().isEmpty() || body == null
                || body.length() > 512) return null;
        String exactCommand = command.trim();
        if (mode == Mode.SET_BIT)
            return exactCommand.equals(body) ? new Result(mode, 1) : null;
        String prefix = exactCommand + " ";
        if (!body.startsWith(prefix)) return null;
        String number = body.substring(prefix.length());
        if (!number.matches("-?(0|[1-9][0-9]{0,4})")) return null;
        try {
            int signed = Integer.parseInt(number);
            if (signed < -32768 || signed > 32767) return null;
            return new Result(mode, signed & 0xffff);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static boolean samePhone(String allowed, String received) {
        String expected = canonicalPhone(allowed), actual = canonicalPhone(received);
        return expected != null && expected.equals(actual);
    }

    private static String canonicalPhone(String phone) {
        if (phone == null) return null;
        String value = phone.trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else if (!(c == '+' && i == 0) && c != ' ' && c != '-' && c != '(' && c != ')') return null;
        }
        if (digits.length() < 7 || digits.length() > 15) return null;
        return digits.toString();
    }
}
