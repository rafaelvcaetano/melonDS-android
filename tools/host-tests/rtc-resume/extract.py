#!/usr/bin/env python3
"""Compile exact RTC production bodies with real core objects, adapting instance state only."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[3]
source = root / "app/src/main/cpp/MelonInstance.cpp"
text = source.read_text()
parts = ['#include "Boundary.h"\nnamespace MelonDSAndroid {\n']
manifest = []
for name in ("setDateTime", "syncRTC", "onResumed"):
    start = text.index("void MelonInstance::" + name + "()")
    end = text.index("\n}\n", start) + 2
    body = text[start:end]
    parts.append(body + "\n")
    manifest.append({"function": name, "sha256": hashlib.sha256(body.encode()).hexdigest()})
parts.append("}\n")
Path(sys.argv[1]).write_text("".join(parts))
print(json.dumps({"source": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                  "functions": manifest}, indent=2))
