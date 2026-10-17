#!/usr/bin/env python3
"""Select a reviewed regression task and invoke the anonymous router once."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

SOURCE = "app/src/main/java/com/bloodsky/hmi/"
TEST = "app/src/test/java/com/bloodsky/hmi/"
PROFILES = {
    "properties": (
        "Properties",
        "Use FbdPropertyEdits.parse to test decimal-comma comparison values and rejected nonfinite values. Assert unrelated optional fields stay null. Use JUnit4.",
        [SOURCE + "FbdPropertyEdits.java"],
    ),
    "fbd-duration": (
        "FbdDuration",
        "Add two focused JUnit4 tests for FbdEditorModel.duration/durationEdit. Verify exact decimal-comma SEK/MIN conversion and preservation of unchanged rounded minute text for 2345ms. Reject a sub-millisecond duration. Use static helpers only; no Activity or Android APIs.",
        [SOURCE + "FbdEditorModel.java", TEST + "FbdEditorModelTest.java"],
    ),
    "fbd-rollback": (
        "FbdRollback",
        "Add two focused JUnit4 tests for FbdEditorTransaction.apply, following the existing test examples. Verify a false commit and a throwing mutation restore the prior widget and connection values, including unknown nested JSON metadata. Use public static MainActivity helpers, no Activity instance or Android API calls. Keep the tests short.",
        [SOURCE + "FbdEditorTransaction.java", TEST + "FbdEditorModelTest.java"],
    ),
}


def plan(profile, run_id, custom_task=""):
    if profile not in PROFILES:
        raise ValueError("Unknown reviewed task profile")
    if not isinstance(run_id, str) or not re.fullmatch(r"[0-9]{1,20}", run_id):
        raise ValueError("Expected a numeric workflow run ID")
    if not isinstance(custom_task, str) or len(custom_task) > 3600:
        raise ValueError("Custom task exceeds 3600 characters")
    prefix, default, context = PROFILES[profile]
    task = custom_task.strip() or default
    class_name = "CloudAgent" + prefix + run_id + "Test"
    task += "\nUse one new class named " + class_name + ". Keep the entire JSON response short."
    return {"profile": profile, "class_name": class_name, "task": task, "context": list(context)}


def execute(root, selected, output, runner=subprocess.run):
    # Fail locally before building/starting a router or making a provider request.
    from cloud_coding_agent import read_context
    read_context(root, selected["context"])
    output.mkdir(parents=True, exist_ok=True)
    (output / "task-plan.json").write_text(json.dumps(selected, ensure_ascii=False, indent=2), encoding="utf-8")
    with tempfile.TemporaryDirectory(prefix="bloodsky-task-") as directory:
        task_file = Path(directory) / "task.txt"
        task_file.write_text(selected["task"], encoding="utf-8")
        runner([sys.executable, str(root / "tools/run_cloud_router.py"), "--mode", "anonymous",
                "--task-file", str(task_file), "--output", str(output),
                "--context", *selected["context"]], cwd=root, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=PROFILES, default=os.environ.get("TASK_PROFILE", "fbd-duration"))
    parser.add_argument("--run-id", default=os.environ.get("GITHUB_RUN_ID", ""))
    parser.add_argument("--output", type=Path, default=Path("cloud-output"))
    args = parser.parse_args()
    try:
        selected = plan(args.profile, args.run_id, os.environ.get("TASK", ""))
        execute(Path(__file__).resolve().parents[1], selected, args.output.resolve())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        # Do not echo task data or provider output in an exception message.
        print("Cloud task failed: " + type(error).__name__, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
