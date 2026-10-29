#!/usr/bin/env python3
"""ISKRA mouth V3: fail-closed topology plan validator (no bpy required).

This tool does NOT cut a mesh. It validates a proposed ordered mouth boundary
against the source mesh connectivity before a Blender builder may use it.
"""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


class Blocked(ValueError):
    pass


def validate_plan(mesh, plan):
    """mesh: {vertices:[[x,y,z]], faces:[[vertex,...]]}; plan: ordered cut loop."""
    if plan.get("schema") != "ISKRA_MOUTH_CUT_PLAN/1":
        raise Blocked("unsupported plan schema")
    digest = hashlib.sha256(json.dumps(mesh, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    if plan.get("mesh_sha256") != digest:
        raise Blocked("mesh fingerprint mismatch")
    vertices, faces = mesh["vertices"], mesh["faces"]
    n = len(vertices)
    loop = plan.get("cut_loop")
    if not isinstance(loop, list) or len(loop) < 6 or any(type(i) is not int or i < 0 or i >= n for i in loop):
        raise Blocked("invalid cut loop")
    if len(set(loop)) != len(loop):
        raise Blocked("repeated loop vertices")
    edge_counts = Counter()
    adjacency = defaultdict(set)
    for face in faces:
        if len(face) < 3 or len(set(face)) != len(face):
            raise Blocked("degenerate source face")
        if any(type(i) is not int or i < 0 or i >= n for i in face):
            raise Blocked("invalid source face index")
        for a,b in zip(face, face[1:]+face[:1]):
            edge = tuple(sorted((a,b)))
            edge_counts[edge] += 1
            adjacency[a].add(b)
            adjacency[b].add(a)
    if any(count > 2 for count in edge_counts.values()):
        raise Blocked("non-manifold source edge")
    for a,b in zip(loop, loop[1:]+loop[:1]):
        if edge_counts[tuple(sorted((a,b)))] != 2:
            raise Blocked("cut loop must follow interior mesh edges")
    selected = set(loop)
    if any(sum(v in selected for v in adjacency[u]) < 2 for u in loop):
        raise Blocked("disconnected loop")
    upper = plan.get("upper_rim", [])
    lower = plan.get("lower_rim", [])
    if not upper or not lower or set(upper)&set(lower):
        raise Blocked("invalid upper/lower rim partition")
    if any(type(i) is not int or i not in selected for i in upper+lower):
        raise Blocked("rim vertex outside loop")
    if set(upper)|set(lower) != selected:
        raise Blocked("unassigned loop vertex")
    # This establishes connectivity only, not facial anatomy or safe cutting.
    return {"status":"TOPOLOGY_CANDIDATE","mesh_sha256":digest,"loop_vertices":len(loop),
            "anatomy_verified":False,"safe_for_automatic_build":False}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--mesh-json",required=True)
    parser.add_argument("--plan",required=True)
    parser.add_argument("--report",required=True)
    args=parser.parse_args()
    report={"status":"BLOCKED","safe_for_automatic_build":False}
    try:
        mesh=json.loads(Path(args.mesh_json).read_text())
        plan=json.loads(Path(args.plan).read_text())
        report=validate_plan(mesh,plan)
    except (ValueError,KeyError,TypeError,IndexError) as exc:
        report["reason"]=str(exc)
    output=Path(args.report)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report))
    raise SystemExit(0 if report["status"]=="TOPOLOGY_CANDIDATE" else 2)


if __name__=="__main__":
    main()
