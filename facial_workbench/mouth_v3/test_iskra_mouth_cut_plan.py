import ast
import copy
import json
import math
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from iskra_mouth_cut_plan import (SCHEMA,Blocked,validate_plan,build_topology,fingerprint,topology,incidence,triangulate)
from iskra_mouth_geometry import illegal_intersection


def fixture(thickness=2):
    r=[[math.cos(i*math.pi/3),math.sin(i*math.pi/3),0] for i in range(6)]
    vertices=r+[[3*x,3*y,0] for x,y,z in r]+[[3*x,3*y,-thickness] for x,y,z in r]+[[0,0,0]]
    faces=[[18,i,(i+1)%6] for i in range(6)]
    faces += [[i,6+i,6+(i+1)%6,(i+1)%6] for i in range(6)]
    faces += [[6+i,12+i,12+(i+1)%6,6+(i+1)%6] for i in range(6)]
    faces += [list(range(17,11,-1))]
    mesh=dict(vertices=vertices,faces=faces)
    plan=dict(schema=SCHEMA,mesh_sha256=fingerprint(mesh),cut_loop=list(range(6)),remove_faces=list(range(6)),
              mouth_vertices=list(range(6))+[18],protected_vertices=list(range(6,18)),
              upper_rim=[0,1,2],lower_rim=[3,4,5],anatomy_reviewed=True,inward_axis=[0,0,-1],depth_ratio=.18)
    return mesh,plan

