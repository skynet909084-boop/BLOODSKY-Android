package com.bloodsky.hmi;

import java.util.Calendar;

/**
 * Craft 1.0.7 service-register bank (addresses 0..9).
 *
 * <p>FB_ARRAY writes use the raw methods and deliberately bypass the VarOut
 * command handlers. The register bank is shared across the active project,
 * matching the single runtime register manager in Craft.</p>
 */
public final class CraftServiceRegisters {
    public interface Actions {
        void selectScreen(int oneBasedScreen);
        void applyBackground(int paletteIndex, int color);
        void enterPassword(int candidate);
        void saveProject();
        void refreshComponents();
        void openProjectUrl();
        void exitApplication();
        void logCascade();
    }

    private static final int[] PALETTE = {
            0, 0xFF000000, 0xFFFF0000, 0xFF800000, 0xFFFFFF00,
            0xFF804000, 0xFF00FF00, 0xFF008000, 0xFF00FFFF,
            0xFF008080, 0xFF0000FF, 0xFF000080, 0xFFFF00FF,
            0xFF800080, 0xFFB0B0B0, 0xFF808080, 0xFFFFFFFF
    };
    private static final CraftServiceRegisters GLOBAL = new CraftServiceRegisters();

    private final int[] words = new int[10];
    private Actions actions;
    private int screenCount = 1;
    private long lastSaveAt;
    private long lastRefreshAt;
    private boolean saveArmed;

    public static CraftServiceRegisters global() { return GLOBAL; }

    public synchronized void setActions(Actions actions) { this.actions = actions; }

    public synchronized void setScreenCount(int count) {
        screenCount = Math.max(0, count);
    }

    /** Raw MX/ARRAY access: no VarOut validation or handler side effects. */
    public synchronized int readRawWord(int address) {
        requireAddress(address);
        if (address == 2 || address == 3 || address == 9) updateClock(Calendar.getInstance());
        return words[address];
    }

    /** Raw FB_ARRAY store, equivalent to Craft q.d.b.T(address, value). */
    public synchronized void writeArrayWord(int address, int value) {
        requireAddress(address);
        words[address] = value;
    }

    /** Raw register-bit mutation, equivalent to Craft q.d.b.K(). */
    public synchronized void writeArrayBit(int address, int bit, boolean value) {
        requireAddress(address);
        requireBit(bit);
        words[address] = setBit(words[address], bit, value);
    }

    /** Craft q.d.b.U / a0: special whole-word VarOutInt handling for 0..9. */
    public synchronized boolean writeVarOutWord(int address, int value) {
        if (!isAddress(address)) return false;
        switch (address) {
            case 4:
                if (value < 1 || value > screenCount) return false;
                if (words[4] == value) return true;
                words[4] = value;
                if (actions != null) actions.selectScreen(value);
                return true;
            case 5:
                if (value > 255) return false;
                words[5] = value;
                return true;
            case 7:
                if (value < 0 || value > 16) return false;
                if (words[7] == value) return true;
                words[7] = value;
                dispatchPalette(value);
                return true;
            case 8:
                if (words[8] != value && actions != null) actions.enterPassword(value);
                return true;
            case 9:
                words[9] = value;
                if (bit(words[9], 15) && actions != null) actions.exitApplication();
                return true;
            default:
                // Craft's word handler has no write case for status, ID, date,
                // or time (addresses 0..3 and 6).
                return false;
        }
    }

    /** Craft q.d.b.L / M: special single-bit VarOut handling for 0..9. */
    public synchronized boolean writeVarOutBit(int address, int bit, boolean value, long nowMillis) {
        if (!isAddress(address)) return false;
        switch (address) {
            case 4:
                if (!value || bit < 1 || bit > screenCount) return false;
                if (words[4] == bit) return true;
                words[4] = bit;
                if (actions != null) actions.selectScreen(bit);
                return true;
            case 5:
                if (bit > 7) return false;
                words[5] = setBit(words[5], bit, value);
                return true;
            case 7:
                if (bit < 0 || bit > 16 || !value || words[7] == (1 << bit)) return false;
                words[7] = 1 << bit;
                dispatchPalette(words[7]);
                return true;
            case 8:
                words[8] = setBit(words[8], bit, value);
                if (actions != null) actions.enterPassword(words[8]);
                return true;
            case 9:
                words[9] = setBit(words[9], bit, value);
                dispatchRegister9BitCommands(nowMillis);
                return true;
            default:
                return false;
        }
    }

