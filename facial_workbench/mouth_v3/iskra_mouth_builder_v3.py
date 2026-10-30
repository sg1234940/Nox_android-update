#!/usr/bin/env python3
"""Transactional mouth candidate builder. No GLB export or production writes.

Uses immutable source IDs and a fresh mesh, not mutable BMesh index tables.
Preserves retained corners, skin weights and relative shape keys. Validates
cavity collisions on Basis and each existing target before returning a copy.
A candidate is NOT visual/Android/GLB validation. main() only writes a report.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent))
from iskra_mouth_cut_plan import Blocked,require,canonical_mesh,fingerprint,build_topology,triangulate,norm,face_vector
from iskra_mouth_geometry import illegal_intersection
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree


def mesh_data(obj):
    return {'vertices':[[float(c) for c in v.co] for v in obj.data.vertices],
            'faces':[list(p.vertices) for p in obj.data.polygons]}

def mesh_fingerprint(obj):return fingerprint(mesh_data(obj))


def check_collisions(coords,topology,label='Basis'):
    require(all(math.isfinite(c) for p in coords for c in p),label+': non-finite geometry')
    triangles=[];owners=[]
    for i,f in enumerate(topology['faces']):
        # The source may contain tiny pre-existing triangles away from the mouth;
        # BVH sees them, but the edit does not authorize repairing those faces.
        if len(f)==3:
            tris=[f]
        else:
            tris=triangulate(coords,f,topology['epsilon'])
        for t in tris:triangles.append(t);owners.append(i)
    for t,i in zip(triangles,owners):
        if topology['face_sources'][i]<0:
            require(norm(face_vector(coords,t))>topology['epsilon']**2,label+': zero-area cavity triangle')
    tree=BVHTree.FromPolygons([Vector(p) for p in coords],triangles,all_triangles=True,epsilon=topology['epsilon'])
    for a,b in tree.overlap(tree):
        if a>=b or owners[a]==owners[b]:continue
        if topology['face_sources'][owners[a]]>=0 and topology['face_sources'][owners[b]]>=0:continue
        require(not illegal_intersection(coords,triangles[a],triangles[b],topology['epsilon']),label+': cavity collision')


def migrated_positions(source,basis,topology):
    return [[p[k]+sum((source[i][k]-basis[i][k])*w for i,w in origins) for k in range(3)]
            for p,origins in zip(topology['vertices'],topology['provenance'])]


def copy_animation(source,dest):
    """Preserve relative-key animation by data path; unsupported constructs block.

    glTF-imported morphs normally have actions but no drivers. Drivers need an
    explicit migration of self-references and cannot be silently discarded.
    """
    ad=source.animation_data
    if ad is None:return
    require(not ad.drivers,'shape-key drivers need explicit verified migration')
    require(not ad.nla_tracks,'shape-key NLA tracks need explicit verified migration')
    da=dest.animation_data_create();da.action=ad.action
    if hasattr(ad,'action_slot') and ad.action_slot is not None:da.action_slot=ad.action_slot
    for attr in ('action_blend_type','action_extrapolation','action_influence','use_nla'):
        if hasattr(ad,attr):setattr(da,attr,getattr(ad,attr))


def inspect_supported_data(obj):
    require(obj.type=='MESH','expected mesh object')
    require(obj.mode=='OBJECT','builder requires Object mode')
    mesh=obj.data
    require(not any(e.is_loose for e in mesh.edges),'source wire edges require explicit transfer')
    allowed_uv={u.name for u in mesh.uv_layers}
    unsupported=[a.name for a in mesh.attributes if not a.is_internal and a.name not in allowed_uv and a.name not in {'position','sharp_face','sharp_edge','material_index'}]
    require(not unsupported,'unsupported source attributes: '+','.join(unsupported))
    require(not mesh.color_attributes,'color attributes require explicit transfer')
    require(not obj.constraints,'object constraints require explicit validation')
    require(all(m.type=='ARMATURE' for m in obj.modifiers),'unsupported mesh modifier')
    require(len(obj.modifiers)<=1,'multiple armatures require explicit migration')
    for m in obj.modifiers:
        require(m.object is not None,'armature modifier has no rig')
        require('Head' in m.object.data.bones,'expected Head skeleton')
    if mesh.shape_keys:
        keys=mesh.shape_keys
        require(keys.use_relative,'absolute shape keys require explicit migration')
        require(not keys.animation_data or (not keys.animation_data.drivers and not keys.animation_data.nla_tracks),'shape-key drivers/NLA require explicit migration')
        require(all(len(k.data)==len(mesh.vertices) for k in keys.key_blocks),'incomplete source shape key')
        require(all(not k.vertex_group for k in keys.key_blocks),'masked shape keys require explicit verified migration')


def dispose(result):
    if result is None:return
    mesh=result.data
    owned=[m for m in mesh.materials if m and m.get('ISKRA_MOUTH_V3_OWNED') is True]
    bpy.data.objects.remove(result,do_unlink=True)
    if mesh.users==0:bpy.data.meshes.remove(mesh)
    for material in owned:
        if material.users==0:bpy.data.materials.remove(material)


def build(obj,plan):
    inspect_supported_data(obj)
    source=mesh_data(obj);topo=build_topology(source,plan);basis=source['vertices']
    check_collisions(topo['vertices'],topo)
    shapes={}
    keys=obj.data.shape_keys
    if keys:
        for key in keys.key_blocks:
            positions=migrated_positions([list(v.co) for v in key.data],basis,topo)
            check_collisions(positions,topo,key.name)
            shapes[key.name]=positions
        # Include actual mixed pose, rather than validating only isolated targets.
        combined=[p[:] for p in basis]
        for key in list(keys.key_blocks)[1:]:
            relative=key.relative_key
            for i in range(len(combined)):
                for k in range(3):combined[i][k]+=(key.data[i].co[k]-relative.data[i].co[k])*key.value
        check_collisions(migrated_positions(combined,basis,topo),topo,'current combined pose')
    result=None;mesh=None;material=None
    try:
        mesh=bpy.data.meshes.new(obj.data.name+'_MOUTH_V3_CANDIDATE')
        mesh.from_pydata(topo['vertices'],[],topo['faces']);mesh.update()
        result=obj.copy();result.data=mesh;result.name=obj.name+'_MOUTH_V3_CANDIDATE'
        bpy.context.collection.objects.link(result)
        # Original materials remain the same data blocks and slot order.
        for mat in obj.data.materials:mesh.materials.append(mat)
        require(all(s.link=='DATA' for s in obj.material_slots),'object-linked material slots need explicit migration')
        material=bpy.data.materials.new('ISKRA_MOUTH_V3_INTERIOR')
        material['ISKRA_MOUTH_V3_OWNED']=True
        material.use_nodes=True
        principled=material.node_tree.nodes.get('Principled BSDF')
        principled.inputs['Base Color'].default_value=(.025,.006,.008,1)
        principled.inputs['Roughness'].default_value=.8
        mesh.materials.append(material);interior_slot=len(mesh.materials)-1
        for polygon,old in zip(mesh.polygons,topo['face_sources']):
            if old>=0:
                polygon.material_index=obj.data.polygons[old].material_index
                polygon.use_smooth=obj.data.polygons[old].use_smooth
            else:polygon.material_index=interior_slot;polygon.use_smooth=True
        old_edges={tuple(sorted(e.vertices)):e for e in obj.data.edges}
        for edge in mesh.edges:
            ids=tuple(sorted(topo['original_ids'][i] for i in edge.vertices))
            if -1 not in ids and ids in old_edges:edge.use_edge_sharp=old_edges[ids].use_edge_sharp
        ring_size=len(plan['cut_loop'])
        for old_uv in obj.data.uv_layers:
            uv=mesh.uv_layers.new(name=old_uv.name,do_init=False)
            uv.active_render=old_uv.active_render
            for polygon,old in zip(mesh.polygons,topo['face_sources']):
                if old>=0:
                    for dst,src in zip(polygon.loop_indices,obj.data.polygons[old].loop_indices):uv.data[dst].uv=old_uv.data[src].uv
                else:
                    # Interior uses its own untextured material; nonzero UV chart
                    # is independent of the retained skin's islands and seams.
                    local=polygon.index-topo['retained_faces']
                    if local<ring_size:
                        u0,u1=local/ring_size,(local+1)/ring_size
                        points=[(u0,0),(u1,0),(u1,1),(u0,1)]
                    else:
                        i=local-ring_size
                        angle=lambda n:(.5+.45*math.cos(2*math.pi*n/ring_size),.5+.45*math.sin(2*math.pi*n/ring_size))
                        points=[angle(i),angle((i+1)%ring_size),(.5,.5)]
                    for dst,point in zip(polygon.loop_indices,points):uv.data[dst].uv=point
            uv.active=old_uv.active
        if obj.data.has_custom_normals:
            normals=[]
            for polygon,old in zip(mesh.polygons,topo['face_sources']):
                normals.extend([tuple(obj.data.corner_normals[i].vector) for i in obj.data.polygons[old].loop_indices] if old>=0 else [(0,0,0)]*len(polygon.vertices))
            mesh.normals_split_custom_set(normals)
        # Replacing Object.data can clear group definitions in Blender.
        result.vertex_groups.clear()
        for group in obj.vertex_groups:
            copied=result.vertex_groups.new(name=group.name);copied.lock_weight=group.lock_weight
        bone_groups={g.index for g in obj.vertex_groups if obj.modifiers and g.name in obj.modifiers[0].object.data.bones}
        for i,origins in enumerate(topo['provenance']):
            weights={}
            for old,w in origins:
                for g in obj.data.vertices[old].groups:weights[g.group]=weights.get(g.group,0)+w*g.weight
            if topo['original_ids'][i]<0 and obj.modifiers:
                influences=[w for g,w in weights.items() if g in bone_groups and w>1e-8]
                require(1<=len(influences)<=8,'new vertex exceeds verified 8-influence skin contract')
                require(abs(sum(influences)-1)<1e-3,'new vertex has unnormalized bone weights')
            for group,value in weights.items():result.vertex_groups[group].add([i],value,'REPLACE')
        if keys:
            for source_key in keys.key_blocks:
                key=result.shape_key_add(name=source_key.name,from_mix=False)
                for v,p in zip(key.data,shapes[source_key.name]):v.co=p
                key.slider_min=source_key.slider_min;key.slider_max=source_key.slider_max
                key.value=source_key.value;key.mute=source_key.mute;key.interpolation=source_key.interpolation
                key.vertex_group=source_key.vertex_group
            for old in keys.key_blocks:result.data.shape_keys.key_blocks[old.name].relative_key=result.data.shape_keys.key_blocks[old.relative_key.name]
            for k in keys.keys():result.data.shape_keys[k]=keys[k]
            copy_animation(keys,result.data.shape_keys)
        result.active_shape_key_index=obj.active_shape_key_index
        # Keep metadata that does not refer to the old vertex/face ordering.
        for k in obj.data.keys():mesh[k]=obj.data[k]
        verify_transfer(obj,result,topo)
        report=dict(status='CANDIDATE_VALIDATED',scope='topology/attributes; not GLB or Android or visual approval',
                    mesh_sha256=fingerprint(source),result_mesh_sha256=mesh_fingerprint(result),
                    result_to_original=topo['original_ids'],face_sources=topo['face_sources'],
                    new_vertices=sum(i<0 for i in topo['original_ids']),removed_faces=len(plan['remove_faces']),
                    preserved_shape_keys=list(shapes),glb_exported=False)
        return result,report
    except Exception:
        if result is not None:dispose(result)
        elif mesh is not None and mesh.users==0:bpy.data.meshes.remove(mesh)
        if material is not None:
            try:
                if material.users==0:bpy.data.materials.remove(material)
            except ReferenceError:pass
        raise


def verify_transfer(source,result,topo):
    mesh=result.data
    require(len(mesh.vertices)==len(topo['vertices']) and len(mesh.polygons)==len(topo['faces']),'unexpected reconstructed topology')
    for i,old in enumerate(topo['original_ids']):
        if old<0:continue
        require((mesh.vertices[i].co-source.data.vertices[old].co).length<1e-7,'source vertex identity/position changed')
        a={g.group:g.weight for g in source.data.vertices[old].groups};b={g.group:g.weight for g in mesh.vertices[i].groups}
        require(a.keys()==b.keys() and all(abs(a[k]-b[k])<1e-6 for k in a),'retained vertex weights changed')
    for polygon,old in zip(mesh.polygons,topo['face_sources']):
        if old<0:continue
        require([topo['original_ids'][i] for i in polygon.vertices]==list(source.data.polygons[old].vertices),'protected face winding/identity changed')
        require(polygon.material_index==source.data.polygons[old].material_index,'retained material slot changed')
        for layer in source.data.uv_layers:
            for dst,src in zip(polygon.loop_indices,source.data.polygons[old].loop_indices):
                require((mesh.uv_layers[layer.name].data[dst].uv-layer.data[src].uv).length<1e-7,'retained UV corner changed')
    source_keys=source.data.shape_keys;result_keys=mesh.shape_keys
    require((list(source_keys.key_blocks.keys()) if source_keys else [])==(list(result_keys.key_blocks.keys()) if result_keys else []),'shape key names/order changed')
    if source_keys:
        for old,key in zip(source_keys.key_blocks,result_keys.key_blocks):
            for i,origin in enumerate(topo['original_ids']):
                if origin>=0:require((old.data[origin].co-key.data[i].co).length<1e-7,'retained shape key delta changed')
    for a,b in zip(source.modifiers,result.modifiers):require(a.type==b.type and a.object==b.object,'rig binding changed')


def main():
    p=argparse.ArgumentParser();p.add_argument('--input',required=True);p.add_argument('--plan',required=True);p.add_argument('--report',required=True)
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    opt=p.parse_args(args);report={'status':'BLOCKED','glb_exported':False};result=None
    # Headless-only CLI: never clear an artist's open scene.
    try:
        require(bpy.app.background,'CLI requires Blender background mode')
        plan=json.loads(Path(opt.plan).read_text())
        require(isinstance(plan,dict),'plan must be an object')
        before=set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=str(Path(opt.input).resolve()))
        imported=set(bpy.data.objects)-before
        # The glTF importer also creates an Icosphere bone-display helper.
        if plan.get('source_mesh'):
            meshes=[o for o in imported if o.type=='MESH' and o.name==plan['source_mesh']]
        else:
            meshes=[o for o in imported if o.type=='MESH' and any(m.type=='ARMATURE' for m in o.modifiers)]
        require(len(meshes)==1,'expected exactly one input mesh')
        result,report=build(meshes[0],plan)
        report['input_sha256']=hashlib.sha256(Path(opt.input).read_bytes()).hexdigest()
    except (Blocked,ValueError,TypeError,KeyError,IndexError,OverflowError,OSError) as exc:
        report.update(status='BLOCKED',glb_exported=False,reason=str(exc))
    except Exception as exc:
        report.update(status='BLOCKED',glb_exported=False,reason='internal error: '+type(exc).__name__+': '+str(exc))
    finally:
        if result is not None:
            dispose(result)
        report_path=Path(opt.report);report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps(report));return 0 if report['status']=='CANDIDATE_VALIDATED' else 2

if __name__=='__main__':raise SystemExit(main())
