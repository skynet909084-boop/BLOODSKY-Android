#!/usr/bin/env python3
"""Independently enforce the test-only allowlist before applying an artifact."""
import argparse
import json
from pathlib import Path
from cloud_coding_agent import MAX_RESPONSE, AgentError, apply_proposal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("proposal", type=Path)
    args = parser.parse_args()
    if args.proposal.stat().st_size > MAX_RESPONSE:
        raise AgentError("Proposal artifact exceeds budget")
    files = apply_proposal(Path.cwd().resolve(), json.loads(args.proposal.read_text(encoding="utf-8")))
    print("Applied " + str(len(files)) + " new test files under the pilot allowlist.")


if __name__ == "__main__":
    main()
