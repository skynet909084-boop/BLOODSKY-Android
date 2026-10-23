# Integrating the safe shared Modbus transport contract — 23 October 2026

GitHub PR #371 landed on stable main while the BLOODSKY all-in-one
integration branch continued separately. It added a basic Java
ModbusTransport contract, ModbusRtu framing utility, and changed
ModbusTcpClient. The integration branch already has more extensive
Modbus TCP operations FC05/FC15/FC22, plus its own tested RTU
transport and original-project register codecs.

Blindly copying main's shorter ModbusTcpClient would remove supported
Craft operations and break existing source-backed HMI/FBD behavior.

This change ports *only* the additive common three-operation
ModbusTransport interface and makes the existing richer
ModbusTcpClient implement it. FC01–04, FC06 and FC16 signatures stay
unchanged, and FC05/FC15/FC22 stay available on the concrete TCP
client. No production polling path is redirected, no new Android
permission is introduced and no hardware endpoint is enabled.

JUnit verifies compatibility through interface references, existing
PDU validation before network access, and preservation of advanced
TCP methods. The new ModbusRtu framing helper from main is *not*
blindly copied: it overlaps with integrated RTU code and must be
reviewed for response-unit/function/byte-count and timeout handling
before any future consolidation. RTU physical hardware acceptance
remains unresolved. Stable main is untouched by this PR.

## Project-widget RTU polling follow-up

The unified integration branch already has `ModbusRtuClient`, the USB serial screen,
Bluetooth SPP connections and an asynchronous widget poller. The poller previously
accepted USB/Bluetooth project bindings only for Craft register sources and FBD
inputs; ordinary HMI widgets were limited to TCP. The follow-up uses the existing
read path and value formatter, preserving multi-register reads and existing codecs.

An imported `usb://` or `bt://` binding is not polled in the editor and cannot start
an RTU connection on its own. On entering operator mode, BLOODSKY asks for approval
for the current session. USB also needs the user to select and connect the adapter
through the existing USB screen. Bluetooth still requires Android's separate
`BLUETOOTH_CONNECT` permission and connects only to the MAC in the project binding.
Declining the session prompt leaves TCP polling available and reports the RTU
binding as disabled. FBD writes are subject to the same session gate.

This is code-level support only. Exact-head CI and physical USB/Bluetooth acceptance
remain open; parser and transport tests do not establish hardware compatibility.
