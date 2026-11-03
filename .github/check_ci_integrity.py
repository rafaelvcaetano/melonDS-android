#!/usr/bin/env python3
"""Reject mutable third-party Actions refs and unverified Gradle distributions."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
WRAPPER_PROPERTIES = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
IMMUTABLE_COMMIT = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def main() -> int:
    errors: list[str] = []

    for workflow in sorted((*WORKFLOWS.glob("*.yml"), *WORKFLOWS.glob("*.yaml"))):
        for line_number, line in enumerate(workflow.read_text(encoding="utf-8").splitlines(), 1):
            match = USES.match(line)
            if not match:
                continue
            action = match.group(1)
            if action.startswith("./"):
                continue
            _, separator, ref = action.rpartition("@")
            if not separator or not IMMUTABLE_COMMIT.fullmatch(ref):
                errors.append(
                    f"{workflow.relative_to(ROOT)}:{line_number}: "
                    f"third-party action must use a full commit SHA: {action}"
                )

    properties: dict[str, str] = {}
    for line in WRAPPER_PROPERTIES.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    checksum = properties.get("distributionSha256Sum", "")
    if not SHA256.fullmatch(checksum):
        errors.append(
            f"{WRAPPER_PROPERTIES.relative_to(ROOT)}: distributionSha256Sum must be a 64-character SHA-256"
        )

    if errors:
        print("CI integrity check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("CI integrity check passed: immutable Action commits and Gradle checksum are present")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
