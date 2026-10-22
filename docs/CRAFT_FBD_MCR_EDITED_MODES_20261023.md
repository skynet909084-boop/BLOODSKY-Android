# Safe editor-to-source modes for original Craft .mcr macros

KaScada Craft 1.0.7 stores its nested FBD child properties in
fbInMacros. Editing an executable BLOODSKY sidecar can update
source child type/typeTime, compareAction, bit and typeVar.
Previously explicit invalid mode strings could be silently
ignored: export appeared successful but the original setting
stayed unchanged. JSONObject.optInt on edited orientation and
variable bits could also truncate a fraction or wrap a large
value into an unrelated native signal.

The writer now rejects unknown explicit modes with JSONException
instead of silently accepting them. Edited sensor axis and var bit
are parsed as exact signed int32 source values; BOOL Var bit must
be within 0..15. For INT/FLOAT Var bit metadata, no unverified
source-specific bound is invented. A missing editor setting still
preserves the original native child field, including unknown
vendor keys. Export never modifies the input definition on
validation failure.

JVM cases cover valid edits, source/vendor preservation, edited
overflow/fractions/arrays/unsupported enum names, and unchanged
original records without requested changes.

Full physical Modbus/FBD operation and visual parity remain
separate acceptance gates.
