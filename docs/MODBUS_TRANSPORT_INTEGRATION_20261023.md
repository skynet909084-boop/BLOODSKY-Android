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
