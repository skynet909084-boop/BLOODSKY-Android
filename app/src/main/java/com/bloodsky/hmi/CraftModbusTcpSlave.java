package com.bloodsky.hmi;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded foreground Modbus TCP server for a project's user register bank. */
public final class CraftModbusTcpSlave implements AutoCloseable {
    private static final int MAX_MBAP_LENGTH = 254;
    private static final int MAX_READ_REGISTERS = 125;
    private static final int MAX_WRITE_REGISTERS = 123;
    private static final int MAX_READ_COILS = 2000;
    private static final int MAX_WRITE_COILS = 1968;

    private final int port;
    private final int unitId;
    private final CraftSystemRegisterBank registers;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<Socket> activeSockets = Collections.newSetFromMap(
            new ConcurrentHashMap<Socket, Boolean>());
    private final ThreadPoolExecutor clients = new ThreadPoolExecutor(
            2, 4, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(16), r -> {
                Thread thread = new Thread(r, "bloodsky-modbus-client");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private volatile ServerSocket listener;
    private volatile Thread acceptThread;

    public CraftModbusTcpSlave(int port, int unitId, CraftSystemRegisterBank registers) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be 0..65535");
        if (unitId < 1 || unitId > 247) throw new IllegalArgumentException("unit ID must be 1..247");
        if (registers == null) throw new IllegalArgumentException("register bank required");
        this.port = port;
        this.unitId = unitId;
        this.registers = registers;
    }

    /** Binds synchronously so callers can report bind errors before entering runtime. */
    public synchronized int start() throws IOException {
        if (running.get()) return getPort();
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port), 16);
        listener = socket;
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "bloodsky-modbus-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return socket.getLocalPort();
    }

    public boolean isRunning() { return running.get(); }

    public int getPort() {
        ServerSocket socket = listener;
        return socket == null ? port : socket.getLocalPort();
    }

    @Override public synchronized void close() {
        running.set(false);
        ServerSocket socket = listener;
        listener = null;
        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
        for (Socket client : activeSockets) try { client.close(); } catch (IOException ignored) {}
        activeSockets.clear();
        ThreadPoolExecutor executor = clients;
        for (Runnable queued : executor.shutdownNow()) {
            if (queued instanceof ClientTask) ((ClientTask) queued).closeSocket();
        }
        Thread thread = acceptThread;
        acceptThread = null;
        if (thread != null) thread.interrupt();
    }

    private void acceptLoop() {
        while (running.get()) {
            Socket client = null;
            try {
                ServerSocket socket = listener;
                if (socket == null) break;
                client = socket.accept();
                client.setSoTimeout(30000);
                activeSockets.add(client);
                clients.execute(new ClientTask(client));
            } catch (SocketException stopped) {
                if (running.get()) running.set(false);
                break;
            } catch (IOException | RuntimeException failure) {
                if (client != null) { activeSockets.remove(client); try { client.close(); } catch (IOException ignored) {} }
                if (!running.get()) break;
            }
        }
        running.set(false);
    }

    private final class ClientTask implements Runnable {
        private final Socket socket;
        ClientTask(Socket socket) { this.socket = socket; }
        void closeSocket() { try { socket.close(); } catch (IOException ignored) {} }

        @Override public void run() {
            try (Socket client = socket;
                 DataInputStream input = new DataInputStream(client.getInputStream());
                 OutputStream output = client.getOutputStream()) {
                while (running.get() && !client.isClosed()) {
                    byte[] header = new byte[7];
                    readFully(input, header, 0, header.length);
                    int length = u16(header, 4);
                    if (length < 2 || length > MAX_MBAP_LENGTH) break;
                    byte[] request = new byte[6 + length];
                    System.arraycopy(header, 0, request, 0, header.length);
                    readFully(input, request, 7, length - 1);
                    byte[] response = respond(request, unitId, registers);
                    output.write(response);
                    output.flush();
                }
            } catch (EOFException | SocketException disconnected) {
                // A peer closing or timing out ends only its own session.
            } catch (IOException malformedOrClosed) {
                // Invalid/truncated frames close this client without touching the listener.
            } finally {
                activeSockets.remove(socket);
            }
        }
    }

    private static void readFully(InputStream input, byte[] target, int offset, int length)
            throws IOException {
        int end = offset + length;
        while (offset < end) {
            int read = input.read(target, offset, end - offset);
            if (read < 0) throw new EOFException("truncated Modbus TCP frame");
            offset += read;
        }
    }

    /**
     * Pure request processor shared by socket runtime and deterministic tests.
     * FC02 aliases project coil bits and FC04 aliases project holding words,
     * both read-only. Service registers remain inaccessible.
     */
    public static byte[] respond(byte[] request, int expectedUnitId,
                                 CraftSystemRegisterBank registers) {
        if (request == null || registers == null || request.length < 8
                || expectedUnitId < 1 || expectedUnitId > 247) return null;
        int length = u16(request, 4);
        if (u16(request, 2) != 0 || length < 2 || length > MAX_MBAP_LENGTH
                || request.length != 6 + length) return null;
        int unit = request[6] & 255;
        int function = request[7] & 255;
        int exception = unit == expectedUnitId ? 0 : 11;
        byte[] pdu;
        if (exception != 0) pdu = exception(function, exception);
        else {
            try {
                pdu = processPdu(request, function, registers);
            } catch (SlaveException invalid) {
                pdu = exception(function, invalid.code);
            } catch (RuntimeException malformed) {
                pdu = exception(function, 3);
            }
        }
        byte[] response = new byte[7 + pdu.length];
        response[0] = request[0]; response[1] = request[1];
        response[2] = request[2]; response[3] = request[3];
        putU16(response, 4, pdu.length + 1);
        response[6] = (byte) unit;
        System.arraycopy(pdu, 0, response, 7, pdu.length);
        return response;
    }

    private static byte[] processPdu(byte[] request, int function,
                                     CraftSystemRegisterBank registers) throws SlaveException {
        switch (function) {
            case 1: return readBits(request, registers, 1);
            case 2: return readBits(request, registers, 2);
            case 3: return readRegisters(request, registers, 3);
            case 4: return readRegisters(request, registers, 4);
            case 5: return writeCoil(request, registers);
            case 6: return writeRegister(request, registers);
            case 15: return writeCoils(request, registers);
            case 16: return writeRegisters(request, registers);
            case 22: return maskWriteRegister(request, registers);
            default: throw new SlaveException(1);
        }
    }

    private static byte[] readBits(byte[] request, CraftSystemRegisterBank bank, int function)
            throws SlaveException {
        requirePduLength(request, 12);
        int start = u16(request, 8), count = u16(request, 10);
        if (count < 1 || count > MAX_READ_COILS) throw new SlaveException(3);
        requireCoilRange(start, count);
        int byteCount = (count + 7) / 8;
        byte[] pdu = new byte[2 + byteCount];
        pdu[0] = (byte) function; pdu[1] = (byte) byteCount;
        for (int bit = 0; bit < count; bit++) {
            int address = start + bit;
            if (bank.readBit(address / 16, address % 16))
                pdu[2 + bit / 8] |= (byte) (1 << (bit % 8));
        }
        return pdu;
    }

    private static byte[] readRegisters(byte[] request, CraftSystemRegisterBank bank, int function)
            throws SlaveException {
        requirePduLength(request, 12);
        int start = u16(request, 8), count = u16(request, 10);
        if (count < 1 || count > MAX_READ_REGISTERS) throw new SlaveException(3);
        requireRegisterRange(start, count);
        byte[] pdu = new byte[2 + count * 2];
        pdu[0] = (byte) function; pdu[1] = (byte) (count * 2);
        for (int i = 0; i < count; i++) putU16(pdu, 2 + i * 2, bank.readWord(start + i) & 0xffff);
        return pdu;
    }

    private static byte[] writeCoil(byte[] request, CraftSystemRegisterBank bank)
            throws SlaveException {
        requirePduLength(request, 12);
        int address = u16(request, 8), raw = u16(request, 10);
        if (raw != 0 && raw != 0xff00) throw new SlaveException(3);
        requireCoilRange(address, 1);
        bank.writeBit(address / 16, address % 16, raw == 0xff00);
        return copyPdu(request, 7);
    }

    private static byte[] writeRegister(byte[] request, CraftSystemRegisterBank bank)
            throws SlaveException {
        requirePduLength(request, 12);
        int address = u16(request, 8);
        requireRegisterRange(address, 1);
        bank.writeWord(address, u16(request, 10));
        return copyPdu(request, 7);
    }

    private static byte[] writeCoils(byte[] request, CraftSystemRegisterBank bank)
            throws SlaveException {
        if (request.length < 13) throw new SlaveException(3);
        int start = u16(request, 8), count = u16(request, 10), byteCount = request[12] & 255;
        if (count < 1 || count > MAX_WRITE_COILS || byteCount != (count + 7) / 8
                || request.length != 13 + byteCount) throw new SlaveException(3);
        requireCoilRange(start, count);
        for (int i = 0; i < count; i++) {
            int address = start + i;
            boolean value = ((request[13 + i / 8] >>> (i % 8)) & 1) != 0;
            bank.writeBit(address / 16, address % 16, value);
        }
        return writeMultipleReply(request, 15);
    }

    private static byte[] writeRegisters(byte[] request, CraftSystemRegisterBank bank)
            throws SlaveException {
        if (request.length < 13) throw new SlaveException(3);
        int start = u16(request, 8), count = u16(request, 10), byteCount = request[12] & 255;
        if (count < 1 || count > MAX_WRITE_REGISTERS || byteCount != count * 2
                || request.length != 13 + byteCount) throw new SlaveException(3);
        requireRegisterRange(start, count);
        for (int i = 0; i < count; i++) bank.writeWord(start + i, u16(request, 13 + i * 2));
        return writeMultipleReply(request, 16);
    }

    /**
     * FC22 Mask Write Register (0x16), echoing the seven-byte request PDU.
     * Writes are atomic in the shared project bank, preventing concurrent
     * clients from losing one another's bit changes.
     */
    private static byte[] maskWriteRegister(byte[] request, CraftSystemRegisterBank bank)
            throws SlaveException {
        requirePduLength(request, 14);
        int address = u16(request, 8);
        requireRegisterRange(address, 1);
        bank.maskWriteWord(address, u16(request, 10), u16(request, 12));
        return copyPdu(request, 7);
    }

    private static byte[] writeMultipleReply(byte[] request, int function) {
        return new byte[]{(byte) function, request[8], request[9], request[10], request[11]};
    }

    private static byte[] copyPdu(byte[] request, int length) {
        byte[] pdu = new byte[request.length - 7];
        System.arraycopy(request, 7, pdu, 0, pdu.length);
        return pdu;
    }

    private static byte[] exception(int function, int code) {
        return new byte[]{(byte) (function | 0x80), (byte) code};
    }

    private static void requireRegisterRange(int start, int count) throws SlaveException {
        long end = (long) start + count;
        if (start < CraftSystemRegisterBank.FIRST_USER_REGISTER
                || end > CraftSystemRegisterBank.SIZE) throw new SlaveException(2);
    }

    private static void requireCoilRange(int start, int count) throws SlaveException {
        long firstRegister = start / 16L;
        long lastRegister = ((long) start + count - 1L) / 16L;
        if (firstRegister < CraftSystemRegisterBank.FIRST_USER_REGISTER
                || lastRegister >= CraftSystemRegisterBank.SIZE) throw new SlaveException(2);
    }

    private static void requirePduLength(byte[] request, int totalLength) throws SlaveException {
        if (request.length != totalLength) throw new SlaveException(3);
    }

    private static int u16(byte[] value, int offset) {
        return ((value[offset] & 255) << 8) | (value[offset + 1] & 255);
    }

    private static void putU16(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static final class SlaveException extends Exception {
        final int code;
        SlaveException(int code) { this.code = code; }
    }
}
