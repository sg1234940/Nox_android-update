#!/usr/bin/env python3
"""Real Blender integration tests; run with blender --background --python ... .

No input asset is changed. A temporary synthetic GLB is exported/reimported and
deleted. An optional real GLB is imported for preservation/rejection checks only;
an anatomical /3 cut plan is never guessed.
"""
import copy
import hashlib
import json
import math
import sys
import struct
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
from iskra_mouth_builder_v3 import build,dispose,mesh_fingerprint,inspect_supported_data,check_collisions
from iskra_mouth_cut_plan import Blocked,build_topology,topology
from test_iskra_mouth_cut_plan import fixture

class BlenderTests(unittest.TestCase):
    def setUp(self):
        source,self.plan=fixture()
        self.mesh=bpy.data.meshes.new('MOUTH_TEST_SOURCE')
        self.mesh.from_pydata(source['vertices'],[],source['faces']);self.mesh.update()
        self.obj=bpy.data.objects.new('MOUTH_TEST_SOURCE',self.mesh);bpy.context.collection.objects.link(self.obj)
        self.plan['mesh_sha256']=mesh_fingerprint(self.obj)
        for name in ['UVMap','UVSecond']:
            uv=self.mesh.uv_layers.new(name=name)
            for i,v in enumerate(uv.data):v.uv=(i*.01,i*.02)
        self.material=bpy.data.materials.new('MOUTH_TEST_SKIN');self.mesh.materials.append(self.material)
        for name in ['Head','Jaw']:self.obj.vertex_groups.new(name=name)
        for i in range(len(self.mesh.vertices)):
            self.obj.vertex_groups['Head'].add([i],.7,'REPLACE');self.obj.vertex_groups['Jaw'].add([i],.3,'REPLACE')
        self.result=None
    def tearDown(self):
        if self.result is not None:dispose(self.result)
        bpy.data.objects.remove(self.obj,do_unlink=True)
        if self.mesh.users==0:bpy.data.meshes.remove(self.mesh)
        if self.material.users==0:bpy.data.materials.remove(self.material)
    def states(self):return (set(bpy.data.objects),set(bpy.data.meshes),set(bpy.data.materials),set(bpy.data.shape_keys))
    def test_valid_topology_and_exact_transfer(self):
        source=mesh_fingerprint(self.obj)
        self.result,report=build(self.obj,self.plan)
        self.assertEqual(report['status'],'CANDIDATE_VALIDATED');self.assertEqual(report['new_vertices'],7)
        self.assertEqual(mesh_fingerprint(self.obj),source)
        self.assertEqual(len(self.result.data.uv_layers),2)
        self.assertEqual(self.result.data.materials[0],self.material)
        for polygon,old in zip(self.result.data.polygons,report['face_sources']):
            if old<0:
                points=[self.result.data.uv_layers[0].data[i].uv for i in polygon.loop_indices]
                area=abs(sum(a.x*b.y-b.x*a.y for a,b in zip(points,points[1:]+points[:1])))/2
                self.assertGreater(area,1e-6)
        for v in self.result.data.vertices:
            self.assertAlmostEqual(sum(g.weight for g in v.groups),1,places=6)
    def test_preserve_21_relative_shape_keys_and_action(self):
        names=['eyeBlinkLeft','eyeBlinkRight','eyesLeft','eyesRight','eyesUp','eyesDown','browInnerUp','browDownLeft','browDownRight','jawOpen','mouthSmile','mouthFrown','mouthFunnel','mouthPucker','viseme_MBP','viseme_FV','viseme_A','viseme_E','viseme_I','viseme_O','viseme_U']
        self.obj.shape_key_add(name='Basis')
        for n,name in enumerate(names):
            key=self.obj.shape_key_add(name=name)
            # Small nonzero mouth displacement remains collision-free.
            for i in range(6):key.data[i].co.z+=(n+1)*1e-5
        key=self.obj.data.shape_keys.key_blocks['jawOpen'];key.value=.5;key.keyframe_insert('value',frame=1);key.value=0
        original_action=self.mesh.shape_keys.animation_data.action
        self.result,report=build(self.obj,self.plan)
        self.assertEqual(report['preserved_shape_keys'],['Basis']+names)
        self.assertEqual(self.result.data.shape_keys.animation_data.action,original_action)
        self.assertEqual(self.result.data.shape_keys.key_blocks['jawOpen'].relative_key.name,'Basis')
    def test_failure_has_no_leaked_datablocks(self):
        before=self.states()
        with self.assertRaises(Blocked):build(self.obj,dict(self.plan,inward_axis=[0,0,1]))
        self.assertEqual(before,self.states())
    def test_failure_after_mesh_creation_cleans_all(self):
        # RNA failure after allocating mesh/material: test the transaction,
        # not only preflight rejection. verify_transfer is fault injected.
        import iskra_mouth_builder_v3 as builder
        original=builder.verify_transfer;before=self.states()
        def fail(*args):raise Blocked('BLOCKED: injected post-transfer fault')
        try:
            builder.verify_transfer=fail
            with self.assertRaises(Blocked):build(self.obj,self.plan)
        finally:builder.verify_transfer=original
        self.assertEqual(before,self.states())
    def test_reverse_winding_BLOCKED(self):
        with self.assertRaisesRegex(Blocked,'winding'):build(self.obj,dict(self.plan,cut_loop=list(reversed(range(6)))))
    def test_thin_shell_collision_BLOCKED(self):
        source,plan=fixture(.1);topo=build_topology(source,plan)
        with self.assertRaisesRegex(Blocked,'collision'):check_collisions(topo['vertices'],topo)
    def test_new_vertex_identity_mutation_detected(self):
        import iskra_mouth_builder_v3 as builder
        self.result,report=build(self.obj,self.plan)
        topo=build_topology({'vertices':[list(v.co) for v in self.obj.data.vertices], 'faces':[list(p.vertices) for p in self.obj.data.polygons]},self.plan)
        topo['original_ids'][0],topo['original_ids'][1]=topo['original_ids'][1],topo['original_ids'][0]
        with self.assertRaises(Blocked):builder.verify_transfer(self.obj,self.result,topo)
    def test_mutation_collision_check_killed(self):
        # Real BVH output plus one removed safety statement.
        import iskra_mouth_builder_v3 as builder
        original=builder.require
        mesh,plan=fixture(.1);topo=build_topology(mesh,plan)
        with self.assertRaises(Blocked):builder.check_collisions(topo['vertices'],topo)
        try:
            builder.require=lambda value,reason:None if reason.endswith(': cavity collision') else original(value,reason)
            builder.check_collisions(topo['vertices'],topo)
        finally:builder.require=original
    def test_unsupported_drivers_BLOCKED_without_deleting_mimic(self):
        self.obj.shape_key_add(name='Basis');key=self.obj.shape_key_add(name='jawOpen');key.driver_add('value')
        before=list(self.mesh.shape_keys.key_blocks)
        with self.assertRaisesRegex(Blocked,'drivers'):build(self.obj,self.plan)
        self.assertEqual(before,list(self.mesh.shape_keys.key_blocks))
    def test_GLB_roundtrip_21_targets_and_seven_body_clips(self):
        rig_data=bpy.data.armatures.new('MOUTH_TEST_RIG')
        rig=bpy.data.objects.new('MOUTH_TEST_RIG',rig_data);bpy.context.collection.objects.link(rig)
        imported=[]
        try:
            bpy.ops.object.select_all(action='DESELECT');rig.select_set(True)
            bpy.context.view_layer.objects.active=rig;bpy.ops.object.mode_set(mode='EDIT')
            head=rig_data.edit_bones.new('Head');head.head=(0,0,0);head.tail=(0,0,1)
            jaw=rig_data.edit_bones.new('Jaw');jaw.head=(0,0,1);jaw.tail=(0,0,1.5);jaw.parent=head
            bpy.ops.object.mode_set(mode='OBJECT')
            self.obj.parent=rig
            self.obj.modifiers.new('Armature','ARMATURE').object=rig
            rig.animation_data_create()
            for i in range(7):
                action=bpy.data.actions.new('BodyClip_'+str(i));rig.animation_data.action=action
                bone=rig.pose.bones['Head'];bone.rotation_mode='XYZ'
                bone.rotation_euler.z=0;bone.keyframe_insert('rotation_euler',frame=1)
                bone.rotation_euler.z=(i+1)*.03;bone.keyframe_insert('rotation_euler',frame=10)
                track=rig.animation_data.nla_tracks.new();track.name='BodyClip_'+str(i)
                track.strips.new(action.name,1,action)
            rig.animation_data.action=None;rig.pose.bones['Head'].rotation_euler.z=0
            self.obj.shape_key_add(name='Basis')
            names=['eyeBlinkLeft','eyeBlinkRight','eyesLeft','eyesRight','eyesUp','eyesDown',
                   'browInnerUp','browDownLeft','browDownRight','jawOpen','mouthSmile','mouthFrown',
                   'mouthFunnel','mouthPucker','viseme_MBP','viseme_FV','viseme_A','viseme_E',
                   'viseme_I','viseme_O','viseme_U']
            for i,name in enumerate(names):
                key=self.obj.shape_key_add(name=name)
                for j in range(6):key.data[j].co.z+=(i+1)*1e-5
            bones_before=[(b.name,tuple(tuple(r) for r in b.matrix_local)) for b in rig_data.bones]
            self.result,report=build(self.obj,self.plan)
            self.assertEqual(bones_before,[(b.name,tuple(tuple(r) for r in b.matrix_local)) for b in rig_data.bones])
            bpy.ops.object.select_all(action='DESELECT');self.result.select_set(True);rig.select_set(True)
            with tempfile.TemporaryDirectory() as d:
                output=Path(d)/'candidate.glb'
                bpy.ops.export_scene.gltf(filepath=str(output),export_format='GLB',use_selection=True,
                    export_morph=True,export_skins=True,export_animations=True,export_animation_mode='NLA_TRACKS')
                data=output.read_bytes();length=struct.unpack_from('<I',data,12)[0];doc=json.loads(data[20:20+length])
                self.assertEqual(len(doc['animations']),7)
                self.assertEqual({a['name'] for a in doc['animations']},{'BodyClip_'+str(i) for i in range(7)})
                binary_start=20+length+8
                def vectors(index):
                    a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']]
                    self.assertEqual(a['componentType'],5126)
                    size={'VEC4':4,'SCALAR':1}[a['type']]
                    start=binary_start+v.get('byteOffset',0)+a.get('byteOffset',0)
                    stride=v.get('byteStride',size*4)
                    return [struct.unpack_from('<'+'f'*size,data,start+i*stride) for i in range(a['count'])]
                head=next(i for i,n in enumerate(doc['nodes']) if n.get('name')=='Head')
                for animation in doc['animations']:
                    channel=next(c for c in animation['channels'] if c['target']=={'node':head,'path':'rotation'})
                    sampler=animation['samplers'][channel['sampler']]
                    q=vectors(sampler['output']);angle=2*math.acos(min(1,abs(sum(a*b for a,b in zip(q[0],q[-1])))))
                    clip=int(animation['name'].split('_')[-1])
                    self.assertAlmostEqual(angle,(clip+1)*.03,places=4)
                    times=vectors(sampler['input'])
                    self.assertGreater(times[-1][0],times[0][0])
                actual=next(m for m in doc['meshes'] if m.get('extras',{}).get('targetNames')==names)
                self.assertTrue(all(len(p['targets'])==21 for p in actual['primitives']))
                self.assertEqual(len(doc['skins']),1)
                before=set(bpy.data.objects);bpy.ops.import_scene.gltf(filepath=str(output))
                imported=list(set(bpy.data.objects)-before)
                target=next(o for o in imported if o.type=='MESH' and o.data.shape_keys)
                self.assertEqual(list(target.data.shape_keys.key_blocks.keys()),['Basis']+names)
                self.assertTrue(any(m.type=='ARMATURE' for m in target.modifiers))
                self.assertEqual(len(target.data.uv_layers),2)
        finally:
            if bpy.context.object and bpy.context.object.mode!='OBJECT':bpy.ops.object.mode_set(mode='OBJECT')
            for obj in imported:
                data=obj.data;bpy.data.objects.remove(obj,do_unlink=True)
                if isinstance(data,bpy.types.Mesh) and data.users==0:bpy.data.meshes.remove(data)
                elif isinstance(data,bpy.types.Armature) and data.users==0:bpy.data.armatures.remove(data)
            bpy.data.objects.remove(rig,do_unlink=True)
            if rig_data.users==0:bpy.data.armatures.remove(rig_data)


