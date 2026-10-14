# Kaskada Craft — confirmed data and FBD findings

This note records only details verified in the reconstructed sources; it is not a claim that BLOODSKY already implements them.

## SQLite project catalogue

Database helper: `ru.himik.nightscream.kascada.Bd.e`  
Database file: `Rovki.db`  
Database version: 11

Table `TableNameProject` is created with these columns:

| Column | Type/default | Meaning indicated by name |
|---|---|---|
| `_id` | integer primary key autoincrement | Internal row ID |
| `name` | text | Project name |
| `pass` | text default '' | Project password |
| `stationMode` | integer default 0 | Station mode flag |
| `countScreen` | integer default 1 | Screen count |
| `countDevice` | integer default 4 | Device count |
| `screenBackground` | text default '' | Screen background setting/data |
| `deviceNetwork` | text default '' | Device/network configuration |
| `createdTime` | text default '00:00:00' | Creation time |
| `createdDate` | text default '2000-01-01' | Creation date |
| `work` | integer default 0 | Work/active flag |
| `pdfDocument` | text default '' | Associated PDF |
| `showTimeOut` | integer default 0 | Timeout display/configuration |
| `systemRegisters` | text default '' | System register configuration |
| `modbusSlave` | text default '' | Modbus slave configuration |
| `settings` | text default '' | Additional project settings |

A separate `Settings` table contains `_id` and `key`. Upgrade logic adds columns introduced after older database versions. Before implementing persistence in BLOODSKY, inspect how `mbCanvas` serializes screens/widgets into these fields and how the ZIP exporter packages related data.

## FBD logic observations

The reconstructed FBD blocks are Android custom views and expose JSON metadata via `getJsonOther()`. This means a compatible port should separate:
1. persisted block identity and properties,
2. graph connections/input-output references,
3. execution/evaluation,
4. visual drawing and interaction.

### AND block

Source: `Logic/FB_AND.java`

- Display name: `AND`
- Inputs: `IN1`, `IN2`; output: `Q`
- Evaluation requires both connected inputs and non-null output values.
- Output is logical conjunction; output inversion is applied when reading the result.
- JSON metadata includes `numScreen`, `invertOut`, `saveKey`, `numOnIn`, and `keyOut`.

### Timer block

Source: `Logic/FB_TIMER.java`

- Default display name: `TON`
- Inputs: `IN` and `MS`; output: `Q`
- Exposes timer type and time-unit type settings.
- Converts the configured duration based on selected units and evaluates elapsed time using the system clock.
- JSON metadata includes `type`, `typeTime`, `numScreen`, `invertOut`, `saveKey`, `numOnIn`, and `keyOut`.

The exact timer mode enumeration and every block's JSON contract still need to be documented from the remaining source before using these as a complete compatibility specification.

## Porting guidance

Do not reproduce the Android classes literally in BLOODSKY. Implement a platform-independent FBD model and deterministic evaluator, then attach the editor/operator UI to that model. Use fixture projects exported by the original app to validate round-trip serialization, widget properties, connections and register bindings.
