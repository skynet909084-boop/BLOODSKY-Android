#!/usr/bin/env python3
"""Fail unless each proposed JUnit class really ran without skips/failures."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from cloud_coding_agent import AgentError, MAX_RESPONSE


def verify(proposal, reports):
    expected = {"com.bloodsky.hmi." + Path(item["path"]).stem for item in proposal["files"]}
    if not expected:
        raise AgentError("No proposed test classes")
    cases = {name: [] for name in expected}
    for path in reports.glob("TEST-*.xml"):
        if path.stat().st_size > 20 * 1024 * 1024:
            raise AgentError("JUnit report exceeds budget")
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            if case.get("classname") in cases:
                cases[case.get("classname")].append(case)
    for name, executed in cases.items():
        if not executed:
            raise AgentError("Proposed class did not execute: " + name)
        if any(any(case.find(tag) is not None for tag in ("failure", "error", "skipped")) for case in executed):
            raise AgentError("Proposed class did not pass all active tests: " + name)
    return sum(len(value) for value in cases.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("proposal", type=Path)
    parser.add_argument("reports", type=Path)
    args = parser.parse_args()
    if args.proposal.stat().st_size > MAX_RESPONSE:
        raise AgentError("Proposal artifact exceeds budget")
    count = verify(json.loads(args.proposal.read_text(encoding="utf-8")), args.reports)
    print("Verified " + str(count) + " executed passing tests in every proposed class.")


if __name__ == "__main__":
    main()
