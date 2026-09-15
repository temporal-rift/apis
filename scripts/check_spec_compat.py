#!/usr/bin/env python3
"""CI compatibility gate for apis spec module version bumps.

For every spec module changed in a pull request, classifies the spec diff as
NONE / PATCH / MINOR / MAJOR and checks the module's pom.xml version bump
against the base revision:

- breaking shape change (removed/renamed field, event, message, path or
  operation; incompatible type/format/requiredness/enum change; tightened
  constraint) requires a MAJOR bump,
- compatible addition (new optional field, endpoint, event) requires at
  least a MINOR bump,
- description-only or loosening edits pass with any version (including
  unchanged); modules with no spec change pass unless downgraded.

Usage (local, same verdict as CI)::

    pip install pyyaml
    python scripts/check_spec_compat.py --base origin/main

Exit status is 0 when every module satisfies its required bump, 1 otherwise.
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("ERROR: PyYAML is required (pip install pyyaml)\n")
    sys.exit(2)

REPO_ROOT = Path(__file__).resolve().parent.parent

NONE = 0
PATCH = 1
MINOR = 2
MAJOR = 3

SEVERITY_NAMES = {NONE: "none", PATCH: "patch", MINOR: "minor", MAJOR: "major"}
BUMP_NAMES = {NONE: "none", PATCH: "patch", MINOR: "minor", MAJOR: "major"}

IGNORED_KEYS = frozenset(
    {
        "description",
        "summary",
        "title",
        "example",
        "examples",
        "externalDocs",
        "deprecated",
        "servers",
        "tags",
        "info",
        "x-",
    }
)

VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def parse_version(text):
    """Return an (major, minor, patch) tuple, or None when unparsable."""
    if text is None:
        return None
    match = VERSION_RE.match(str(text).strip())
    if not match:
        return None
    return (int(match.group(1)), int(match.group(2)), int(match.group(3)))


def bump_type(base, head):
    """Classify the version increment from base to head tuple.

    Returns one of NONE/PATCH/MINOR/MAJOR, or None for a downgrade or an
    unparsable version pair.
    """
    if base is None or head is None:
        return None
    if head == base:
        return NONE
    if head < base:
        return None
    if head[0] != base[0]:
        return MAJOR
    if head[1] != base[1]:
        return MINOR
    return PATCH


def pom_version(pom_text):
    """Extract the module version from a pom.xml document.

    Uses the first top-level <version> after <artifactId>, falling back to
    the first <version> found.
    """
    versions = re.findall(r"<version>\s*([^<>\s]+)\s*</version>", pom_text or "")
    return versions[0].strip() if versions else None


def run_git(*args):
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result


def resolve_base_ref(base):
    """Resolve the user-supplied base to a commit sha (merge-base when possible)."""
    probe = run_git("merge-base", base, "HEAD")
    if probe.returncode == 0 and probe.stdout.strip():
        return probe.stdout.strip()
    show = run_git("rev-parse", "--verify", base)
    if show.returncode == 0 and show.stdout.strip():
        return show.stdout.strip()
    return base


def file_at_revision(path, revision):
    """Return file text at a git revision, or None when absent."""
    result = run_git("show", f"{revision}:{path}")
    if result.returncode != 0:
        return None
    return result.stdout


def changed_files(base):
    result = run_git("diff", "--name-only", f"{base}...HEAD")
    if result.returncode != 0:
        fallback = run_git("diff", "--name-only", f"{base}", "HEAD")
        if fallback.returncode != 0:
            committed = []
        else:
            committed = [line for line in fallback.stdout.splitlines() if line.strip()]
    else:
        committed = [line for line in result.stdout.splitlines() if line.strip()]
    # Include uncommitted working-tree edits and untracked files so the gate
    # reports the same verdict locally before a commit as CI does after it.
    uncommitted = run_git("diff", "--name-only")
    untracked = run_git("ls-files", "--others", "--exclude-standard")
    local = []
    if uncommitted.returncode == 0:
        local.extend(line for line in uncommitted.stdout.splitlines() if line.strip())
    if untracked.returncode == 0:
        local.extend(line for line in untracked.stdout.splitlines() if line.strip())
    seen = set()
    ordered = []
    for path in committed + local:
        if path not in seen:
            seen.add(path)
            ordered.append(path)
    return ordered


def discover_modules():
    """Return sorted top-level module dirs containing a pom.xml."""
    modules = []
    for pom in REPO_ROOT.glob("*/pom.xml"):
        if pom.parent.name.startswith("."):
            continue
        modules.append(pom.parent.name)
    return sorted(modules)


def spec_files_for_module(module):
    """Return repo-relative spec paths owned by a module."""
    owned = []
    for pattern in (
        "src/main/resources/asyncapi/asyncapi.yml",
        "src/main/resources/asyncapi/*.yml",
        "src/main/resources/openapi/*/*.yml",
        "src/main/resources/openapi/*.yml",
    ):
        for path in (REPO_ROOT / module).glob(pattern):
            owned.append(path.relative_to(REPO_ROOT).as_posix())
    return sorted(set(owned))


def module_bundles_shared(module):
    pom = REPO_ROOT / module / "pom.xml"
    try:
        return "shared-schemas" in pom.read_text(encoding="utf-8")
    except OSError:
        return False


def shared_schema_files():
    return sorted(
        p.relative_to(REPO_ROOT).as_posix()
        for p in (REPO_ROOT / "shared-schemas").glob("*.yaml")
    )


class RevisionLoader:
    """Loads YAML documents from either the working tree or a git revision."""

    def __init__(self, revision=None):
        # revision None means the working tree (HEAD side of the diff).
        self.revision = revision
        self._cache = {}

    def load(self, repo_path):
        if repo_path in self._cache:
            return self._cache[repo_path]
        if self.revision is None:
            full = REPO_ROOT / repo_path
            if not full.is_file():
                self._cache[repo_path] = None
                return None
            text = full.read_text(encoding="utf-8")
        else:
            text = file_at_revision(repo_path, self.revision)
            if text is None:
                self._cache[repo_path] = None
                return None
        try:
            doc = yaml.safe_load(text)
        except yaml.YAMLError:
            doc = None
        self._cache[repo_path] = doc
        return doc


def resolve_refs(node, root_doc, loader, spec_path, seen=None):
    """Return node with local/file $refs inlined (cycle-safe)."""
    if seen is None:
        seen = set()
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str):
            if ref in seen:
                return {}
            seen = seen | {ref}
            target = follow_ref(ref, root_doc, loader, spec_path)
            resolved = resolve_refs(target, root_doc, loader, spec_path, seen)
            if not isinstance(resolved, dict):
                return resolved
            merged = dict(resolved)
            for key, value in node.items():
                if key != "$ref":
                    merged[key] = resolve_refs(value, root_doc, loader, spec_path, seen)
            return merged
        return {
            key: resolve_refs(value, root_doc, loader, spec_path, seen)
            for key, value in node.items()
        }
    if isinstance(node, list):
        return [resolve_refs(item, root_doc, loader, spec_path, seen) for item in node]
    return node


def follow_ref(ref, root_doc, loader, spec_path):
    """Follow a single $ref to its target document node."""
    if ref.startswith("#"):
        target = root_doc
        for part in ref[1:].split("/"):
            if not part:
                continue
            key = part.replace("~1", "/").replace("~0", "~")
            if isinstance(target, dict) and key in target:
                target = target[key]
            else:
                return {}
        return target if target is not None else {}
    if "#" in ref:
        file_part, fragment = ref.split("#", 1)
    else:
        file_part, fragment = ref, ""
    if file_part and spec_path is None:
        return {}
    base_dir = str(Path(spec_path).parent) if spec_path else "."
    candidate = (Path(base_dir) / file_part).as_posix() if file_part else spec_path
    # AsyncAPI specs reference packaged 'shared/...' paths; fall back to the
    # 'shared-schemas/...' source tree when the packaged path is absent.
    fallbacks = [candidate]
    if "shared/" in candidate and not candidate.startswith("shared-schemas/"):
        tail = candidate.split("shared/", 1)[1]
        fallbacks.append(f"shared-schemas/{tail}")
        if spec_path.startswith("projection-api/"):
            fallbacks.append(candidate)
    target_doc = None
    for option in fallbacks:
        target_doc = loader.load(option)
        if target_doc is not None:
            candidate = option
            break
    if target_doc is None:
        return {}
    if not fragment:
        return target_doc
    target = target_doc
    for part in fragment.split("/"):
        if not part:
            continue
        key = part.replace("~1", "/").replace("~0", "~")
        if isinstance(target, dict) and key in target:
            target = target[key]
        else:
            return {}
    return target if target is not None else {}


def without_ignored(node):
    """Strip documentation-only keys that never change the contract shape."""
    if isinstance(node, dict):
        cleaned = {}
        for key, value in node.items():
            if key in IGNORED_KEYS or key.startswith("x-"):
                continue
            cleaned[key] = without_ignored(value)
        return cleaned
    if isinstance(node, list):
        return [without_ignored(item) for item in node]
    return node


def combine(severity, findings, message, level):
    if level > severity:
        severity = level
    if message and level >= PATCH:
        findings.append(message)
    return severity


NUMERIC_BOUNDS = ("minimum", "maximum", "minItems", "maxItems", "minLength",
                  "maxLength", "minProperties", "maxProperties")


def compare_bounds(base, head, where, severity, findings):
    for key in NUMERIC_BOUNDS:
        in_base = key in base
        in_head = key in head
        if not in_base and not in_head:
            continue
        if not in_base and in_head:
            if key.startswith("min"):
                severity = combine(severity, findings,
                                   f"BREAKING: {where} adds '{key}: {head[key]}'", MAJOR)
            else:
                severity = combine(severity, findings,
                                   f"BREAKING: {where} adds '{key}: {head[key]}'", MAJOR)
            continue
        if in_base and not in_head:
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} drops '{key}'", PATCH)
            continue
        try:
            old, new = float(base[key]), float(head[key])
        except (TypeError, ValueError):
            if base[key] != head[key]:
                severity = combine(severity, findings,
                                   f"BREAKING: {where} changes '{key}'", MAJOR)
            continue
        if old == new:
            continue
        tightened = (key.startswith("min") and new > old) or (
            key.startswith("max") and new < old
        )
        if tightened:
            severity = combine(severity, findings,
                               f"BREAKING: {where} tightens '{key}' {old} -> {new}", MAJOR)
        else:
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} loosens '{key}' {old} -> {new}", PATCH)
    for key in ("exclusiveMinimum", "exclusiveMaximum", "uniqueItems"):
        if base.get(key) == head.get(key):
            continue
        if head.get(key) in (True, 1) and not base.get(key):
            severity = combine(severity, findings,
                               f"BREAKING: {where} enables '{key}'", MAJOR)
        else:
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} relaxes '{key}'", PATCH)
    return severity


def compare_schemas(base, head, where, severity, findings):
    """Recursively compare two (ref-resolved) schema nodes."""
    base = base if isinstance(base, dict) else {}
    head = head if isinstance(head, dict) else {}
    if not base and not head:
        return severity
    if bool(base) != bool(head):
        level = MAJOR if base else MINOR
        kind = "removes" if base else "adds"
        return combine(severity, findings, f"{'BREAKING' if base else 'ADDED'}: {where} {kind} schema", level)

    for key in ("type", "format"):
        if base.get(key) != head.get(key):
            if base.get(key) is not None and head.get(key) is not None:
                severity = combine(severity, findings,
                                   f"BREAKING: {where} changes '{key}' "
                                   f"'{base.get(key)}' -> '{head.get(key)}'", MAJOR)
            elif base.get(key) is not None:
                severity = combine(severity, findings,
                                   f"BREAKING: {where} drops '{key}'", MAJOR)
            else:
                severity = combine(severity, findings,
                                   f"BREAKING: {where} adds '{key}'", MAJOR)

    if "nullable" in base or "nullable" in head:
        if bool(base.get("nullable")) and not head.get("nullable"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} is no longer nullable", MAJOR)
        elif bool(head.get("nullable")) and not base.get("nullable"):
            severity = combine(severity, findings,
                               f"ADDED: {where} becomes nullable", MINOR)

    if "enum" in base or "enum" in head:
        old = base.get("enum") or []
        new = head.get("enum") or []
        removed = [v for v in old if v not in new]
        added = [v for v in new if v not in old]
        if removed:
            severity = combine(severity, findings,
                               f"BREAKING: {where} removes enum value(s) {removed}", MAJOR)
        if added:
            severity = combine(severity, findings,
                               f"ADDED: {where} adds enum value(s) {added}", MINOR)

    base_props = base.get("properties") or {}
    head_props = head.get("properties") or {}
    base_required = set(base.get("required") or [])
    head_required = set(head.get("required") or [])
    for name in sorted(set(base_props) - set(head_props)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes property '{name}'", MAJOR)
    for name in sorted(set(head_props) - set(base_props)):
        if name in head_required:
            severity = combine(severity, findings,
                               f"BREAKING: {where} adds required property '{name}'", MAJOR)
        else:
            severity = combine(severity, findings,
                               f"ADDED: {where} adds optional property '{name}'", MINOR)
    for name in sorted(set(base_props) & set(head_props)):
        if name in base_required and name not in head_required:
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} property '{name}' becomes optional", PATCH)
        if name not in base_required and name in head_required:
            severity = combine(severity, findings,
                               f"BREAKING: {where} property '{name}' becomes required", MAJOR)
        severity = compare_schemas(base_props[name], head_props[name],
                                   f"{where} property '{name}'", severity, findings)
    for name in sorted(head_required - base_required - set(head_props)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} adds required entry '{name}'", MAJOR)

    for key in ("items", "additionalProperties", "contains"):
        if key in base or key in head:
            b_child, h_child = base.get(key), head.get(key)
            if isinstance(b_child, bool) or isinstance(h_child, bool):
                if b_child != h_child:
                    if h_child is False:
                        severity = combine(severity, findings,
                                           f"BREAKING: {where} restricts '{key}'", MAJOR)
                    else:
                        severity = combine(severity, findings,
                                           f"COMPATIBLE: {where} relaxes '{key}'", PATCH)
            elif isinstance(b_child, dict) or isinstance(h_child, dict):
                severity = compare_schemas(b_child or {}, h_child or {},
                                           f"{where} '{key}'", severity, findings)
            elif b_child != h_child and (b_child is not None or h_child is not None):
                severity = combine(severity, findings,
                                   f"BREAKING: {where} changes '{key}'", MAJOR)

    for key in ("allOf", "oneOf", "anyOf"):
        b_list = base.get(key) or []
        h_list = head.get(key) or []
        if not b_list and not h_list:
            continue
        severity = compare_combiner(b_list, h_list, key, where, severity, findings)

    if "not" in base or "not" in head:
        if without_ignored(base.get("not")) != without_ignored(head.get("not")):
            severity = combine(severity, findings,
                               f"BREAKING: {where} changes 'not' subschema", MAJOR)

    severity = compare_bounds(base, head, where, severity, findings)
    return severity


def compare_combiner(base_list, head_list, key, where, severity, findings):
    base_norm = [without_ignored(item) for item in base_list]
    head_norm = [without_ignored(item) for item in head_list]
    if base_norm == head_norm:
        return severity
    if key in ("oneOf", "anyOf"):
        if len(head_norm) > len(base_norm) and all(
            any(b == h for h in head_norm) for b in base_norm
        ):
            return combine(severity, findings,
                           f"ADDED: {where} widens '{key}' with new branch", MINOR)
        return combine(severity, findings,
                       f"BREAKING: {where} changes '{key}' branches", MAJOR)
    # allOf narrows when branches are added, loosens when removed.
    if len(head_norm) > len(base_norm):
        return combine(severity, findings,
                       f"BREAKING: {where} adds '{key}' constraint", MAJOR)
    if len(head_norm) < len(base_norm):
        return combine(severity, findings,
                       f"COMPATIBLE: {where} drops '{key}' constraint", PATCH)
    return combine(severity, findings,
                   f"BREAKING: {where} changes '{key}' constraint", MAJOR)


def compare_media(base_media, head_media, where, severity, findings, loader_pair):
    base_media = base_media or {}
    head_media = head_media or {}
    for ctype in sorted(set(base_media) - set(head_media)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes media type '{ctype}'", MAJOR)
    for ctype in sorted(set(head_media) - set(base_media)):
        severity = combine(severity, findings,
                           f"ADDED: {where} adds media type '{ctype}'", MINOR)
    for ctype in sorted(set(base_media) & set(head_media)):
        b_schema = (base_media[ctype] or {}).get("schema", {})
        h_schema = (head_media[ctype] or {}).get("schema", {})
        b_schema = loader_pair[0](b_schema)
        h_schema = loader_pair[1](h_schema)
        severity = compare_schemas(b_schema, h_schema,
                                   f"{where} '{ctype}' schema", severity, findings)
    return severity


def compare_parameters(base_params, head_params, where, severity, findings, resolvers):
    def key_of(param):
        return (param.get("name"), param.get("in"))

    base_map = {key_of(p): p for p in base_params or []}
    head_map = {key_of(p): p for p in head_params or []}
    for key in sorted(set(base_map) - set(head_map)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes parameter '{key[0]}' in '{key[1]}'", MAJOR)
    for key in sorted(set(head_map) - set(base_map)):
        if head_map[key].get("required"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} adds required parameter '{key[0]}'", MAJOR)
        else:
            severity = combine(severity, findings,
                               f"ADDED: {where} adds optional parameter '{key[0]}'", MINOR)
    for key in sorted(set(base_map) & set(head_map)):
        b_p, h_p = base_map[key], head_map[key]
        if not b_p.get("required") and h_p.get("required"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} parameter '{key[0]}' becomes required", MAJOR)
        if b_p.get("required") and not h_p.get("required"):
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} parameter '{key[0]}' becomes optional", PATCH)
        b_schema = resolvers[0](b_p.get("schema", {}))
        h_schema = resolvers[1](h_p.get("schema", {}))
        severity = compare_schemas(b_schema, h_schema,
                                   f"{where} parameter '{key[0]}' schema", severity, findings)
    return severity


def compare_operation(base_op, head_op, where, severity, findings, resolvers):
    if base_op.get("operationId") != head_op.get("operationId"):
        if base_op.get("operationId") and head_op.get("operationId"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} renames operationId "
                               f"'{base_op.get('operationId')}' -> '{head_op.get('operationId')}'", MAJOR)
    severity = compare_parameters(base_op.get("parameters"), head_op.get("parameters"),
                                  where, severity, findings, resolvers)
    base_body = base_op.get("requestBody")
    head_body = head_op.get("requestBody")
    if base_body is None and head_body is not None:
        if head_body.get("required"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} adds required request body", MAJOR)
        else:
            severity = combine(severity, findings,
                               f"ADDED: {where} adds optional request body", MINOR)
    elif base_body is not None and head_body is None:
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes request body", MAJOR)
    elif base_body is not None and head_body is not None:
        if not base_body.get("required") and head_body.get("required"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} request body becomes required", MAJOR)
        if base_body.get("required") and not head_body.get("required"):
            severity = combine(severity, findings,
                               f"COMPATIBLE: {where} request body becomes optional", PATCH)
        severity = compare_media((base_body.get("content") or {}),
                                 (head_body.get("content") or {}),
                                 f"{where} request body", severity, findings, resolvers)
    base_resp = base_op.get("responses") or {}
    head_resp = head_op.get("responses") or {}
    for code in sorted(set(base_resp) - set(head_resp)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes response '{code}'", MAJOR)
    for code in sorted(set(head_resp) - set(base_resp)):
        severity = combine(severity, findings,
                           f"ADDED: {where} adds response '{code}'", MINOR)
    for code in sorted(set(base_resp) & set(head_resp)):
        severity = compare_media(((base_resp[code] or {}).get("content") or {}),
                                 ((head_resp[code] or {}).get("content") or {}),
                                 f"{where} response '{code}'", severity, findings, resolvers)
    return severity


def compare_openapi_documents(base_doc, head_doc, spec_path, severity, findings):
    base_doc = base_doc or {}
    head_doc = head_doc or {}

    base_paths = base_doc.get("paths") or {}
    head_paths = head_doc.get("paths") or {}
    for path in sorted(set(base_paths) - set(head_paths)):
        severity = combine(severity, findings,
                           f"BREAKING: {spec_path} removes path '{path}'", MAJOR)
    for path in sorted(set(head_paths) - set(base_paths)):
        severity = combine(severity, findings,
                           f"ADDED: {spec_path} adds path '{path}'", MINOR)
    for path in sorted(set(base_paths) & set(head_paths)):
        base_item = base_paths[path] or {}
        head_item = head_paths[path] or {}
        methods = set(k for k in list(base_item) + list(head_item)
                      if k in ("get", "post", "put", "patch", "delete", "head", "options", "trace"))
        for method in sorted(set(k for k in base_item if k in methods) -
                             set(k for k in head_item if k in methods)):
            severity = combine(severity, findings,
                               f"BREAKING: {spec_path} removes operation '{method.upper()} {path}'", MAJOR)
        for method in sorted(set(k for k in head_item if k in methods) -
                             set(k for k in base_item if k in methods)):
            severity = combine(severity, findings,
                               f"ADDED: {spec_path} adds operation '{method.upper()} {path}'", MINOR)
        for method in sorted(methods & set(base_item) & set(head_item)):
            where = f"{spec_path} '{method.upper()} {path}'"
            resolvers = (
                lambda node, _doc=base_doc: resolve_refs(node or {}, _doc, base_loader, spec_path),
                lambda node, _doc=head_doc: resolve_refs(node or {}, _doc, head_loader, spec_path),
            )
            severity = compare_operation(base_item[method] or {}, head_item[method] or {},
                                         where, severity, findings, resolvers)

    base_schemas = (base_doc.get("components") or {}).get("schemas") or {}
    head_schemas = (head_doc.get("components") or {}).get("schemas") or {}
    for name in sorted(set(base_schemas) - set(head_schemas)):
        severity = combine(severity, findings,
                           f"BREAKING: {spec_path} removes schema '{name}'", MAJOR)
    for name in sorted(set(head_schemas) - set(base_schemas)):
        severity = combine(severity, findings,
                           f"ADDED: {spec_path} adds schema '{name}'", MINOR)
    for name in sorted(set(base_schemas) & set(head_schemas)):
        b_schema = resolve_refs(base_schemas[name], base_doc, base_loader, spec_path)
        h_schema = resolve_refs(head_schemas[name], head_doc, head_loader, spec_path)
        if without_ignored(b_schema) == without_ignored(h_schema):
            continue
        severity = compare_schemas(b_schema, h_schema,
                                   f"{spec_path} schema '{name}'", severity, findings)
    return severity


def compare_asyncapi_documents(base_doc, head_doc, spec_path, severity, findings):
    base_doc = base_doc or {}
    head_doc = head_doc or {}

    base_channels = base_doc.get("channels") or {}
    head_channels = head_doc.get("channels") or {}
    for name in sorted(set(base_channels) - set(head_channels)):
        severity = combine(severity, findings,
                           f"BREAKING: {spec_path} removes channel '{name}'", MAJOR)
    for name in sorted(set(head_channels) - set(base_channels)):
        severity = combine(severity, findings,
                           f"ADDED: {spec_path} adds channel '{name}'", MINOR)
    for name in sorted(set(base_channels) & set(head_channels)):
        raw_b_ch = base_channels[name] if isinstance(base_channels[name], dict) else {}
        raw_h_ch = head_channels[name] if isinstance(head_channels[name], dict) else {}
        b_ch = resolve_refs(base_channels[name], base_doc, base_loader, spec_path)
        h_ch = resolve_refs(head_channels[name], head_doc, head_loader, spec_path)
        if isinstance(b_ch, dict) and isinstance(h_ch, dict):
            if b_ch.get("address") != h_ch.get("address"):
                severity = combine(severity, findings,
                                   f"BREAKING: {spec_path} changes channel '{name}' address", MAJOR)
            # Raw (unresolved) message maps: a channel entry that is a pure
            # alias of a component message is covered by the component-message
            # comparison, so only inline messages are deep-compared here.
            severity = compare_message_maps(raw_b_ch.get("messages"), raw_h_ch.get("messages"),
                                            f"{spec_path} channel '{name}'", severity, findings,
                                            base_doc, head_doc, spec_path)

    base_ops = base_doc.get("operations") or {}
    head_ops = head_doc.get("operations") or {}
    for name in sorted(set(base_ops) - set(head_ops)):
        severity = combine(severity, findings,
                           f"BREAKING: {spec_path} removes operation '{name}'", MAJOR)
    for name in sorted(set(head_ops) - set(base_ops)):
        severity = combine(severity, findings,
                           f"ADDED: {spec_path} adds operation '{name}'", MINOR)
    for name in sorted(set(base_ops) & set(head_ops)):
        b_op = resolve_refs(base_ops[name], base_doc, base_loader, spec_path)
        h_op = resolve_refs(head_ops[name], head_doc, head_loader, spec_path)
        if isinstance(b_op, dict) and isinstance(h_op, dict):
            if b_op.get("action") != h_op.get("action"):
                severity = combine(severity, findings,
                                   f"BREAKING: {spec_path} changes operation '{name}' action", MAJOR)

    base_msgs = (base_doc.get("components") or {}).get("messages") or {}
    head_msgs = (head_doc.get("components") or {}).get("messages") or {}
    severity = compare_message_maps(base_msgs, head_msgs, spec_path, severity, findings,
                                    base_doc, head_doc, spec_path)

    base_schemas = (base_doc.get("components") or {}).get("schemas") or {}
    head_schemas = (head_doc.get("components") or {}).get("schemas") or {}
    for name in sorted(set(base_schemas) - set(head_schemas)):
        severity = combine(severity, findings,
                           f"BREAKING: {spec_path} removes schema '{name}'", MAJOR)
    for name in sorted(set(head_schemas) - set(base_schemas)):
        severity = combine(severity, findings,
                           f"ADDED: {spec_path} adds schema '{name}'", MINOR)
    for name in sorted(set(base_schemas) & set(head_schemas)):
        # Envelope-header and shared-enum aliases resolve to external files;
        # the shared-file comparison below already classifies those, so only
        # compare module-local payload shapes here to avoid double reporting.
        b_raw, h_raw = base_schemas[name], head_schemas[name]
        if is_shared_alias(b_raw) and is_shared_alias(h_raw):
            continue
        b_schema = resolve_refs(b_raw, base_doc, base_loader, spec_path)
        h_schema = resolve_refs(h_raw, head_doc, head_loader, spec_path)
        if without_ignored(b_schema) == without_ignored(h_schema):
            continue
        severity = compare_schemas(b_schema, h_schema,
                                   f"{spec_path} schema '{name}'", severity, findings)
    return severity


def is_shared_alias(node):
    ref = node.get("$ref") if isinstance(node, dict) else None
    return isinstance(ref, str) and ("shared/" in ref or "shared-schemas/" in ref)


def compare_message_maps(base_msgs, head_msgs, where, severity, findings, base_doc, head_doc,
                         spec_path):
    base_msgs = base_msgs if isinstance(base_msgs, dict) else {}
    head_msgs = head_msgs if isinstance(head_msgs, dict) else {}
    for name in sorted(set(base_msgs) - set(head_msgs)):
        severity = combine(severity, findings,
                           f"BREAKING: {where} removes message '{name}'", MAJOR)
    for name in sorted(set(head_msgs) - set(base_msgs)):
        severity = combine(severity, findings,
                           f"ADDED: {where} adds message '{name}'", MINOR)
    for name in sorted(set(base_msgs) & set(head_msgs)):
        raw_base, raw_head = base_msgs[name], head_msgs[name]
        # A channel message that is a pure alias of a component message is
        # covered by the component-message comparison below; comparing its
        # resolved payload here as well would report every change twice.
        if is_pure_ref(raw_base) and is_pure_ref(raw_head):
            b_msg = resolve_refs(raw_base, base_doc, base_loader, spec_path)
            h_msg = resolve_refs(raw_head, head_doc, head_loader, spec_path)
            if isinstance(b_msg, dict) and isinstance(h_msg, dict):
                if b_msg.get("name") != h_msg.get("name"):
                    severity = combine(severity, findings,
                                       f"BREAKING: {where} renames message '{name}'", MAJOR)
            continue
        b_msg = resolve_refs(raw_base, base_doc, base_loader, spec_path)
        h_msg = resolve_refs(raw_head, head_doc, head_loader, spec_path)
        if not isinstance(b_msg, dict) or not isinstance(h_msg, dict):
            continue
        if b_msg.get("name") != h_msg.get("name"):
            severity = combine(severity, findings,
                               f"BREAKING: {where} renames message '{name}'", MAJOR)
        raw_b_msg = raw_base if isinstance(raw_base, dict) else {}
        raw_h_msg = raw_head if isinstance(raw_head, dict) else {}
        for part in ("payload", "headers"):
            # Payloads/headers aliasing a component schema are covered by the
            # schema comparison; only inline shapes are compared here.
            if is_component_schema_ref(raw_b_msg.get(part)) and \
                    is_component_schema_ref(raw_h_msg.get(part)):
                continue
            b_part = b_msg.get(part, {})
            h_part = h_msg.get(part, {})
            if without_ignored(b_part) != without_ignored(h_part):
                severity = compare_schemas(b_part, h_part,
                                           f"{where} message '{name}' {part}", severity, findings)
    return severity


def is_pure_ref(node):
    return isinstance(node, dict) and set(node) == {"$ref"} and isinstance(node["$ref"], str)


def is_component_schema_ref(node):
    return (isinstance(node, dict) and set(node) == {"$ref"}
            and node["$ref"].startswith("#/components/schemas/"))


def classify_spec_pair(spec_path, base_doc, head_doc):
    """Classify one spec file pair, returning (severity, findings)."""
    severity, findings = NONE, []
    if base_doc is None and head_doc is None:
        return severity, findings
    if base_doc is None or head_doc is None:
        if head_doc is None:
            return MAJOR, [f"BREAKING: {spec_path} spec file removed"]
        # A brand-new spec file inside an existing module (e.g. openapi v2
        # added next to v1) is a compatible addition; a whole new module has
        # no base version to compare and is handled by the caller.
        return MINOR, [f"ADDED: {spec_path} spec file added"]
    if without_ignored(base_doc) == without_ignored(head_doc):
        # Structurally identical: description/comment-only edits land here.
        return NONE, []
    if spec_path.endswith("asyncapi.yml") or "asyncapi" in spec_path:
        severity = compare_asyncapi_documents(base_doc, head_doc, spec_path, severity, findings)
    else:
        severity = compare_openapi_documents(base_doc, head_doc, spec_path, severity, findings)
    if severity == NONE:
        # Documents differ only in ignored/documentation keys.
        severity = PATCH
    return severity, findings


def classify_shared_pair(repo_path, base_doc, head_doc):
    severity, findings = NONE, []
    if base_doc is None and head_doc is None:
        return severity, findings
    if base_doc is None or head_doc is None:
        level = MAJOR if head_doc is None else MINOR
        kind = "removed" if head_doc is None else "added"
        label = "BREAKING" if head_doc is None else "ADDED"
        return level, [f"{label}: {repo_path} shared file {kind}"]
    for name in sorted(set(base_doc) - set(head_doc)):
        severity = combine(severity, findings,
                           f"BREAKING: {repo_path} removes shared definition '{name}'", MAJOR)
    for name in sorted(set(head_doc) - set(base_doc)):
        severity = combine(severity, findings,
                           f"ADDED: {repo_path} adds shared definition '{name}'", MINOR)
    for name in sorted(set(base_doc) & set(head_doc)):
        b_schema = base_doc[name] if isinstance(base_doc, dict) else {}
        h_schema = head_doc[name] if isinstance(head_doc, dict) else {}
        if without_ignored(b_schema) == without_ignored(h_schema):
            continue
        severity = compare_schemas(b_schema, h_schema,
                                   f"{repo_path} shared '{name}'", severity, findings)
    if severity == NONE and without_ignored(base_doc) != without_ignored(head_doc):
        severity = PATCH
    return severity, findings


def required_bump(severity):
    if severity >= MAJOR:
        return "major"
    if severity >= MINOR:
        return "minor"
    return None


def evaluate_module(module, base_commit, base_loader, head_loader, touched, shared_severity, shared_findings):
    """Evaluate one module; return a result dict."""
    pom_path = f"{module}/pom.xml"
    base_pom = file_at_revision(pom_path, base_commit)
    head_pom_file = REPO_ROOT / pom_path
    head_pom = head_pom_file.read_text(encoding="utf-8") if head_pom_file.is_file() else None

    if base_pom is None and head_pom is None:
        return None
    if base_pom is None:
        return {
            "module": module, "status": "pass", "reason": "new module has no base version",
            "severity": "none", "bump": "n/a", "findings": [],
        }

    base_version = pom_version(base_pom)
    head_version = pom_version(head_pom)
    bump = bump_type(parse_version(base_version), parse_version(head_version))

    owned = spec_files_for_module(module)
    severity, findings = NONE, []
    for spec_path in owned:
        in_base = file_at_revision(spec_path, base_commit) is not None
        in_head = (REPO_ROOT / spec_path).is_file()
        if not in_base and not in_head:
            continue
        if spec_path not in touched and not (in_base != in_head):
            continue
        base_doc = base_loader.load(spec_path)
        head_doc = head_loader.load(spec_path)
        if base_doc is None and head_doc is None and in_base == in_head:
            continue
        file_sev, file_findings = classify_spec_pair(spec_path, base_doc, head_doc)
        if file_sev > severity:
            severity = file_sev
        findings.extend(file_findings)

    if module_bundles_shared(module) and shared_severity > NONE:
        if shared_severity > severity:
            severity = shared_severity
        findings.extend(shared_findings)

    if bump is None:
        return {
            "module": module, "status": "fail",
            "reason": f"version {head_version!r} is not a valid increase over base {base_version!r}",
            "severity": SEVERITY_NAMES[severity],
            "bump": "invalid", "findings": findings,
        }

    need = required_bump(severity)
    if need == "major" and bump != MAJOR:
        return {
            "module": module, "status": "fail",
            "reason": f"breaking spec change requires a major bump "
                      f"(base {base_version} -> head {head_version} is {BUMP_NAMES[bump]})",
            "severity": SEVERITY_NAMES[severity], "bump": BUMP_NAMES[bump], "findings": findings,
        }
    if need == "minor" and bump not in (MINOR, MAJOR):
        return {
            "module": module, "status": "fail",
            "reason": f"compatible spec addition requires at least a minor bump "
                      f"(base {base_version} -> head {head_version} is {BUMP_NAMES[bump]})",
            "severity": SEVERITY_NAMES[severity], "bump": BUMP_NAMES[bump], "findings": findings,
        }
    return {
        "module": module, "status": "pass",
        "reason": f"spec change '{SEVERITY_NAMES[severity]}' satisfied by {BUMP_NAMES[bump]} bump "
                  f"({base_version} -> {head_version})" if severity or bump else
                  f"no spec change, version unchanged at {head_version}",
        "severity": SEVERITY_NAMES[severity], "bump": BUMP_NAMES[bump], "findings": findings,
    }


base_loader = None
head_loader = None


def main(argv=None):
    global base_loader, head_loader
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="origin/main",
                        help="base git ref to compare against (default: origin/main)")
    parser.add_argument("--modules", default="",
                        help="comma-separated module subset (default: all discovered)")
    args = parser.parse_args(argv)

    base_commit = resolve_base_ref(args.base)
    base_loader = RevisionLoader(base_commit)
    head_loader = RevisionLoader(None)

    touched = set(changed_files(base_commit))
    shared_files = [f for f in touched if f.startswith("shared-schemas/")]
    shared_severity, shared_findings = NONE, []
    for shared_path in shared_files:
        sev, items = classify_shared_pair(shared_path, base_loader.load(shared_path),
                                          head_loader.load(shared_path))
        if sev > shared_severity:
            shared_severity = sev
        shared_findings.extend(items)

    modules = discover_modules()
    if args.modules:
        wanted = {m.strip() for m in args.modules.split(",") if m.strip()}
        modules = [m for m in modules if m in wanted]

    results = []
    for module in modules:
        result = evaluate_module(module, base_commit, base_loader, head_loader,
                                 touched, shared_severity, shared_findings)
        if result is not None:
            results.append(result)

    failures = [r for r in results if r["status"] == "fail"]
    for result in results:
        marker = "PASS" if result["status"] == "pass" else "FAIL"
        print(f"[{marker}] {result['module']}: {result['reason']}")
        for finding in result["findings"]:
            print(f"       - {finding}")
        if result["status"] == "fail":
            print(f"::error file={result['module']}/pom.xml::"
                  f"spec compatibility gate: {result['reason']}")
    print(f"spec-compat: {len(results) - len(failures)}/{len(results)} modules pass")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
