# BLOODSKY FBD typed arithmetic batch — 17 Oct 2026

Stacked on draft PR #101 (runtime and wire geometry), itself stacked on #76. No `main` changes.

## Engine improvements

The existing Android runtime implements many BLOODSKY numeric operations but some lacked source/target terminal contracts. This batch adds **BLOODSKY-specific typed pins** and insertion-order-independent evaluation for:

| Internal block | New inputs | Output | Behavior |
| --- | --- | --- | --- |
| `FBD Сложение` | NUMBER IN1, IN2 | NUMBER OUT | A+B |
| `FBD Вычитание` | NUMBER IN1, IN2 | NUMBER OUT | A−B; no insertion-order dependence |
| `FBD Умножение` | NUMBER IN1, IN2 | NUMBER OUT | A×B |
| `FBD Деление` | NUMBER IN1, IN2 | NUMBER OUT | A/B; zero denominator produces current BLOODSKY fallback 0 |
| `FBD MIN_MAX` | NUMBER IN1, IN2 | NUMBER OUT | existing MIN behavior, now pinned |
| `FBD Сравнение` | NUMBER A, B | BOOL Q | selected comparison operator; parameter defaults retained |
| `FBD MOVE` | NUMBER IN | NUMBER OUT | value passthrough |
| `FBD SEL` | BOOL G, NUMBER IN0, IN1 | NUMBER OUT | G=false selects IN0, true selects IN1 |

The original unpinned project records continue to execute their prior positional math. No old field or unknown metadata is rewritten. Numeric outputs are forbidden from connecting to Boolean contacts; Boolean selectors cannot consume numeric signals.

These are **BLOODSKY typed graph extensions**, not proof of original KaScada Craft 1.0.7 pin names, error behavior, integer rounding or signal units. In particular the original CTU and timer block semantics remain under source audit; do not enable their original disabled catalogue entries on this evidence alone.

## Acceptance

- New `FbdTypedArithmeticTest` executes numeric and BOOL typed graphs; proves subtraction/division independent of wire order, numeric source compatibility, comparison parameter versus wired operand, SEL selection, legacy unpinned fallback.
- Exact head Python, Android JUnit/assembleDebug and API30 smoke must pass. No paid tools, signed release or stable-main merge.
- After a clean pass, merge into PR #101 feature branch; after its independent green gates, combine into PR #76, then run all three integration checks again.
- Continue from handoff `docs/FBD_EXECUTION_HANDOFF_20261017.md` on PR #101 to avoid redoing previous work.
