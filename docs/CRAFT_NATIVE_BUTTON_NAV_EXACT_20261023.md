# KaScada native button ID6 and screen navigation exactness — 23 October 2026

Original Craft CustomComponents.e modes 0/1/2 are respectively
momentary, screen navigation and constant action. Mode 1 contains
the one-based `changeScreen` destination.

The existing importer used `Number.intValue()`, and .pro exporter
used `JSONObject.optInt` for mode and screen identity. JSON numbers
above signed int32 (e.g. 4294967297) could wrap into a valid mode
or screen 1. This risked enabling an unsupported button action or
rewriting its target to the wrong screen during .pro re-export.

Both sides now use CraftExactInteger for native ID6 mode, target
and remap. Valid absent mode retains source default 0. Unsupported
or malformed mode and lossy target input abort rather than silently
activate a different action; a *valid exact* destination outside
the available screen count remains visible but unavailable as before.

JVM tests exercise screen navigation across two screens including
.pro export/reimport, source and edited metadata precision attacks,
and preserved unknown vendor fields. No network action is enabled,
no pin or resource is changed. Original on-device navigation and
visual acceptance remain separate release gates.