class CutPlanTests(unittest.TestCase):
    def setUp(self):self.mesh,self.plan=fixture()
    def blocked(self,**changes):
        with self.assertRaisesRegex(Blocked,'BLOCKED:'):validate_plan(self.mesh,dict(self.plan,**changes))
    def test_valid_candidate_not_automatic_authorization(self):
        r=validate_plan(self.mesh,self.plan)
        self.assertEqual(r['status'],'TOPOLOGY_CANDIDATE');self.assertFalse(r['safe_for_automatic_build'])
    def test_wrong_sha(self):self.blocked(mesh_sha256='wrong')
    def test_old_schema(self):self.blocked(schema='ISKRA_MOUTH_CUT_PLAN/2')
    def test_repeated_loop(self):self.blocked(cut_loop=[0,1,2,3,4,0])
    def test_repeated_faces(self):self.blocked(remove_faces=[0,0])
    def test_bad_order(self):self.blocked(cut_loop=[0,2,1,3,4,5])
    def test_reversed_winding(self):self.blocked(cut_loop=list(reversed(range(6))))
    def test_disconnected_faces(self):self.blocked(remove_faces=[0,3])
    def test_outward_axis(self):self.blocked(inward_axis=[0,0,1])
    def test_tangent_axis(self):self.blocked(inward_axis=[1,0,0])
    def test_zero_axis(self):self.blocked(inward_axis=[0,0,0])
    def test_nan_axis(self):self.blocked(inward_axis=[float('nan'),0,0])
    def test_infinity(self):self.blocked(inward_axis=[float('inf'),0,0])
    def test_huge_integer(self):self.blocked(inward_axis=[10**1000,0,0])
    def test_nested_loop(self):self.blocked(cut_loop=[[0],1,2,3,4,5])
    def test_nested_faces(self):self.blocked(remove_faces=[[0]])
    def test_boolean_index(self):self.blocked(cut_loop=[False,1,2,3,4,5])
    def test_negative_index(self):self.blocked(cut_loop=[-1,1,2,3,4,5])
    def test_empty_faces(self):self.blocked(remove_faces=[])
    def test_bad_face_range(self):self.blocked(remove_faces=[999])
    def test_missing_axis(self):self.blocked(inward_axis=None)
    def test_no_review(self):self.blocked(anatomy_reviewed=False)
    def test_missing_depth(self):self.blocked(depth_ratio=None)
    def test_bad_depth(self):self.blocked(depth_ratio=.51)
    def test_nan_depth(self):self.blocked(depth_ratio=float('nan'))
    def test_wrong_protected_set(self):self.blocked(protected_vertices=[6])
    def test_touch_protected_vertex(self):self.blocked(mouth_vertices=list(range(6)),protected_vertices=list(range(6,19)))
    def test_wrong_rim_partition(self):self.blocked(lower_rim=[2,3,4,5])
    def test_scattered_upper_lower(self):self.blocked(upper_rim=[0,2,4],lower_rim=[1,3,5])
    def test_null_plan(self):
        with self.assertRaises(Blocked):validate_plan(self.mesh,None)
    def test_numeric_canonicalization(self):
        mesh=copy.deepcopy(self.mesh);mesh['vertices'][18]=[0.0,-0.0,0.0]
        self.assertEqual(fingerprint(mesh),fingerprint(self.mesh))
    def test_open_source_boundary_may_remain(self):
        self.mesh['faces'].pop();self.plan['mesh_sha256']=fingerprint(self.mesh)
        r=build_topology(self.mesh,self.plan)
        self.assertEqual(len(topology(r['faces'])),6)
    def test_not_allowed_to_reuse_existing_opening(self):
        self.mesh['faces']=self.mesh['faces'][6:];self.plan['mesh_sha256']=fingerprint(self.mesh)
        with self.assertRaises(Blocked):validate_plan(self.mesh,self.plan)
    def test_new_hole_selection_blocked(self):self.blocked(remove_faces=[0,1,2,3,4])
    def test_zero_area_source_patch(self):
        self.mesh['vertices'][18]=self.mesh['vertices'][0][:];self.plan['mesh_sha256']=fingerprint(self.mesh)
        with self.assertRaises(Blocked):validate_plan(self.mesh,self.plan)
    def test_vertex_nonmanifold(self):
        with self.assertRaises(Blocked):topology([[0,1,2],[0,3,4]])
    def test_inconsistent_source_winding(self):
        self.mesh['faces'][8].reverse();self.plan['mesh_sha256']=fingerprint(self.mesh)
        with self.assertRaises(Blocked):validate_plan(self.mesh,self.plan)
    def test_large_complement_not_mouth(self):
        # Removing every face except the source mouth patch would retain only
        # that patch. It is rejected without an authorizing region expansion.
        self.blocked(remove_faces=list(range(6,len(self.mesh['faces']))))
    def test_surface_limit_is_not_overridable_by_plan(self):
        coords=self.mesh['vertices'][:6]+[[0,0,1],[0,0,-1]]
        faces=[[6,i,(i+1)%6] for i in range(6)]+[[7,(i+1)%6,i] for i in range(6)]
        mesh=dict(vertices=coords,faces=faces)
        plan=dict(self.plan,mesh_sha256=fingerprint(mesh),mouth_vertices=list(range(7)),
                  protected_vertices=[7],max_removed_surface_fraction=1.0)
        with self.assertRaisesRegex(Blocked,'10%'):validate_plan(mesh,plan)
    def test_geometric_self_intersection_BLOCKED(self):
        self.mesh['vertices'][0],self.mesh['vertices'][2]=self.mesh['vertices'][2],self.mesh['vertices'][0]
        self.plan['mesh_sha256']=fingerprint(self.mesh)
        with self.assertRaises(Blocked):validate_plan(self.mesh,self.plan)
    def test_edge_nonmanifold(self):
        with self.assertRaisesRegex(Blocked,'nonmanifold edges'):topology([[0,1,2],[1,0,3],[0,1,4]])
    def test_source_ids_exact_after_reconstruction(self):
        r=build_topology(self.mesh,self.plan)
        self.assertNotIn(18,r['original_ids'])
        for i,old in enumerate(r['original_ids']):
            if old>=0:self.assertEqual(r['vertices'][i],self.mesh['vertices'][old])
        for f,old in zip(r['faces'],r['face_sources']):
            if old>=0:self.assertEqual([r['original_ids'][i] for i in f],self.mesh['faces'][old])
        self.assertEqual(sum(i<0 for i in r['original_ids']),7)
        self.assertEqual(topology(r['faces']),set())
    def test_mapping_detects_equal_count_identity_mutation(self):
        r=build_topology(self.mesh,self.plan);ids=r['original_ids'][:];ids[0],ids[1]=ids[1],ids[0]
        self.assertEqual(len(ids),len(r['original_ids']))
        self.assertTrue(any(old>=0 and r['vertices'][i]!=self.mesh['vertices'][old] for i,old in enumerate(ids)))
    def test_source_not_modified(self):
        before=copy.deepcopy(self.mesh);build_topology(self.mesh,self.plan);self.assertEqual(before,self.mesh)
    def test_separate_closed_component_preserved(self):
        k=len(self.mesh['vertices']);self.mesh['vertices'] += [[10,0,0],[11,0,0],[10,1,0],[10,0,1]]
        self.mesh['faces'] += [[k,k+2,k+1],[k,k+1,k+3],[k,k+3,k+2],[k+1,k+2,k+3]]
        self.plan['protected_vertices'] += list(range(k,k+4));self.plan['mesh_sha256']=fingerprint(self.mesh)
        r=build_topology(self.mesh,self.plan)
        self.assertTrue(set(range(k,k+4))<=set(r['original_ids']))
    def test_cli_bad_json_report(self):
        with tempfile.TemporaryDirectory() as d:
            d=Path(d);(d/'mesh').write_text(json.dumps(self.mesh));(d/'plan').write_text('{broken')
            p=subprocess.run([sys.executable,str(Path(__file__).with_name('iskra_mouth_cut_plan.py')),'--mesh-json',str(d/'mesh'),'--plan',str(d/'plan'),'--report',str(d/'report')],capture_output=True)
            self.assertEqual(p.returncode,2);self.assertEqual(json.loads((d/'report').read_text())['status'],'BLOCKED')
    def test_mutations_each_safety_statement(self):
        path=Path(__file__).with_name('iskra_mouth_cut_plan.py')
        cases=[('source mesh fingerprint mismatch',dict(mesh_sha256='wrong')),
               ('cut touches protected vertices',dict(mouth_vertices=list(range(6)),protected_vertices=list(range(6,19)))),
               ('anatomical approval required',dict(anatomy_reviewed=False)),
               ('cut_loop winding differs from removed faces',dict(cut_loop=list(reversed(range(6))))),
               ('inward_axis points outward or tangentially',dict(inward_axis=[0,0,1]))]
        for reason,changes in cases:
            with self.subTest(reason=reason):
                tree=ast.parse(path.read_text());removed=[]
                class Mut(ast.NodeTransformer):
                    def visit_Expr(self,node):
                        if isinstance(node.value,ast.Call) and isinstance(node.value.func,ast.Name) and node.value.func.id=='require' and any(isinstance(a,ast.Constant) and a.value==reason for a in node.value.args):
                            removed.append(True);return ast.copy_location(ast.Pass(),node)
                        return self.generic_visit(node)
                tree=Mut().visit(tree);env={'__name__':'mutant'};exec(compile(tree,str(path),'exec'),env)
                self.assertEqual(len(removed),1)
                with self.assertRaises(Blocked):validate_plan(self.mesh,dict(self.plan,**changes))
                self.assertEqual(env['validate_plan'](self.mesh,dict(self.plan,**changes))['status'],'TOPOLOGY_CANDIDATE')

