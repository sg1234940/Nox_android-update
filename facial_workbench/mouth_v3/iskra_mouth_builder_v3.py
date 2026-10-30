#!/usr/bin/env python3
"""ISKRA V3 experimental stitched mouth cavity builder.

Requires explicit face IDs and a cyclic ordered cut loop from a trusted,
model-specific review. Never guesses face selection from color or PCA.
No GLB export: writes a Blender staging file only after structural checks.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import sys
from collections import Counter
from pathlib import Path

import bpy
import bmesh
from mathutils import Vector


class Blocked(RuntimeError):
    pass


def block(reason):
    raise Blocked("BLOCKED: " + reason)


def mesh_fingerprint(obj):
    data = {
        "vertices": [[float(c) for c in v.co] for v in obj.data.vertices],
        "faces": [list(p.vertices) for p in obj.data.polygons],
    }
    payload = json.dumps(data, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(payload).hexdigest()


def boundary_edges(bm):
    return {frozenset(v.index for v in edge.verts)
            for edge in bm.edges if edge.is_boundary}


def inspect_cut(bm, loop):
    bm.verts.index_update()
    bm.edges.index_update()
    bm.faces.index_update()
    rim = set(loop)
    boundary = [edge for edge in bm.edges if edge.is_boundary
                and all(v.index in rim for v in edge.verts)]
    degree = Counter(v.index for edge in boundary for v in edge.verts)
    if len(boundary) != len(loop) or set(degree) != rim or any(x != 2 for x in degree.values()):
        block("cut did not yield exactly one closed boundary loop")
    expected = {frozenset((a, b)) for a, b in zip(loop, loop[1:] + loop[:1])}
    if {frozenset(v.index for v in e.verts) for e in boundary} != expected:
        block("boundary edges differ from approved loop")


def build(obj, plan, depth_ratio=0.18):
    if obj.data.shape_keys:
        block("existing shape keys: topology edits require separate migration")
    if plan.get("schema") != "ISKRA_MOUTH_CUT_PLAN/2":
        block("unsupported plan schema")
    if plan.get("mesh_sha256") != mesh_fingerprint(obj):
        block("source mesh fingerprint mismatch")
    loop = plan.get("cut_loop")
    faces = plan.get("remove_faces")
    if not isinstance(loop, list) or len(loop) < 6 or len(set(loop)) != len(loop):
        block("invalid ordered cut loop")
    if not isinstance(faces, list) or not faces or len(set(faces)) != len(faces):
        block("invalid face selection")
    if any(type(x) is not int or x < 0 or x >= len(obj.data.vertices) for x in loop):
        block("invalid loop vertex index")
    if any(type(x) is not int or x < 0 or x >= len(obj.data.polygons) for x in faces):
        block("invalid face index")
    if plan.get("anatomy_reviewed") is not True:
        block("anatomical review required")
    axis_values = plan.get("inward_axis")
    if not isinstance(axis_values, list) or len(axis_values) != 3 or not all(type(x) in (int, float) and math.isfinite(x) for x in axis_values):
        block("invalid inward axis")
    axis = Vector(axis_values)
    if axis.length < 1e-8:
        block("zero inward axis")
    axis.normalize()
    # Copy data, including material slots, UVs and armature modifiers.
    result = obj.copy()
    result.data = obj.data.copy()
    bpy.context.collection.objects.link(result)
    result.name = obj.name + "_MOUTH_V3_STAGING"
    bm = bmesh.new()
    try:
        bm.from_mesh(result.data)
        bm.verts.ensure_lookup_table()
        bm.faces.ensure_lookup_table()
        bm.verts.index_update()
        bm.faces.index_update()
        before = boundary_edges(bm)
        selected = [bm.faces[i] for i in faces]
        # Selection must be a connected face island.
        selected_set = set(selected)
        frontier = [selected[0]]
        reached = {selected[0]}
        while frontier:
            for edge in frontier.pop().edges:
                for face in edge.link_faces:
                    if face in selected_set and face not in reached:
                        reached.add(face)
                        frontier.append(face)
        if reached != selected_set:
            block("selected faces are disconnected")
        bmesh.ops.delete(bm, geom=selected, context="FACES_ONLY")
        bm.verts.index_update()
        bm.edges.index_update()
        bm.faces.index_update()
        inspect_cut(bm, loop)
        after = boundary_edges(bm)
        new_edges = after - before
        expected = {frozenset((a, b)) for a, b in zip(loop, loop[1:] + loop[:1])}
        if new_edges != expected:
            block("cut created unexpected boundary edges")
        # Construct an inward ring and cap, stitched to the actual rim.
        coords = [bm.verts[i].co.copy() for i in loop]
        width = max((a - b).length for a in coords for b in coords)
        depth = width * depth_ratio
        if not 0.02 <= depth_ratio <= 0.5 or depth < 1e-8:
            block("invalid cavity depth")
        inner = [bm.verts.new(p + axis * depth) for p in coords]
        bm.verts.index_update()
        rim = [bm.verts[i] for i in loop]
        for i in range(len(loop)):
            j = (i + 1) % len(loop)
            bm.faces.new((rim[i], rim[j], inner[j], inner[i]))
        center = sum((v.co for v in inner), Vector()) / len(inner)
        back = bm.verts.new(center + axis * depth * 0.2)
        for i in range(len(inner)):
            j = (i + 1) % len(inner)
            bm.faces.new((inner[i], inner[j], back))
        bm.normal_update()
        if any(face.calc_area() < 1e-12 for face in bm.faces):
            block("degenerate face after cavity construction")
        if any(not math.isfinite(c) for vert in bm.verts for c in vert.co):
            block("non-finite geometry")
        if any(len(edge.link_faces) > 2 for edge in bm.edges):
            block("nonmanifold edge")
        if boundary_edges(bm) != before:
            block("new cavity left open boundary edges")
        # This stage is deliberately blocked from saving: new vertex skin
        # weights and UVs require an explicit, verified transfer algorithm.
        block("cavity topology staged; UV and skin-weight transfer not yet validated")
    finally:
        bm.free()
        bpy.data.objects.remove(result, do_unlink=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--plan", required=True)
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    opt = parser.parse_args(args)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(Path(opt.input).resolve()))
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    if len(meshes) != 1:
        block("expected exactly one mesh")
    plan = json.loads(Path(opt.plan).read_text())
    build(meshes[0], plan)


if __name__ == "__main__":
    main()
