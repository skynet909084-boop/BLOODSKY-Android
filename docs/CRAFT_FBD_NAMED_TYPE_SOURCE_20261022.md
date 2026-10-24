# Original Craft FBD named variable typeVar safety — 22 October 2026

Original mbCanvas factory IDs 133 VarNameIn and 134 VarNameOut
use typeVar=1 Boolean, 2 Integer, 3 Float. BLOODSKY imports
source names and native interface types into executable FBD macro
graphs. Prior type inference used JSONObject.optInt, permitting
oversized/fractional typeVar to wrap/truncate into a valid source
type and making unrelated original variable names look executable.

The factory mapper and graph translator now use a single
CraftExactInteger-based enum reader and reject anything outside
1..3. Missing typeVar retains the existing legacy INT default.
The translator preserves source name, native typeVar and unknown
properties, and never executes an invalid original graph instead
of guessing an alternate type. No new network subscriptions are
enabled or source .mcr files rewritten.

JVM tests cover both class IDs, all three native types, default
migration, oversized/fractional/null/array values and a complete
named-FLOAT output graph. Android hardware and original visual
matching remain separate acceptance gates.
