# Craft ID14 chart — lossless native settings, October 22, 2026

The original graph class `CustomComponents.o` stores `secondPeriod`,
`countPoint`, `countGraphChannel`, `typeGraph`,
`nameSeries1..4`, `colorLabelsXY` and `colorBackground` in
`jsTableComponent_other`. BLOODSKY mirrors them in editable runtime fields.

Previously, an imported chart with `craftOther` stored as a JSON *string*
was treated as if it had no source fields. Changing the period or channels
silently discarded other Chart settings and unrecognized vendor metadata.
Now all three settings overloads decode both JSON objects and string-encoded
original `other` fragments, preserve the source representation, and
reject malformed/unsupported source records before mutating any fields.
The original TCP/USB endpoint and opt-in state remain untouched.

JUnit covers full four-series configuration, source metadata nesting,
both edit overloads, bad source content and atomic rollback. This is a
source-backed project compatibility fix, not a claim of pixel-perfect UI
or of chart behavior on real PLC hardware.
