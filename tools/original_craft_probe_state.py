"""Stable detection for original Craft's project-loading state."""
from __future__ import annotations

import re

_LOADING_RE = re.compile(r"(?:загр.{0,40}проек|подождит)", re.IGNORECASE | re.DOTALL)


def is_project_loading(root) -> bool:
    """Detect project loading across text, hint, and content-description variants."""
    for node in root.iter("node"):
        visible = " ".join(node.attrib.get(key, "") for key in ("text", "content-desc", "hint"))
        if _LOADING_RE.search(visible):
            return True
    return False
