import hashlib
import json
import unittest
from iskra_mouth_cut_plan import validate_plan, Blocked


class CutPlanTests(unittest.TestCase):
    def setUp(self):
        # A center vertex surrounded by a closed hexagonal ring.
        self.mesh={"vertices":[[0,0,0]]+[[i,0,0] for i in range(6)],
                   "faces":[[0, i, i+1] for i in range(1,6)]+[[0,6,1]]}
        # Add exterior triangles so all ring edges are interior.
        self.mesh["vertices"] += [[i,1,0] for i in range(6)]
        self.mesh["faces"] += [[i,7+i-1,7+(i%6)] for i in range(1,7)]
        digest=hashlib.sha256(json.dumps(self.mesh,sort_keys=True,separators=(",",":")).encode()).hexdigest()
        self.plan={"schema":"ISKRA_MOUTH_CUT_PLAN/1","mesh_sha256":digest,
                   "cut_loop":[1,2,3,4,5,6],"upper_rim":[1,2,3],"lower_rim":[4,5,6]}

    def test_candidate_not_build_authorization(self):
        self.assertFalse(validate_plan(self.mesh,self.plan)["safe_for_automatic_build"])

    def test_wrong_hash_rejected(self):
        self.plan["mesh_sha256"]="wrong"
        with self.assertRaises(Blocked): validate_plan(self.mesh,self.plan)

    def test_repeated_vertex_rejected(self):
        self.plan["cut_loop"]=[1,2,3,4,5,1]
        with self.assertRaises(Blocked): validate_plan(self.mesh,self.plan)

    def test_wrong_rim_rejected(self):
        self.plan["lower_rim"]=[3,4,5,6]
        with self.assertRaises(Blocked): validate_plan(self.mesh,self.plan)


if __name__=="__main__":
    unittest.main()
