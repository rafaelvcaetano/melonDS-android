#!/usr/bin/env python3
"""Reject mutable CI inputs and incomplete Gradle dependency verification."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
ANDROID_CORE_WORKFLOWS = (ROOT / "melonDS-android-lib" / ".github" / "workflows" / "core-checks.yml",)
WRAPPER_PROPERTIES = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
VERIFICATION_METADATA = ROOT / "gradle" / "verification-metadata.xml"
IMMUTABLE_COMMIT = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def main() -> int:
    errors: list[str] = []

    workflows = (*WORKFLOWS.glob("*.yml"), *WORKFLOWS.glob("*.yaml"), *ANDROID_CORE_WORKFLOWS)
    for workflow in sorted(workflows):
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

    try:
        verification_root = ET.parse(VERIFICATION_METADATA).getroot()
    except (OSError, ET.ParseError) as error:
        errors.append(
            f"{VERIFICATION_METADATA.relative_to(ROOT)}: missing or invalid dependency verification metadata: {error}"
        )
    else:
        checksums = verification_root.findall(".//{*}sha256")
        if not checksums:
            errors.append(
                f"{VERIFICATION_METADATA.relative_to(ROOT)}: no dependency SHA-256 checksums are present"
            )
        for checksum_node in checksums:
            value = checksum_node.get("value", "")
            if not SHA256.fullmatch(value):
                errors.append(
                    f"{VERIFICATION_METADATA.relative_to(ROOT)}: invalid dependency SHA-256: {value!r}"
                )

    if errors:
        print("CI integrity check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "CI integrity check passed: immutable Action commits, Gradle distribution checksum, "
        "and dependency checksums are present"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