class IntersectionTests(unittest.TestCase):
    def test_crossing_triangles(self):
        c=[[0,0,0],[2,0,0],[0,2,0],[.5,.5,-1],[.5,.5,1],[1,.5,1]]
        self.assertTrue(illegal_intersection(c,[0,1,2],[3,4,5],1e-7))
    def test_shared_edge_is_legal(self):
        c=[[0,0,0],[1,0,0],[0,1,0],[0,0,1]]
        self.assertFalse(illegal_intersection(c,[0,1,2],[1,0,3],1e-7))
    def test_coplanar_overlap_not_legal(self):
        c=[[0,0,0],[1,0,0],[0,1,0],[.25,.25,0]]
        self.assertTrue(illegal_intersection(c,[0,1,2],[0,1,3],1e-7))
    def test_coplanar_neighbors_legal(self):
        c=[[0,0,0],[1,0,0],[0,1,0],[1,1,0]]
        self.assertFalse(illegal_intersection(c,[0,1,2],[1,3,2],1e-7))
    def test_noncontact_legal(self):
        c=[[0,0,0],[1,0,0],[0,1,0],[0,0,1],[1,0,1],[0,1,1]]
        self.assertFalse(illegal_intersection(c,[0,1,2],[3,4,5],1e-7))

if __name__=='__main__':unittest.main()
