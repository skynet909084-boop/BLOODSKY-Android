"""Pure state helpers for the original Craft emulator probe."""


def remember_state_signature(seen, signature):
    """Return True once per signature and record it for subsequent deduplication."""
    if signature in seen:
        return False
    seen.add(signature)
    return True
