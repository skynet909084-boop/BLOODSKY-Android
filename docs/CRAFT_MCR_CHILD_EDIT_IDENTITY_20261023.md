# Craft .mcr child identity safety — 23 October 2026

A KaScada 1.0.7 .mcr file stores native child records in fbInMacros,
with componentId and saveKey. BLOODSKY overlays a validated graph and
may write edited properties back into original child records. The
previous exporter used only saveKey to select the original target.
An edited widget that declared the wrong craftComponentId but
reused another child's saveKey could silently rewrite that other
original record. Duplicate source saveKeys or duplicate graph widgets
also created ambiguous write ordering.

The editor-to-source writer now requires both exact identifiers to
match, rejects duplicate edit targets and refuses ambiguous original
saveKeys. It continues to preserve every unknown original field and
does not mutate the input definition. Existing legitimate changes
to timer, trigger, float word ordering and typed variables remain
supported through unchanged matching IDs.

JVM regression covers legitimate two-child editing, mismatched
factory identity, duplicate source/graph aliases, int32 overflow
and unchanged input on rejection. This is source data integrity,
not verified pixel, hardware or full original runtime parity.
