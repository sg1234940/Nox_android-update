#!/usr/bin/env python3
"""Fail-closed gate for Iskra mouth mesh authoring; no Blender dependency."""
import json
import sys
from pathlib import Path

def validate(report):
    if report.get("schema") != "ISKRA_MOUTH_GEOMETRY_REPORT/3.0":
        raise ValueError("Unsupported report schema")
    if report.get("safe_for_automatic_build") is not False:
        raise ValueError("Unsafe automatic-build flag")
    if report.get("mouth_topology_verified") is not False:
        raise ValueError("Unsubstantiated topology verification")
    if any(item.get("status") == "VERIFIED" for item in report.get("regions", {}).values()):
        raise ValueError("Unsubstantiated verified region")
    return True

if __name__ == "__main__":
    validate(json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")))
    print("PASS: audit report cannot authorize topology mutation")
