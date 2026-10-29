import json
import struct
import numpy as np
import sys
base = "base_basic_pbr.glb"
rig = "iskra_avatar_facial_rig.glb"
def load(p):
    d = open(p, "rb").read()
    m, v, t = struct.unpack_from("<4sII", d, 0)
    assert m == b"glTF"
    assert v == 2
    assert t == len(d)
    jl, jt = struct.unpack_from("<II", d, 12)
    j = json.loads(d[20:20 + jl].decode().rstrip())
    o = 20 + jl
    bl, bt = struct.unpack_from("<II", d, o)
    return (j, d[o + 8:o + 8 + bl])
def arr(j, b, i):
    a = j["accessors"][i]
    v = j["bufferViews"][a["bufferView"]]
    st = v.get("byteOffset", 0) + a.get("byteOffset", 0)
    return np.frombuffer(b, np.float32, count=a["count"] * 3, offset=st).reshape(-1, 3)
bj, bb = load(base)
j, b = load(rig)
tests = []
def ck(n, x):
    tests.append((n, bool(x)))
ck("glTF 2.0", j["asset"]["version"] == "2.0")
ck("body animation count preserved", len(j["animations"]) == len(bj["animations"]) == 7)
ck("body animation JSON preserved", j["animations"] == bj["animations"])
names = j["meshes"][0]["extras"]["targetNames"]
ck("21 named morph targets", len(names) == 21 and len(j["meshes"][0]["primitives"][0]["targets"]) == 21)
ck("required controls mapped", all(x in j["meshes"][0]["extras"]["iskraFacialRig"]["controls"] for x in ["CTRL_EYES", "CTRL_BROWS", "CTRL_MOUTH", "CTRL_JAW", "CTRL_VISEMES"]))
targets = j["meshes"][0]["primitives"][0]["targets"]
for name, t in zip(names, targets):
    d = arr(j, b, t["POSITION"])
    ck(name + " nonzero", float(np.max(np.linalg.norm(d, axis=1))) > 0.001)
    ck(name + " finite", np.isfinite(d).all())
for n, ok in tests:
    print("PASS" if ok else "FAIL", n)
print("SUMMARY", sum(x for _, x in tests), "/", len(tests))
sys.exit(0 if all(x for _, x in tests) else 1)
