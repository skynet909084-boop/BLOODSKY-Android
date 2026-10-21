"""Pure state helpers for the original Craft emulator probe."""

from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path


CHECKPOINT_VERSION = 1


def remember_state_signature(seen, signature):
    """Return True once per signature and record it for subsequent deduplication."""
    if signature in seen:
        return False
    seen.add(signature)
    return True


def write_checkpoint(path: Path, data: dict) -> None:
    """Durably replace a JSON checkpoint; temp and target share a filesystem."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = dict(data)
    payload["checkpoint_version"] = CHECKPOINT_VERSION
    encoded = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
    fd, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_name, path)
        try:
            directory_fd = os.open(path.parent, os.O_RDONLY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        except OSError:
            pass
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def load_checkpoint(path: Path, *, apk_sha256: str, profile: str = "full") -> dict:
    """Read a checkpoint only when its schema and original APK match."""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if data.get("checkpoint_version") != CHECKPOINT_VERSION:
        raise ValueError("unsupported or missing checkpoint version")
    if data.get("profile") != profile:
        raise ValueError(f"checkpoint profile mismatch: expected {profile}")
    if data.get("apk_sha256") != apk_sha256:
        raise ValueError("checkpoint APK SHA-256 does not match the installed source")
    required = ("visited_signatures", "state_rows", "transition_rows", "pending_paths")
    if any(key not in data for key in required):
        raise ValueError("checkpoint is missing traversal state")
    if not all(isinstance(data[key], list) for key in required):
        raise ValueError("checkpoint traversal fields must be arrays")
    if not all(isinstance(item, str) for item in data["visited_signatures"]):
        raise ValueError("checkpoint state signatures must be strings")
    if not all(isinstance(path_item, list) for path_item in data["pending_paths"]):
        raise ValueError("checkpoint frontier paths must be arrays")
    return data
