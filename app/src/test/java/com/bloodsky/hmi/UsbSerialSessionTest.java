package com.bloodsky.hmi;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class UsbSerialSessionTest {
    @Test public void appliesExactFramingAndExchangesRtuRequestAndResponse() throws Exception {
        FakePort port = new FakePort(response(17, 3, new byte[] {0x12, 0x34}));
        UsbSerialSettings settings = new UsbSerialSettings(19200, 7, 2, UsbSerialSettings.Parity.EVEN);
        try (UsbSerialSession session = UsbSerialSession.open(port, settings, 800)) {
            assertArrayEquals(new int[] {0x1234}, session.read(17, 3, 0x0102, 1));
        }
        assertEquals(19200, port.baud);
        assertEquals(7, port.dataBits);
        assertEquals(2, port.stopBits);
        assertEquals(UsbSerialSettings.Parity.EVEN.ordinal(), port.parity);
        assertEquals(800, port.readTimeout);
        assertEquals(800, port.writeTimeout);
        assertArrayEquals(ModbusRtuClient.buildReadRequest(17, 3, 0x0102, 1), port.written.toByteArray());
        assertEquals(1, port.closeCount);
    }

    @Test public void sendsAndVerifiesRegisterWritesThroughTheSerialPort() throws Exception {
        FakePort port = new FakePort(writeResponse(17, new byte[] {6, 1, 2, 0x12, 0x34}));
        try (UsbSerialSession session = UsbSerialSession.open(port, settings(), 800)) {
            session.writeSingle(17, 0x0102, 0x1234);
        }
        assertArrayEquals(writeResponse(17, new byte[] {6, 1, 2, 0x12, 0x34}), port.written.toByteArray());
        assertEquals(1, port.closeCount);
    }

    @Test public void supportsEveryDriverParityAndOneAndOneAndHalfStopBits() throws Exception {
        for (UsbSerialSettings.Parity parity : UsbSerialSettings.Parity.values()) {
            for (int stopBits : new int[] {1, 2, 3}) {
                FakePort port = new FakePort(new byte[0]);
                UsbSerialSession session = UsbSerialSession.open(port,
                        new UsbSerialSettings(9600, 8, stopBits, parity), 100);
                assertEquals(parity.ordinal(), port.parity);
                assertEquals(stopBits, port.stopBits);
                session.close();
            }
        }
    }

    @Test public void configurationFailureClosesPortExactlyOnce() throws Exception {
        FakePort port = new FakePort(new byte[0]);
        port.failConfigure = true;
        try {
            UsbSerialSession.open(port, settings(), 100);
            fail("Expected configuration failure");
        } catch (IOException expected) {
            assertEquals(1, port.closeCount);
        }
    }

    @Test public void readTimeoutAbortsSessionAndClosesPort() throws Exception {
        FakePort port = new FakePort(new byte[0]);
        port.timeoutOnRead = true;
        UsbSerialSession session = UsbSerialSession.open(port, settings(), 500);
        try {
            session.read(1, 3, 0, 1);
            fail("Expected timeout");
        } catch (SocketTimeoutException expected) {
            assertTrue(port.closeCount >= 1);
        } finally {
            session.close();
        }
        assertEquals(1, port.closeCount);
    }

    @Test public void serialIoFailureClosesPortAndRepeatedCloseIsSafe() throws Exception {
        FakePort port = new FakePort(new byte[0]);
        port.failWrite = true;
        UsbSerialSession session = UsbSerialSession.open(port, settings(), 500);
        try {
            session.read(1, 3, 0, 1);
            fail("Expected write error");
        } catch (IOException expected) {
            assertEquals(1, port.closeCount);
        } finally {
            session.close();
            session.close();
        }
        assertEquals(1, port.closeCount);
    }

    @Test public void validatesSettingsBeforeUsingPort() throws Exception {
        FakePort port = new FakePort(new byte[0]);
        try {
            new UsbSerialSettings(0, 8, 1, UsbSerialSettings.Parity.NONE);
            fail("Expected invalid baud");
        } catch (IllegalArgumentException expected) { }
        try {
            new UsbSerialSettings(9600, 9, 1, UsbSerialSettings.Parity.NONE);
            fail("Expected invalid data bits");
        } catch (IllegalArgumentException expected) { }
        try {
            new UsbSerialSettings(9600, 8, 4, UsbSerialSettings.Parity.NONE);
            fail("Expected invalid stop bits");
        } catch (IllegalArgumentException expected) { }
        assertEquals(0, port.closeCount);
    }

    private static UsbSerialSettings settings() {
        return new UsbSerialSettings(9600, 8, 1, UsbSerialSettings.Parity.NONE);
    }

    private static final class FakePort implements UsbSerialSession.Port {
        final byte[] incoming;
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        int offset, baud, dataBits, stopBits, parity, readTimeout, writeTimeout, closeCount;
        boolean failConfigure, failWrite, timeoutOnRead;

        FakePort(byte[] incoming) { this.incoming = Arrays.copyOf(incoming, incoming.length); }
        @Override public void configure(int baud, int dataBits, int stopBits, int parity) throws IOException {
            if (failConfigure) throw new IOException("configure failed");
            this.baud = baud; this.dataBits = dataBits; this.stopBits = stopBits; this.parity = parity;
        }
        @Override public int read(byte[] buffer, int timeoutMs) throws IOException {
            readTimeout = timeoutMs;
            if (timeoutOnRead) throw new SocketTimeoutException("read timed out");
            int count = Math.min(buffer.length, incoming.length - offset);
            if (count <= 0) return 0;
            System.arraycopy(incoming, offset, buffer, 0, count);
            offset += count;
            return count;
        }
        @Override public void write(byte[] buffer, int length, int timeoutMs) throws IOException {
            writeTimeout = timeoutMs;
            if (failWrite) throw new IOException("write failed");
            written.write(buffer, 0, length);
        }
        @Override public void close() { closeCount++; }
    }

    private static byte[] response(int unit, int function, byte[] payload) {
        byte[] pdu = new byte[3 + payload.length];
        pdu[0] = (byte) unit;
        pdu[1] = (byte) function;
        pdu[2] = (byte) payload.length;
        System.arraycopy(payload, 0, pdu, 3, payload.length);
        int crc = ModbusRtuClient.crc16(pdu, pdu.length);
        byte[] result = Arrays.copyOf(pdu, pdu.length + 2);
        result[result.length - 2] = (byte) crc;
        result[result.length - 1] = (byte) (crc >>> 8);
        return result;
    }

    private static byte[] writeResponse(int unit, byte[] pdu) {
        byte[] body = new byte[pdu.length + 1];
        body[0] = (byte) unit;
        System.arraycopy(pdu, 0, body, 1, pdu.length);
        int crc = ModbusRtuClient.crc16(body, body.length);
        byte[] result = Arrays.copyOf(body, body.length + 2);
        result[result.length - 2] = (byte) crc;
        result[result.length - 1] = (byte) (crc >>> 8);
        return result;
    }
}
