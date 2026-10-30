#!/usr/bin/env python3
"""ISKRA mouth region audit V3. Read-only topology analysis; never authorizes cutting."""
import argparse
import hashlib
import json
import sys
from collections import defaultdict, deque
from pathlib import Path

import bpy
sys.path.insert(0,str(Path(__file__).resolve().parent))
from iskra_mouth_cut_plan import fingerprint, SCHEMA

REGIONS = ("LIP_UPPER", "LIP_LOWER", "MOUTH_CORNER_L",
           "MOUTH_CORNER_R", "CHIN", "MOUTH_INTERIOR")

def boundary_components(mesh):
    usage = defaultdict(int)
    neighbors = defaultdict(set)
    for polygon in mesh.polygons:
        vertices = list(polygon.vertices)
        for a, b in zip(vertices, vertices[1:] + vertices[:1]):
            usage[tuple(sorted((a, b)))] += 1
    for (a, b), count in usage.items():
        if count == 1:
            neighbors[a].add(b)
            neighbors[b].add(a)
    remaining = set(neighbors)
    components = []
    while remaining:
        start = remaining.pop()
        queue = deque([start])
        component = {start}
        while queue:
            for neighbor in neighbors[queue.popleft()]:
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    component.add(neighbor)
                    queue.append(neighbor)
        components.append({
            "vertex_count": len(component),
            "closed_loop": all(len(neighbors[v]) == 2 for v in component),
            "vertex_indices": sorted(component),
        })
    return components

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--report", default="MOUTH_GEOMETRY_REPORT.json")
    parser.add_argument("--mesh-json")
    parser.add_argument("--draft-plan")
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    options = parser.parse_args(args)
    if not bpy.app.background:
        raise RuntimeError("BLOCKED: analyzer CLI requires background mode")
    before=set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(Path(options.input).resolve()))
    imported=set(bpy.data.objects)-before
    meshes = [obj for obj in imported if obj.type == "MESH" and any(m.type=="ARMATURE" for m in obj.modifiers)]
    if len(meshes)!=1:
        raise RuntimeError("BLOCKED: expected exactly one skinned source mesh")
    report = {
        "schema": "ISKRA_MOUTH_GEOMETRY_REPORT/3.0",
        "source": Path(options.input).name,
        "input_sha256": hashlib.sha256(Path(options.input).read_bytes()).hexdigest(),
        "blender_version": bpy.app.version_string,
        "status": "CANDIDATE_ONLY",
        "safe_for_automatic_build": False,
        "mouth_topology_verified": False,
        "regions": {name: {"status": "UNKNOWN"} for name in REGIONS},
        "meshes": [],
        "warnings": ["Global mesh boundary loops are not evidence of mouth rims.",
                     "Manual landmark/topology confirmation required before modifying polygons."],
    }
    for obj in meshes:
        mesh = obj.data
        raw={"vertices":[list(v.co) for v in mesh.vertices],"faces":[list(p.vertices) for p in mesh.polygons]}
        report["meshes"].append({
            "name": obj.name,
            "mesh_sha256": fingerprint(raw),
            "vertices": len(mesh.vertices),
            "polygons": len(mesh.polygons),
            "uv_layers": len(mesh.uv_layers),
            "shape_keys": [key.name for key in mesh.shape_keys.key_blocks]
                          if mesh.shape_keys else [],
            "boundary_components": boundary_components(mesh),
        })
    if options.mesh_json:
        output=Path(options.mesh_json);output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(json.dumps(raw,separators=(",",":"),allow_nan=False)+"\n")
    if options.draft_plan:
        # Deliberately invalid until a person/authoring step supplies landmarks.
        draft={"schema":SCHEMA,"source_mesh":meshes[0].name,"mesh_sha256":fingerprint(raw),
               "cut_loop":[],"remove_faces":[],"mouth_vertices":[],"protected_vertices":[],
               "upper_rim":[],"lower_rim":[],"inward_axis":None,"depth_ratio":0.18,
               "anatomy_reviewed":False,"status":"ANATOMY_REVIEW_REQUIRED"}
        output=Path(options.draft_plan);output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(json.dumps(draft,indent=2)+"\n")
    path = Path(options.report)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("AUDIT ONLY: no mesh modified; automatic build BLOCKED", path)

if __name__ == "__main__":
    main()
