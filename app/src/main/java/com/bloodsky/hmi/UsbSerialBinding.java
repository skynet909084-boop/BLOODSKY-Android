package com.bloodsky.hmi;

import java.net.URI;

/** Strict, explicit project endpoint for the currently user-selected USB RTU session. */
public final class UsbSerialBinding {
    public final int unit, address, functionCode, quantity;

    private UsbSerialBinding(int unit, int address, int functionCode, int quantity) {
        this.unit = unit; this.address = address; this.functionCode = functionCode; this.quantity = quantity;
    }

    /**
     * Parses usb://<unit>/<register>, using standard 3xxxx/4xxxx prefixes when present.
     * Imported Craft USB metadata is never parsed here; callers must explicitly store this URI.
     */
    public static UsbSerialBinding parse(String value, int quantity) {
        if (value == null || !value.startsWith("usb://") || quantity < 1 || quantity > 8) return null;
        try {
            URI uri = URI.create(value.trim());
            String[] path = uri.getPath().split("/", -1);
            if (!"usb".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != -1
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path.length != 2 || !path[0].isEmpty()) return null;
            int unit = Integer.parseInt(uri.getHost());
            int enteredAddress = Integer.parseInt(path[1]);
            if (unit < 0 || unit > 247 || enteredAddress < 0 || enteredAddress > 65535) return null;
            int function = 3, address = enteredAddress;
            if (enteredAddress >= 30001 && enteredAddress <= 39999) {
                function = 4; address = enteredAddress - 30001;
            } else if (enteredAddress >= 40001 && enteredAddress <= 49999) {
                function = 3; address = enteredAddress - 40001;
            }
            if ((long) address + quantity > 65536L) return null;
            return new UsbSerialBinding(unit, address, function, quantity);
        } catch (Exception invalid) { return null; }
    }

    public String registerKey() {
        return "usb/" + unit + "/" + address + "/FC" + functionCode + "/" + quantity;
    }
}
