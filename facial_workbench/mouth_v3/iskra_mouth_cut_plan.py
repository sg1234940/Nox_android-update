#!/usr/bin/env python3
"""Shared ISKRA_MOUTH_CUT_PLAN/3 contract. No Blender dependency or writes.

A manual anatomy approval is required; this module cannot infer mouth anatomy.
The approved region and its protected complement are bound to the source mesh.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

SCHEMA = 'ISKRA_MOUTH_CUT_PLAN/3'
MAX_REMOVED_SURFACE_FRACTION = 0.10

class Blocked(ValueError):
    pass

def require(condition, reason):
    if not condition:
        raise Blocked('BLOCKED: ' + reason)

def sub(a, b): return tuple(x-y for x,y in zip(a,b))
def add(a, b): return tuple(x+y for x,y in zip(a,b))
def mul(a, t): return tuple(x*t for x in a)
def dot(a, b): return sum(x*y for x,y in zip(a,b))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def norm(a): return math.sqrt(dot(a,a))
def unit(a):
    n=norm(a);require(math.isfinite(n) and n>1e-15, 'zero or excessive vector')
    return mul(a, 1/n)

def number(x):
    # Bounded before float conversion: huge JSON integers cannot overflow.
    return type(x) in (int,float) and -1e12 <= x <= 1e12 and math.isfinite(x)

def indices(value, size, name, minimum=1):
    require(isinstance(value,list) and len(value)>=minimum, name+' must be a nonempty list')
    require(all(type(i) is int and 0<=i<size for i in value), name+' contains invalid indices')
    require(len(set(value))==len(value), name+' contains repeated indices')
    return value

def canonical_mesh(mesh):
    require(isinstance(mesh,dict), 'mesh must be an object')
    vv,ff=mesh.get('vertices'),mesh.get('faces')
    require(isinstance(vv,list) and len(vv)>=3, 'missing vertices')
    require(all(isinstance(v,(list,tuple)) and len(v)==3 and all(number(c) for c in v) for v in vv), 'invalid coordinates')
    require(isinstance(ff,list) and ff, 'missing faces')
    faces=[list(indices(f,len(vv),'source face',3)) for f in ff]
    return {'vertices':[[0.0 if c==0 else float(c) for c in v] for v in vv], 'faces':faces}

def fingerprint(mesh):
    payload=json.dumps(canonical_mesh(mesh),sort_keys=True,separators=(',',':'),allow_nan=False).encode()
    return hashlib.sha256(payload).hexdigest()

def face_vector(coords, face):
    # Newell area vector: also checks simple planar polygons via triangulate().
    return tuple(sum(cross(coords[a],coords[b])[k] for a,b in zip(face,face[1:]+face[:1]))/2 for k in range(3))

def orient(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
def on_segment(a,b,p,eps):
    return abs(orient(a,b,p))<=eps and min(a[0],b[0])-eps<=p[0]<=max(a[0],b[0])+eps and min(a[1],b[1])-eps<=p[1]<=max(a[1],b[1])+eps

def segments_touch(a,b,c,d,eps):
    oo=[orient(a,b,c),orient(a,b,d),orient(c,d,a),orient(c,d,b)]
    return (oo[0]*oo[1]<-eps*eps and oo[2]*oo[3]<-eps*eps) or any((on_segment(a,b,c,eps),on_segment(a,b,d,eps),on_segment(c,d,a,eps),on_segment(c,d,b,eps)))

def projected_polygon(coords,face,eps):
    normal=face_vector(coords,face);require(norm(normal)>eps*eps,'zero-area polygon')
    axis=max(range(3),key=lambda i:abs(normal[i]))
    points=[tuple(c[k] for k in range(3) if k!=axis) for c in (coords[i] for i in face)]
    count=len(face)
    for i in range(count):
        for j in range(i+1,count):
            if j==i+1 or (i==0 and j==count-1):continue
            require(not segments_touch(points[i],points[(i+1)%count],points[j],points[(j+1)%count],eps*eps), 'self-intersecting polygon')
    return points,normal

def triangulate(coords,face,eps):
    pts,_=projected_polygon(coords,face,eps)
    area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(pts,pts[1:]+pts[:1]))
    sign=1 if area>0 else -1
    remaining=list(range(len(face)));out=[]
    while len(remaining)>3:
        for pos,b in enumerate(remaining):
            a,c=remaining[pos-1],remaining[(pos+1)%len(remaining)]
            if sign*orient(pts[a],pts[b],pts[c])<=eps*eps:continue
            if any(all(sign*orient(pts[u],pts[v],pts[k])>=-eps*eps for u,v in [(a,b),(b,c),(c,a)]) for k in remaining if k not in (a,b,c)):continue
            out.append([face[a],face[b],face[c]]);remaining.pop(pos);break
        else:raise Blocked('BLOCKED: polygon cannot be triangulated safely')
    out.append([face[k] for k in remaining]);return out

def incidence(faces):
    usage=defaultdict(list)
    for i,face in enumerate(faces):
        for a,b in zip(face,face[1:]+face[:1]):usage[tuple(sorted((a,b)))].append((i,a,b))
    return usage

def topology(faces, name='mesh'):
    usage=incidence(faces)
    require(all(len(v)<=2 for v in usage.values()),name+' has nonmanifold edges')
    require(all(len(v)!=2 or v[0][1:]!=v[1][1:] for v in usage.values()),name+' has inconsistent winding')
    links=defaultdict(lambda:defaultdict(set))
    for f in faces:
        for j,v in enumerate(f):
            a,b=f[j-1],f[(j+1)%len(f)]
            links[v][a].add(b);links[v][b].add(a)
    for neighbors in links.values():
        require(all(1<=len(n)<=2 for n in neighbors.values()),name+' has nonmanifold vertex link')
        todo=[next(iter(neighbors))];seen=set(todo)
        while todo:
            for v in neighbors[todo.pop()]-seen:seen.add(v);todo.append(v)
        require(len(seen)==len(neighbors),name+' has disconnected vertex link')
    return {e for e,v in usage.items() if len(v)==1}

def validate_plan(mesh, plan):
    mesh=canonical_mesh(mesh);coords,faces=mesh['vertices'],mesh['faces']
    require(isinstance(plan,dict),'plan must be an object')
    require(plan.get('schema')==SCHEMA,'unsupported plan schema; explicit /3 approval required')
    require(plan.get('mesh_sha256')==fingerprint(mesh),'source mesh fingerprint mismatch')
    loop=indices(plan.get('cut_loop'),len(coords),'cut_loop',6)
    removed=indices(plan.get('remove_faces'),len(faces),'remove_faces')
    mouth=set(indices(plan.get('mouth_vertices'),len(coords),'mouth_vertices',6))
    protected=set(indices(plan.get('protected_vertices'),len(coords),'protected_vertices'))
    require(protected==set(range(len(coords)))-mouth,'protected_vertices must be exact region complement')
    require(set(loop)<=mouth,'cut loop outside approved mouth region')
    require(all(set(faces[i])<=mouth for i in removed),'cut touches protected vertices')
    upper=indices(plan.get('upper_rim'),len(coords),'upper_rim',2)
    lower=indices(plan.get('lower_rim'),len(coords),'lower_rim',2)
    require(not set(upper)&set(lower) and set(upper)|set(lower)==set(loop),'upper/lower must partition loop')
    membership=[v in upper for v in loop]
    require(sum(a!=b for a,b in zip(membership,membership[1:]+membership[:1]))==2,'upper/lower must be contiguous arcs')
    require(plan.get('anatomy_reviewed') is True,'anatomical approval required')
    axis=plan.get('inward_axis')
    require(isinstance(axis,list) and len(axis)==3 and all(number(x) for x in axis),'invalid inward_axis')
    axis=unit(axis)
    ratio=plan.get('depth_ratio')
    require(number(ratio) and .02<=ratio<=.5,'depth_ratio outside 0.02..0.5')
    width=max(norm(sub(coords[a],coords[b])) for a in loop for b in loop)
    require(width>1e-8,'zero mouth width')
    eps=width*1e-7
    source_boundary=topology(faces,'source')
    usage=incidence(faces);expected={tuple(sorted((a,b))) for a,b in zip(loop,loop[1:]+loop[:1])}
    require(all(e in usage and len(usage[e])==2 for e in expected),'cut loop must follow interior edges')
    selected=set(removed);seen={removed[0]};todo=list(seen)
    adj=defaultdict(set)
    for users in usage.values():
        if len(users)==2:
            a,b=users[0][0],users[1][0]
            if a in selected and b in selected:adj[a].add(b);adj[b].add(a)
    while todo:
        for i in adj[todo.pop()]-seen:seen.add(i);todo.append(i)
    require(seen==selected,'selected faces are disconnected')
    patch=[faces[i] for i in removed];patch_usage=incidence(patch)
    patch_boundary={e for e,v in patch_usage.items() if len(v)==1}
    require(patch_boundary==expected,'selected patch boundary differs from cut loop')
    require(len({v for f in patch for v in f})-len(patch_usage)+len(patch)==1,'selected patch is not a disk')
    for a,b in zip(loop,loop[1:]+loop[:1]):
        require(patch_usage[tuple(sorted((a,b)))][0][1:]==(a,b),'cut_loop winding differs from removed faces')
    # Check only edited polygon geometry here; Blender validates/collides all
    # generated triangles against the retained mesh before making an object.
    vectors=[face_vector(coords,f) for f in patch]
    for f in patch:triangulate(coords,f,eps)
    surface=sum(norm(face_vector(coords,f)) for f in faces)
    require(sum(norm(v) for v in vectors)<=surface*MAX_REMOVED_SURFACE_FRACTION,'cut exceeds 10% protected source surface limit')
    normal=unit(tuple(sum(v[k] for v in vectors) for k in range(3)))
    require(dot(axis,normal)<-.5,'inward_axis points outward or tangentially')
    pts,rim_normal=projected_polygon(coords,loop,eps)
    center=tuple(sum(coords[i][k] for i in loop)/len(loop) for k in range(3))
    require(max(abs(dot(sub(coords[i],center),unit(rim_normal))) for i in loop)<=width*.02,'cut loop is too nonplanar for this cavity algorithm')
    dropped=max(range(3),key=lambda k:abs(rim_normal[k]));cp=tuple(center[k] for k in range(3) if k!=dropped)
    sign=1 if sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(pts,pts[1:]+pts[:1]))>0 else -1
    require(all(sign*orient(a,b,cp)>eps*eps for a,b in zip(pts,pts[1:]+pts[:1])),'cavity fan center outside polygon kernel')
    return dict(status='TOPOLOGY_CANDIDATE',schema=SCHEMA,mesh_sha256=fingerprint(mesh),safe_for_automatic_build=False,
                loop=loop,remove_faces=removed,axis=axis,depth=width*ratio,epsilon=eps,source_boundary=source_boundary)

def build_topology(mesh,plan):
    checked=validate_plan(mesh,plan);mesh=canonical_mesh(mesh)
    coords,faces=mesh['vertices'],mesh['faces'];removed=set(checked['remove_faces']);loop=checked['loop']
    retained_ids=[i for i in range(len(faces)) if i not in removed]
    used_before={v for f in faces for v in f};used_after={v for i in retained_ids for v in faces[i]}
    kept=[i for i in range(len(coords)) if i in used_after or i not in used_before]
    mapping={old:new for new,old in enumerate(kept)}
    out=[coords[i][:] for i in kept];provenance=[[(i,1.0)] for i in kept];original_ids=kept[:]
    outfaces=[[mapping[v] for v in faces[i]] for i in retained_ids]
    face_sources=retained_ids[:]
    inner=[]
    for i in loop:
        inner.append(len(out));out.append(list(add(coords[i],mul(checked['axis'],checked['depth']))))
        provenance.append([(i,1.0)]);original_ids.append(-1)
    center=tuple(sum(out[i][k] for i in inner)/len(inner) for k in range(3))
    back=len(out);out.append(list(add(center,mul(checked['axis'],checked['depth']*.2))))
    provenance.append([(i,1/len(loop)) for i in loop]);original_ids.append(-1)
    for i in range(len(loop)):
        j=(i+1)%len(loop)
        outfaces.append([mapping[loop[i]],mapping[loop[j]],inner[j],inner[i]]);face_sources.append(-1)
    for i in range(len(loop)):
        outfaces.append([inner[i],inner[(i+1)%len(loop)],back]);face_sources.append(-1)
    b=topology(outfaces,'result')
    remapped_boundary={tuple(sorted((mapping[a],mapping[c]))) for a,c in checked['source_boundary']}
    require(b==remapped_boundary,'result changed source boundary')
    for f in outfaces[len(retained_ids):]:
        require(norm(face_vector(out,f))>checked['epsilon']**2,'degenerate cavity face')
        triangulate(out,f,checked['epsilon'])
    return dict(vertices=out,faces=outfaces,original_ids=original_ids,provenance=provenance,
                face_sources=face_sources,retained_faces=len(retained_ids),epsilon=checked['epsilon'])

def main():
    p=argparse.ArgumentParser();p.add_argument('--mesh-json',required=True);p.add_argument('--plan',required=True);p.add_argument('--report',required=True)
    a=p.parse_args();report={'status':'BLOCKED','safe_for_automatic_build':False}
    try:
        r=validate_plan(json.loads(Path(a.mesh_json).read_text()),json.loads(Path(a.plan).read_text()))
        report={k:v for k,v in r.items() if k not in ('source_boundary',)}
    except (ValueError,TypeError,KeyError,IndexError,OverflowError,OSError) as e:report['reason']=str(e)
    output=Path(a.report);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n');print(json.dumps(report))
    return 0 if report['status']=='TOPOLOGY_CANDIDATE' else 2

if __name__=='__main__':raise SystemExit(main())
