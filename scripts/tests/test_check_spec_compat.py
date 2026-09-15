#!/usr/bin/env python3
"""Unit tests for scripts/check_spec_compat.py (stdlib unittest only).

Run from the repo root::

    python -m unittest discover -s scripts/tests -v
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_spec_compat as gate


def schema(properties, required=None, **extra):
    node = {"type": "object", "properties": dict(properties)}
    if required:
        node["required"] = list(required)
    node.update(extra)
    return node


def prop(type_, **extra):
    node = {"type": type_}
    node.update(extra)
    return node


class VersionTests(unittest.TestCase):
    def test_parses_plain_semver(self):
        self.assertEqual(gate.parse_version("1.2.3"), (1, 2, 3))

    def test_rejects_snapshot_and_partial(self):
        self.assertIsNone(gate.parse_version("1.2.3-SNAPSHOT"))
        self.assertIsNone(gate.parse_version("1.2"))
        self.assertIsNone(gate.parse_version(None))

    def test_bump_classification(self):
        self.assertEqual(gate.bump_type((1, 2, 3), (1, 2, 3)), gate.NONE)
        self.assertEqual(gate.bump_type((1, 2, 3), (1, 2, 4)), gate.PATCH)
        self.assertEqual(gate.bump_type((1, 2, 3), (1, 3, 0)), gate.MINOR)
        self.assertEqual(gate.bump_type((1, 2, 3), (2, 0, 0)), gate.MAJOR)

    def test_downgrade_is_invalid(self):
        self.assertIsNone(gate.bump_type((2, 0, 0), (1, 9, 9)))
        self.assertIsNone(gate.bump_type((1, 2, 3), None))

    def test_pom_version_reads_first_version(self):
        pom = "<project><artifactId>m</artifactId><version>3.0.3</version></project>"
        self.assertEqual(gate.pom_version(pom), "3.0.3")


class SchemaDiffTests(unittest.TestCase):
    def compare(self, base, head):
        return gate.compare_schemas(base, head, "test", gate.NONE, [])

    def test_identical_is_none(self):
        base = schema({"a": prop("string")}, ["a"])
        self.assertEqual(self.compare(base, base), gate.NONE)

    def test_removed_property_is_breaking(self):
        base = schema({"a": prop("string"), "b": prop("string")}, ["a", "b"])
        head = schema({"a": prop("string")}, ["a"])
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_added_optional_property_is_addition(self):
        base = schema({"a": prop("string")}, ["a"])
        head = schema({"a": prop("string"), "b": prop("string")}, ["a"])
        self.assertEqual(self.compare(base, head), gate.MINOR)

    def test_added_required_property_is_breaking(self):
        base = schema({"a": prop("string")}, ["a"])
        head = schema({"a": prop("string"), "b": prop("string")}, ["a", "b"])
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_property_becomes_required_is_breaking(self):
        base = schema({"a": prop("string"), "b": prop("string")}, ["a"])
        head = schema({"a": prop("string"), "b": prop("string")}, ["a", "b"])
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_property_becomes_optional_is_compatible(self):
        base = schema({"a": prop("string"), "b": prop("string")}, ["a", "b"])
        head = schema({"a": prop("string"), "b": prop("string")}, ["a"])
        self.assertEqual(self.compare(base, head), gate.PATCH)

    def test_type_change_is_breaking(self):
        self.assertEqual(self.compare(prop("string"), prop("integer")), gate.MAJOR)

    def test_enum_removal_is_breaking(self):
        base = {"type": "string", "enum": ["A", "B"]}
        head = {"type": "string", "enum": ["A"]}
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_enum_addition_is_addition(self):
        base = {"type": "string", "enum": ["A"]}
        head = {"type": "string", "enum": ["A", "B"]}
        self.assertEqual(self.compare(base, head), gate.MINOR)

    def test_tightened_min_items_is_breaking(self):
        base = {"type": "array", "minItems": 1}
        head = {"type": "array", "minItems": 5}
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_loosened_max_items_is_compatible(self):
        base = {"type": "array", "maxItems": 3}
        head = {"type": "array", "maxItems": 7}
        self.assertEqual(self.compare(base, head), gate.PATCH)

    def test_added_unique_items_is_breaking(self):
        base = {"type": "array"}
        head = {"type": "array", "uniqueItems": True}
        self.assertEqual(self.compare(base, head), gate.MAJOR)

    def test_nested_property_removal_is_breaking(self):
        base = schema({"outer": schema({"x": prop("string")}, ["x"])}, ["outer"])
        head = schema({"outer": schema({}, [])}, ["outer"])
        self.assertEqual(self.compare(base, head), gate.MAJOR)


class DocumentDiffTests(unittest.TestCase):
    def setUp(self):
        gate.base_loader = gate.RevisionLoader(None)
        gate.head_loader = gate.RevisionLoader(None)

    def classify(self, base, head, path="openapi/v1/x.yml"):
        return gate.classify_spec_pair(path, base, head)

    def test_no_change_is_none(self):
        doc = {"openapi": "3.0.3", "paths": {}, "components": {"schemas": {}}}
        severity, _ = self.classify(doc, doc)
        self.assertEqual(severity, gate.NONE)

    def test_description_only_is_no_contract_change(self):
        base = {"openapi": "3.0.3", "info": {"title": "T", "description": "old"},
                "paths": {}}
        head = {"openapi": "3.0.3", "info": {"title": "T", "description": "new"},
                "paths": {}}
        severity, _ = self.classify(base, head)
        self.assertEqual(severity, gate.NONE)

    def test_removed_openapi_path_is_breaking(self):
        base = {"paths": {"/api/v1/a": {"get": {"operationId": "getA",
                                                "responses": {"200": {"description": "ok"}}}}},
                "components": {"schemas": {}}}
        head = {"paths": {}, "components": {"schemas": {}}}
        severity, findings = self.classify(base, head)
        self.assertEqual(severity, gate.MAJOR)
        self.assertTrue(any("removes path" in f for f in findings))

    def test_added_openapi_path_is_addition(self):
        base = {"paths": {}, "components": {"schemas": {}}}
        head = {"paths": {"/api/v1/b": {"post": {"operationId": "createB",
                                                 "responses": {"201": {"description": "ok"}}}}},
                "components": {"schemas": {}}}
        severity, _ = self.classify(base, head)
        self.assertEqual(severity, gate.MINOR)

    def test_renamed_operation_id_is_breaking(self):
        op = {"operationId": "getA", "responses": {"200": {"description": "ok"}}}
        base = {"paths": {"/api/v1/a": {"get": dict(op)}}, "components": {"schemas": {}}}
        renamed = dict(op, operationId="fetchA")
        head = {"paths": {"/api/v1/a": {"get": renamed}}, "components": {"schemas": {}}}
        severity, _ = self.classify(base, head)
        self.assertEqual(severity, gate.MAJOR)

    def test_added_required_parameter_is_breaking(self):
        base_op = {"operationId": "getA", "responses": {"200": {"description": "ok"}}}
        new_param = {"name": "filter", "in": "query", "required": True,
                     "schema": {"type": "string"}}
        head_op = dict(base_op, parameters=[new_param])
        base = {"paths": {"/a": {"get": base_op}}, "components": {"schemas": {}}}
        head = {"paths": {"/a": {"get": head_op}}, "components": {"schemas": {}}}
        severity, _ = self.classify(base, head)
        self.assertEqual(severity, gate.MAJOR)

    def test_added_optional_parameter_is_addition(self):
        base_op = {"operationId": "getA", "responses": {"200": {"description": "ok"}}}
        new_param = {"name": "filter", "in": "query", "required": False,
                     "schema": {"type": "string"}}
        head_op = dict(base_op, parameters=[new_param])
        base = {"paths": {"/a": {"get": base_op}}, "components": {"schemas": {}}}
        head = {"paths": {"/a": {"get": head_op}}, "components": {"schemas": {}}}
        severity, _ = self.classify(base, head)
        self.assertEqual(severity, gate.MINOR)

    def test_removed_asyncapi_message_is_breaking(self):
        base = {"channels": {}, "operations": {},
                "components": {"messages": {"Known": {"name": "Known",
                                                      "payload": {"type": "object"}}},
                               "schemas": {}}}
        head = {"channels": {}, "operations": {},
                "components": {"messages": {}, "schemas": {}}}
        severity, _ = gate.classify_spec_pair("asyncapi/asyncapi.yml", base, head)
        self.assertEqual(severity, gate.MAJOR)

    def test_added_asyncapi_message_is_addition(self):
        base = {"channels": {}, "operations": {},
                "components": {"messages": {}, "schemas": {}}}
        head = {"channels": {}, "operations": {},
                "components": {"messages": {"Fresh": {"name": "Fresh",
                                                      "payload": {"type": "object"}}},
                               "schemas": {}}}
        severity, _ = gate.classify_spec_pair("asyncapi/asyncapi.yml", base, head)
        self.assertEqual(severity, gate.MINOR)

    def test_removed_payload_field_is_breaking(self):
        base_msg = {"name": "E", "payload": schema(
            {"gameId": prop("string")}, ["gameId"])}
        head_msg = {"name": "E", "payload": schema({}, [])}
        base = {"channels": {}, "operations": {},
                "components": {"messages": {"E": base_msg}, "schemas": {}}}
        head = {"channels": {}, "operations": {},
                "components": {"messages": {"E": head_msg}, "schemas": {}}}
        severity, _ = gate.classify_spec_pair("asyncapi/asyncapi.yml", base, head)
        self.assertEqual(severity, gate.MAJOR)

    def test_new_spec_file_is_addition(self):
        severity, _ = self.classify(None, {"paths": {}})
        self.assertEqual(severity, gate.MINOR)

    def test_removed_spec_file_is_breaking(self):
        severity, _ = self.classify({"paths": {}}, None)
        self.assertEqual(severity, gate.MAJOR)

    def test_shared_enum_removal_is_breaking(self):
        base = {"Faction": {"type": "string", "enum": ["A", "B"]}}
        head = {"Faction": {"type": "string", "enum": ["A"]}}
        severity, _ = gate.classify_shared_pair("shared-schemas/enums.yaml", base, head)
        self.assertEqual(severity, gate.MAJOR)

    def test_shared_enum_addition_is_addition(self):
        base = {"Faction": {"type": "string", "enum": ["A"]}}
        head = {"Faction": {"type": "string", "enum": ["A", "B"]}}
        severity, _ = gate.classify_shared_pair("shared-schemas/enums.yaml", base, head)
        self.assertEqual(severity, gate.MINOR)


class VerdictTests(unittest.TestCase):
    def test_required_bump_mapping(self):
        self.assertIsNone(gate.required_bump(gate.NONE))
        self.assertIsNone(gate.required_bump(gate.PATCH))
        self.assertEqual(gate.required_bump(gate.MINOR), "minor")
        self.assertEqual(gate.required_bump(gate.MAJOR), "major")


if __name__ == "__main__":
    unittest.main()
