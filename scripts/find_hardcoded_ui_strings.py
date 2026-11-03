#!/usr/bin/env python3
"""Report hardcoded UI strings that already have a string resource.

This is a report, not a formatter. It only looks at common Compose text slots.
"""

from __future__ import annotations

import collections
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = "shared/src/commonMain/kotlin"
VALUES = [
    "shared/src/commonMain/composeResources/values/lampcord.xml",
    "shared/src/commonMain/composeResources/values/strings.xml",
]

PATTERNS = [
    re.compile(r'\bText\s*\(\s*"([^"\\]{2,})"'),
    re.compile(r'\bText\s*\(\s*text\s*=\s*"([^"\\]{2,})"'),
    re.compile(r'contentDescription\s*=\s*"([^"\\]{2,})"'),
    re.compile(r'placeholder\s*=\s*\{\s*Text\s*\(\s*"([^"\\]{2,})"'),
    re.compile(r'title\s*=\s*\{\s*Text\s*\(\s*"([^"\\]{2,})"'),
    re.compile(r'label\s*=\s*\{\s*Text\s*\(\s*"([^"\\]{2,})"'),
]


def load_resources() -> dict[str, list[str]]:
    by_value: dict[str, list[str]] = collections.defaultdict(list)
    for path in VALUES:
        if not os.path.isfile(path):
            continue
        for node in ET.parse(path).getroot().findall("string"):
            name = node.get("name")
            value = "".join(node.itertext()).strip()
            if name and value:
                by_value[value].append(name)
    return by_value


def main() -> int:
    resources = load_resources()
    found: dict[str, list[str]] = collections.defaultdict(list)
    for directory, _, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(directory, name)
            for line_number, line in enumerate(open(path, encoding="utf-8"), 1):
                for pattern in PATTERNS:
                    for match in pattern.finditer(line):
                        value = match.group(1)
                        if value.strip():
                            found[value].append(f"{path}:{line_number}")

    matches = [(value, resources[value], places) for value, places in found.items() if value in resources]
    matches.sort(key=lambda item: (-len(item[2]), item[0]))
    for value, keys, places in matches:
        print(f"{len(places):3}  {value!r} -> {', '.join(keys[:3])}  ({places[0]})")
    print(f"\n{len(matches)} unique hardcoded values already have a resource.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
