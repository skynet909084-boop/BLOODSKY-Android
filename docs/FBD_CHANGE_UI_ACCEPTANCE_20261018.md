# Native CH_I / CH_F UI acceptance

Continuation from PR135 head 8f1860480ad1cbd01b8b7d4df397a6deb0f5de21. Add both source-backed variants using actual extended Android picker. Confirm exact saved type, preservation of existing SEL/COMPARE widgets, unchanged property Apply, and all widget JSON after force-stop/cold restart. Save before/after screenshots and require empty crash log.

135 local Python tests passed after pinned-resource preparation. Local Gradle JUnit/APK not run: Gradle absent. API30 execution and independent APK/JUnit are pending; this document is not a success claim. Original preview buttons stay disabled; this test does not establish numerical runtime or original pixel parity. Existing graph/JUnit runtime tests are in PR135.

Separate integration blocker tracked in PR136: full UI image fixture was absent from DocumentsUI (artifact 12121514401); media scanning plus bounded MediaStore verification is awaiting full emulator acceptance. Main unchanged.
