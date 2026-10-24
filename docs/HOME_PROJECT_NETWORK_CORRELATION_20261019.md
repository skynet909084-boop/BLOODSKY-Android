# Correlation of the saved `home2` and `дом 2025` projects

Date: 2026-10-19. Analysis uses the supplied Craft ZIP exports and Craft 1.0.7 decompiled source. Endpoint literals and cloud identifiers are intentionally omitted here; the original archives remain unchanged.

## Findings

| Property | `home2` (phone export) | `дом 2025` |
|---|---:|---:|
| Craft format version | 107 | 106 |
| `countDevice` | 1 | 7 |
| Configured network rows | 16 | 16 |
| Configured named devices | slot 0 public endpoint; slot 1 private LAN endpoint | slots 0–6; six rows have LAN endpoints and one has a blank endpoint |
| `TableConnections` entries | 3, including a Cloud entry and two rows sharing `connectionsId=2` | 2, both using `connectionsId=2` |
| Component records | 65 | 346 |
| Components bound to device slot 0 | 45 | 13 |
| Components with `posDeviceNetworkInList=100` | 1 | 62 |
| Components with no `posDeviceNetworkInList` | 19 | 47 |

For lineage, the earlier `home.zip` export (2024-05-10) is version 106 and has 24 components, all bound to slot 0, plus a single Cloud connection. It is a useful predecessor comparison for the later phone export.

## Relationship between `home.zip` and `home2.zip`

The saved `home2` is very likely an expanded/reconfigured continuation of `home`: 23 of the older export's 24 component records have the same compact identity tuple (component ID, text label, register address, request type, and screen number) in `home2`. All 24 earlier components were bound to slot 0; the same bindings in `home2` still point to slot 0. That slot now resolves to the public IP endpoint, whereas the older project's slot 0 resolved to a Cloud endpoint. This is strong evidence that the project retained its widget/register configuration while changing the transport endpoint assigned to the shared device slot. One old component's tuple differs, so this is not a byte-for-byte copy.

The `home2` export still contains a Cloud row in `TableConnections`, but no active `deviceNetwork` row points to its old Cloud server ID: slot 0 uses `idServer=2`, and the new private LAN row also uses `idServer=2`. The retained Cloud record therefore appears unused/stale in this export. The Cloud row alone does not mean that the current widgets are still using Cloud.

The phone export's slot 0 points to the public endpoint on port 7070 and slot 1 points to a private LAN endpoint on port 4001. Its 45 ordinary device-bound components reference slot 0; the slot 1 endpoint is configured but no ordinary component in this export references it. This means the recently saved `home2` has its displayed objects bound to the public endpoint, while the local endpoint is currently only a stored device row. This is a file-level association, not proof that either endpoint answers.

`дом 2025` is a separate, older project configuration. Its slots 0–6 hold Modbus unit addresses 16, 18, 19, 20, 34, 30, and 31. Several use one private endpoint and one uses a second private endpoint; one row has no endpoint. The ordinary component bindings in this archive mostly use slot 0 (13 records); they do not prove that every configured device is active from the visible screens. The project does not inherit `home2`'s public endpoint.

Both archives serialize endpoint details twice: `deviceNetwork` rows hold `num`, unit `address`, `idServer`, `server`, and `rtu`; `TableConnections` holds connection records with `connectionsId`, display `name`, and a JSON `server` object. In these samples `idServer=2` and `connectionsId=2` repeat for multiple distinct endpoints. Therefore the numeric server ID alone is not a unique endpoint key; preserve the complete records and associate a widget through its `posDeviceNetworkInList` slot.

There are 16 device rows in each exported `deviceNetwork`, but `countDevice` is 1 for `home2` and 7 for `дом 2025`. Source `mbCanvas` loads rows by their explicit `num`, and other source code uses `countDevice` for the device selector. Treat `countDevice` as a separate project setting, not a safe substitute for array length or the set of referenced slots. BLOODSKY should preserve the stored value and each row, then resolve component bindings by slot index.

The sentinel `posDeviceNetworkInList=100` is not a device slot. Craft source routes that value to its ninth/global register bucket in its HTTP data handler (`c0/a.java`); it appears on 1 `home2` component and 62 `дом 2025` components. BLOODSKY import and runtime must not interpret those as device 100 or as a broken network endpoint.

## What the `rtu` flag does and does not establish

Both project exports store `rtu=true` on the relevant TCP endpoint rows. The inspected Craft 1.0.7 `WifiConnector` nevertheless selects MBAP/TCP mode 2; its RTU flag is not a working way to switch that connector into RTU framing. The separate RTU transports in Craft are Bluetooth SPP and USB serial. So the `home2` public endpoint's saved `rtu=true` does not prove RTU-over-TCP behavior. The apparent `дом 2025` endpoints likewise need live validation and source-path confirmation before classifying their protocol from the project flag alone.

## Implications for BLOODSKY

1. Keep `deviceNetwork` rows and `TableConnections` records losslessly, including duplicate numeric IDs and unknown fields.
2. Resolve each component's `posDeviceNetworkInList` against the row whose `num` matches it; do not infer endpoint from `idServer` alone.
3. Handle slot 100 as the original global/system register bucket, separate from network device slots 0–15.
4. Show configured-but-unreferenced endpoints (such as `home2` slot 1) distinctly from endpoints used by components.
5. Keep transport runtime status unverified until a controlled Modbus query succeeds. The project files do not encode a successful connection, live reachability, VPN/tunnel setup, firewall forwarding, or current register values.

Current `CraftProjectImporter` behavior is conservative: it preserves each source `jsTableComponent_connectionInfo` as `craftConnectionInfo` but leaves BLOODSKY's active `connectionInfo` blank. That avoids silently sending writes to an imported public endpoint. The next importer/runtime step is an explicit slot resolver and a user-visible endpoint remap, backed by a read-only query test before enabling writes.

## Source and data basis

- `home2.zip` and `дом 2025.zip`, `TableNameProject.deviceNetwork`, `TableConnections`, and `TableComponent[].jsTableComponent_connectionInfo`.
- Original `mbCanvas.java`: loads `deviceNetwork` records by their `num` (the code also retains compatibility branches for 8-row and 16-row arrays).
- Original `Bd/h.java`: serializes the project's configured device list and `countDevice`.
- Original `c0/a.java`: maps `posDeviceNetworkInList == 100` to global data bucket index 8.
- Original `Connections/Wifi/WifiConnector.java` plus Smali audit in `TRANSPORT_CONNECTIVITY_AUDIT_20261019.md`: Wi-Fi uses MBAP/TCP in Craft 1.0.7.

## Open validation

- Confirm the meaning of the stale-looking `countDevice` values in the live UI/device selector; file rows and component bindings show it cannot be used as the only source of truth.
- On the phone, run a read-only connectivity test for the public endpoint and separately for the local endpoint from the intended network. Do not infer reachability from the white-IP string in the project.
- Determine whether the `дом 2025` unit-address set represents the same physical installation as `home2`; the archives show similar project context and shared LAN endpoints, but no identity key or explicit parent/child link.
- Compare current register addresses and values after a successful query; the ZIPs contain component bindings, not the remote PLC's live state.
