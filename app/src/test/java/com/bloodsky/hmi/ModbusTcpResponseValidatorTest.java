package com.bloodsky.hmi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import org.junit.Test;

public class ModbusTcpResponseValidatorTest {
    private byte[] header(int tx, int protocol, int length, int unit) {
        return new byte[] {(byte)(tx >>> 8), (byte)tx, (byte)(protocol >>> 8), (byte)protocol,
                (byte)(length >>> 8), (byte)length, (byte)unit};
    }

    @Test public void acceptsValidHeaderAndRegisterPdu() throws Exception {
        assertEquals(7, ModbusTcpResponseValidator.validateHeader(header(0x1234, 0, 7, 1), 0x1234, 1, 3, 2));
        assertArrayEquals(new int[] {0x1234, 42},
                ModbusTcpResponseValidator.validateAndDecodePdu(
                        new byte[] {3, 4, 0x12, 0x34, 0, 42}, 3, 2));
    }

    @Test public void rejectsMismatchedTransactionId() throws Exception {
        expectIOException("transaction", () -> ModbusTcpResponseValidator.validateHeader(header(2, 0, 5, 1), 1, 1, 3, 1));
    }

    @Test public void rejectsNonzeroProtocolId() throws Exception {
        expectIOException("protocol", () -> ModbusTcpResponseValidator.validateHeader(header(1, 1, 5, 1), 1, 1, 3, 1));
    }

    @Test public void rejectsMismatchedUnitId() throws Exception {
        expectIOException("unit", () -> ModbusTcpResponseValidator.validateHeader(header(1, 0, 5, 2), 1, 1, 3, 1));
    }

    @Test public void rejectsInvalidDeclaredLength() throws Exception {
        expectIOException("length", () -> ModbusTcpResponseValidator.validateHeader(header(1, 0, 6, 1), 1, 1, 3, 1));
    }

    @Test public void rejectsTruncatedHeader() throws Exception {
        expectIOException("header", () -> ModbusTcpResponseValidator.validateHeader(new byte[] {0, 1}, 1, 1, 3, 1));
    }

    @Test public void rejectsUnexpectedFunctionCode() throws Exception {
        expectIOException("function", () -> ModbusTcpResponseValidator.validateAndDecodePdu(new byte[] {4, 2, 0, 1}, 3, 1));
    }

    @Test public void rejectsWrongByteCountAndTruncatedPdu() throws Exception {
        expectIOException("byte count", () -> ModbusTcpResponseValidator.validateAndDecodePdu(new byte[] {3, 4, 0, 1}, 3, 2));
        expectIOException("PDU", () -> ModbusTcpResponseValidator.validateAndDecodePdu(new byte[] {3}, 3, 1));
    }

    @Test public void reportsModbusExceptionResponse() throws Exception {
        expectIOException("exception response", () -> ModbusTcpResponseValidator.validateAndDecodePdu(new byte[] { (byte)0x83, 2 }, 3, 1));
    }

    @Test public void rejectsMalformedExceptionResponse() throws Exception {
        expectIOException("exception", () -> ModbusTcpResponseValidator.validateAndDecodePdu(new byte[] { (byte)0x83, 2, 0 }, 3, 1));
    }

    private interface CheckedAction { void run() throws Exception; }

    private void expectIOException(String expectedMessage, CheckedAction action) throws Exception {
        try {
            action.run();
            fail("Expected IOException containing: " + expectedMessage);
        } catch (IOException expected) {
            assertTrue("Actual message: " + expected.getMessage(),
                    expected.getMessage().toLowerCase(java.util.Locale.ROOT).contains(expectedMessage.toLowerCase(java.util.Locale.ROOT)));
        }
    }
}
