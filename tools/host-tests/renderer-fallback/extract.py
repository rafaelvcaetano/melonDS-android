#!/usr/bin/env python3
"""Compile byte-exact production function bodies, never a copied test implementation."""
import hashlib
import json
from pathlib import Path
import sys


def function(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    state = "code"
    index = opening
    while index < len(source):
        char = source[index]
        next_char = source[index:index + 2]
        if state == "line":
            if char == "\n":
                state = "code"
        elif state == "block":
            if next_char == "*/":
                state = "code"
                index += 1
        elif state in ('"', "'"):
            if char == "\\":
                index += 1
            elif char == state:
                state = "code"
        elif next_char == "//":
            state = "line"
            index += 1
        elif next_char == "/*":
            state = "block"
            index += 1
        elif char in ('"', "'"):
            state = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
        index += 1
    raise ValueError("Unbalanced function: " + signature)


repo = Path(__file__).resolve().parents[3]
output = Path(sys.argv[1])
frontend = Path(sys.argv[2]) if len(sys.argv) > 2 else repo / "app/src/main/cpp/MelonInstance.cpp"
core = repo / "melonDS-android-lib/src/GPU.cpp"
units = [(frontend, "void MelonInstance::updateRenderer()", "MelonDSAndroid"),
         (core, "void GPU::SetRenderer3D(", "melonDS")]
parts = ['#include "Boundary.h"\nusing namespace melonDS;\nusing namespace melonDS::Platform;\n']
manifest = []
for path, signature, namespace in units:
    source = path.read_text()
    body = function(source, signature)
    parts.append("namespace " + namespace + " {\n" + body + "\n}\n")
    manifest.append({"path": str(path), "signature": signature,
                     "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                     "function_sha256": hashlib.sha256(body.encode()).hexdigest()})
output.write_text("".join(parts))
print(json.dumps(manifest, indent=2))
