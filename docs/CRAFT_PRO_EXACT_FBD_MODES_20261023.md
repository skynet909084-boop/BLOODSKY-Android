# Exact native FBD operator modes in ordinary Craft .pro projects

The pinned KaScada 1.0.7 factory uses source enum values for:
FB_COMPARE 107/112 (compareAction 1..6), FB_ARITHMETIC 110/111
(compareAction 1..4), FB_TRIGGER 114 (type 1..3),
FB_TIMER 115 (type 1..4 and typeTime 1..3), Sensor_Orientation
120 (type 1..3), Sunrise_GPS 127 (type 1..2).

The ordinary .pro importer previously used JSONObject.optInt for
these native source choices. Invalid 64-bit or fractional JSON could
wrap or truncate to a supported value; out-of-range values silently
selected +, >, TON, T, first sensor axis or sunrise. Source .mcr
graphs received exact enum checks in PR #370, but ordinary .pro
projects still carried the unsafe conversion.

The source importer now applies exact signed int32 mode validation
with each native factory code range. Missing values retain their
previous defaults; unsupported explicit source modes reject import
instead of falsely executing a different mode. No original
metadata is rewritten on failure.

Regression tests cover direct .pro imports, all original mode families,
source defaults, overflow/fractions and valid .pro export/re-import
with vendor fields. Pixel-perfect UI, Android device operation and
full FBD original parity remain unverified.
