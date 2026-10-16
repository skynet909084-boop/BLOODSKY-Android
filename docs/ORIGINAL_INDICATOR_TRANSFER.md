# Original two-state indicator transfer

Source: `CustomComponents/q.java` methods `c`, `d`, `e`, `getJsonOther` and `onDraw`, checked against `smali_classes2/.../CustomComponents/q.smali` for padding/FIT_XY setup. Original `view_base_height` is 40dp. Hashes of pinned Java/resources are in `original-indicator-provenance.json`.

Production indicators now draw original lamp_green/off/red/blue/yellow bitmaps with the original FIT_XY scaling and ten raw pixels of padding. New indicators start at 40dp square. Existing geometry is retained. Generic card/background, textual value and replacement colored dot are removed from this renderer; editor selection remains available. Missing custom media/unknown builtins use the original warning icon; polling errors also retain warning feedback. Work-mode visibility/error behavior and selection overlays still need full source/device comparison.

Original `isImageUserOn`, `pathOrNameOn`, `isImageUserOff`, `pathOrNameOff` are resolved from a JSON `other` object/string or flattened BLOODSKY metadata. Defaults match original green/on and off/off. Configured custom images use the common media resolver, and ZIP export embeds and rewrites both states while preserving `other` representation and unknown fields. Existing original database/connectionInfo decoding is not implemented by this change. The original image-selection property dialog and original component-picker thumbnails remain open work.

Status: wired-unverified. Byte/source and JVM tests verify known state names, metadata/snapshot persistence and portable custom images; they do not verify touch behavior, bitmap appearance or pixel parity on Android. No signed/public release claim.
