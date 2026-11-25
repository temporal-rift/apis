#!/usr/bin/env python3
"""Print a module's version, which is the `info.version` of its spec.

The spec is the only place a module's version is written; the Maven build receives it as `-Drevision`.

    python3 scripts/spec_version.py simulation-api      ->  8.1.0

Stdlib only, so CI jobs that publish need no extra install. When a module holds several specs (such as
two OpenAPI path versions) they must all declare the same version.
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

SPEC_PATTERNS = (
    "src/main/resources/asyncapi/*.yml",
    "src/main/resources/openapi/*/*.yml",
    "src/main/resources/openapi/*.yml",
)

INFO_BLOCK = re.compile(r"^info:[ \t]*\r?\n((?:[ \t]+.*\r?\n|[ \t]*\r?\n)*)", re.MULTILINE)
VERSION_LINE = re.compile(r"^[ \t]+version:[ \t]*['\"]?([^'\"\s#]+)['\"]?[ \t]*(?:#.*)?$", re.MULTILINE)


def spec_files(module_dir):
    found = []
    for pattern in SPEC_PATTERNS:
        found.extend(Path(module_dir).glob(pattern))
    return sorted(set(found))


def declared_version(spec_text):
    """The `info.version` written in a spec's text, or None."""
    block = INFO_BLOCK.search(spec_text or "")
    if block is None:
        return None
    line = VERSION_LINE.search(block.group(1))
    return line.group(1) if line else None


def agreed_version(spec_texts):
    """The one version declared by every spec text in a {name: text} map.

    Raises ValueError when the map is empty, a spec declares none, or the specs disagree.
    """
    if not spec_texts:
        raise ValueError("the module has no spec")
    versions = {name: declared_version(text) for name, text in spec_texts.items()}
    missing = sorted(name for name, version in versions.items() if version is None)
    if missing:
        raise ValueError(f"{', '.join(missing)} declares no info.version")
    if len(set(versions.values())) > 1:
        raise ValueError(f"the specs declare different versions: {versions}")
    return next(iter(versions.values()))


def module_version(module_dir):
    """The module's version, read from the specs in its directory."""
    return agreed_version({spec.as_posix(): spec.read_text(encoding="utf-8") for spec in spec_files(module_dir)})


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.stderr.write("usage: spec_version.py <module>\n")
        sys.exit(2)
    try:
        print(module_version(REPO_ROOT / sys.argv[1]))
    except ValueError as error:
        sys.stderr.write(f"ERROR: {error}\n")
        sys.exit(1)
