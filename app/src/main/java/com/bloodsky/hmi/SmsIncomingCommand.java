package com.bloodsky.hmi;

/** Fail-closed validation for the sender/command part of Craft incoming-SMS control. */
public final class SmsIncomingCommand {
    public enum Mode { REGISTER_NUMBER, SET_BIT }

    public static final class Result {
        public final Mode mode;
        /** 16-bit Modbus representation; negative input is encoded as two's complement. */
        public final int registerValue;
        /** Numeric mode may update one to sixteen contiguous registers. */
        public final int[] registerValues;
        /** FC22 masks for bit mode; other register bits are preserved atomically. */
        public final int writeAndMask;
        public final int writeOrMask;
        private Result(Mode mode, int[] registerValues, int writeAndMask, int writeOrMask) {
            this.mode = mode;
            this.registerValues = registerValues.clone();
            this.registerValue = registerValues.length == 0 ? 0 : registerValues[0];
            this.writeAndMask = writeAndMask;
            this.writeOrMask = writeOrMask;
        }
    }

    private SmsIncomingCommand() { }

    public static Result parse(String allowedSender, String receivedSender, String command,
                               String body, Mode mode) {
        return parse(allowedSender, receivedSender, command, body, mode, 0);
    }

    public static Result parse(String allowedSender, String receivedSender, String command,
                               String body, Mode mode, int bitIndex) {
        if (mode == null || !samePhone(allowedSender, receivedSender)
                || command == null || command.trim().isEmpty() || body == null
                || body.length() > 512) return null;
        String exactCommand = command.trim();
        if (mode == Mode.SET_BIT) {
            if (!exactCommand.equals(body) || bitIndex < 0 || bitIndex > 15) return null;
            int mask = 1 << bitIndex;
            return new Result(mode, new int[]{1}, 0xffff ^ mask, mask);
        }
        String prefix = exactCommand + " ";
        if (!body.startsWith(prefix)) return null;
        String numericPayload = body.substring(prefix.length()).trim();
        if (numericPayload.isEmpty()) return null;
        String[] tokens = numericPayload.split("\\s+");
        if (tokens.length > 16) return null;
        int[] values = new int[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                if (!tokens[i].matches("-?(0|[1-9][0-9]{0,4})")) return null;
                int signed = Integer.parseInt(tokens[i]);
                if (signed < -32768 || signed > 32767) return null;
                values[i] = signed & 0xffff;
            }
            return new Result(mode, values, 0xffff, values[0]);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static boolean samePhone(String allowed, String received) {
        String expected = canonicalPhone(allowed), actual = canonicalPhone(received);
        return expected != null && expected.equals(actual);
    }

    public static boolean isValidSender(String phone) { return canonicalPhone(phone) != null; }

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