    /** Source-compatible overload using the device's current clock. */
    public synchronized boolean writeVarOutBit(int address, int bit, boolean value) {
        return writeVarOutBit(address, bit, value, System.currentTimeMillis());
    }

    /** Dynamic values supplied by Android/device state, like Craft setters X/Y/Q/S. */
    public synchronized void setCharging(boolean charging) {
        words[0] = setBit(words[0], 15, !charging);
    }

    public synchronized void setChannelFault(int channel, boolean fault) {
        if (channel < 0 || channel > 7) return;
        words[0] = setBit(words[0], channel, fault);
    }

    public synchronized void setDeviceId(int lower16Bits) {
        words[1] = lower16Bits;
    }

    public synchronized void setCurrentScreen(int oneBasedScreen) {
        words[4] = oneBasedScreen;
    }

    public synchronized void setBrightness(int brightness) {
        words[5] = brightness;
    }

    public synchronized void setBatteryLevel(int level) {
        words[6] = level;
    }

    public synchronized void updateClock(Calendar time) {
        if (time == null) return;
        words[2] = time.get(Calendar.DAY_OF_MONTH) * 100 + time.get(Calendar.MONTH) + 1;
        words[3] = time.get(Calendar.HOUR_OF_DAY) * 100 + time.get(Calendar.MINUTE);
        int calendarDay = time.get(Calendar.DAY_OF_WEEK);
        int craftDay = calendarDay == Calendar.SUNDAY ? 7 : calendarDay - 1;
        words[9] = (words[9] & ~7) | (craftDay & 7);
        words[9] = setBit(words[9], 8, time.get(Calendar.SECOND) % 2 == 1);
    }

    public synchronized int readRawBit(int address, int bit) {
        requireAddress(address);
        requireBit(bit);
        if (address == 2 || address == 3 || address == 9) updateClock(Calendar.getInstance());
        return bit(words[address], bit) ? 1 : 0;
    }

    public synchronized void clear() {
        java.util.Arrays.fill(words, 0);
        actions = null;
        screenCount = 1;
        lastSaveAt = 0;
        lastRefreshAt = 0;
        saveArmed = false;
    }

    private void dispatchRegister9BitCommands(long nowMillis) {
        if (bit(words[9], 15) && actions != null) actions.exitApplication();

        if (!bit(words[9], 14)) {
            saveArmed = false;
        } else if (!saveArmed) {
            saveArmed = true;
            if (nowMillis - lastSaveAt >= 10000) {
                lastSaveAt = nowMillis;
                if (actions != null) actions.saveProject();
            }
        }

        if (bit(words[9], 13) && nowMillis - lastRefreshAt >= 1000) {
            lastRefreshAt = nowMillis;
            if (actions != null) actions.refreshComponents();
        }
        if (bit(words[9], 10) && actions != null) actions.openProjectUrl();
        if (bit(words[9], 11) && actions != null) actions.logCascade();
    }

    private void dispatchPalette(int index) {
        if (actions != null && index >= 0 && index < PALETTE.length)
            actions.applyBackground(index, PALETTE[index]);
    }

    private static boolean isAddress(int address) { return address >= 0 && address < 10; }
    private static void requireAddress(int address) {
        if (!isAddress(address)) throw new IllegalArgumentException("Service register address must be 0..9");
    }
    private static void requireBit(int bit) {
        if (bit < 0 || bit > 31) throw new IllegalArgumentException("Bit must be 0..31");
    }
    private static int setBit(int word, int bit, boolean value) {
        int mask = 1 << bit;
        return value ? word | mask : word & ~mask;
    }
    private static boolean bit(int word, int bit) { return ((word >>> bit) & 1) != 0; }
}