def real_asset_report(path):
    before=set(bpy.data.objects);before_actions=set(bpy.data.actions);binary=Path(path).read_bytes();digest=hashlib.sha256(binary).hexdigest()
    bpy.ops.import_scene.gltf(filepath=str(Path(path).resolve()))
    objects=set(bpy.data.objects)-before;meshes=[o for o in objects if o.type=='MESH' and any(m.type=='ARMATURE' for m in o.modifiers)];assert len(meshes)==1, [(o.name,o.type) for o in objects]
    obj=meshes[0];identity=mesh_fingerprint(obj)
    try:inspect_supported_data(obj);support='SUPPORTED'
    except Blocked as e:support=str(e)
    try:topology([list(p.vertices) for p in obj.data.polygons]);topology_status='MANIFOLD_WITH_ALLOWED_BOUNDARIES'
    except Blocked as e:topology_status=str(e)
    source={'status':'SOURCE_INSPECTED_NO_ANATOMICAL_CUT_PLAN','input_sha256':digest,'mesh_sha256':identity,
            'vertices':len(obj.data.vertices),'polygons':len(obj.data.polygons),'uv_layers':list(obj.data.uv_layers.keys()),
            'shape_keys':list(obj.data.shape_keys.key_blocks.keys()) if obj.data.shape_keys else [],
            'Head':any('Head' in o.data.bones for o in objects if o.type=='ARMATURE'),
            'actions':sorted(a.name for a in set(bpy.data.actions)-before_actions),'data_support':support,'source_topology':topology_status}
    try:build(obj,{})
    except Blocked:pass
    else:raise AssertionError('Missing real anatomical plan must BLOCK')
    assert mesh_fingerprint(obj)==identity
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==digest
    return source

if __name__=='__main__':
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    # Isolation only in this synthetic test process, never in builder main().
    bpy.ops.wm.read_factory_settings(use_empty=True)
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(BlenderTests)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'blender':bpy.app.version_string,'tests_run':result.testsRun,'failures':len(result.failures),'errors':len(result.errors)}
    if args:report['real_asset']=real_asset_report(args[0])
    print('MOUTH_V3_TEST_REPORT',json.dumps(report,indent=2))
    if len(args)>1:Path(args[1]).write_text(json.dumps(report,indent=2)+'\n')
    raise SystemExit(0 if result.wasSuccessful() else 1)
