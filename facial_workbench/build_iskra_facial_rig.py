#!/usr/bin/env python3
import json, struct
from pathlib import Path
import numpy as np

SRC = Path("base_basic_pbr.glb")
OUT = Path("iskra_avatar_facial_rig.glb")
CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}

def read_glb(path):
    d = path.read_bytes()
    magic, ver, total = struct.unpack_from("<4sII", d, 0)
    assert magic == b"glTF"
    assert ver == 2
    assert total == len(d)
    pos = 12
    doc = None
    binary = b""
    while pos < len(d):
        ln, typ = struct.unpack_from("<II", d, pos)
        pos += 8
        payload = d[pos:pos + ln]
        pos += ln
        if typ == 0x4E4F534A:
            doc = json.loads(payload.decode().rstrip("\0 \t\r\n"))
        elif typ == 0x004E4942:
            binary = payload
    return doc, binary

def accessor(doc, blob, i):
    a = doc["accessors"][i]
    v = doc["bufferViews"][a["bufferView"]]
    n = NC[a["type"]]
    dt = np.dtype(CT[a["componentType"]])
    stride = v.get("byteStride", dt.itemsize * n)
    start = v.get("byteOffset", 0) + a.get("byteOffset", 0)
    out = np.empty((a["count"], n), dt)
    for k in range(a["count"]):
        out[k] = np.frombuffer(blob, dtype=dt, count=n, offset=start + k * stride)
    return out

def gauss(P, c, s):
    c = np.array(c)
    s = np.array(s)
    return np.exp(-0.5 * np.sum(((P - c) / s) ** 2, axis=1)).astype(np.float32)

def build_targets(P, headw):
    front = np.clip((P[:, 2] - 0.035) / 0.11, 0, 1).astype(np.float32)
    hw = np.clip(headw * 1.5, 0, 1).astype(np.float32)
    def mask(c, s):
        return gauss(P, c, s) * front * hw
    Leye = mask((-0.055, 1.735, 0.145), (0.050, 0.029, 0.045))
    Reye = mask((0.055, 1.735, 0.145), (0.050, 0.029, 0.045))
    Lbrow = mask((-0.055, 1.785, 0.135), (0.060, 0.025, 0.050))
    Rbrow = mask((0.055, 1.785, 0.135), (0.060, 0.025, 0.050))
    mouth = mask((0, 1.635, 0.145), (0.100, 0.050, 0.055))
    lower = mouth * np.clip((1.655 - P[:, 1]) / 0.055, 0, 1)
    corners = mouth * np.clip(np.abs(P[:, 0]) / 0.045, 0, 1)
    eyeLball = mask((-0.055, 1.728, 0.155), (0.036, 0.034, 0.038))
    eyeRball = mask((0.055, 1.728, 0.155), (0.036, 0.034, 0.038))
    Z = np.zeros_like(P, dtype=np.float32)
    def v(dx=0, dy=0, dz=0, w=None):
        q = Z.copy()
        w = np.asarray(w, dtype=np.float32)
        q[:, 0] = dx * w
        q[:, 1] = dy * w
        q[:, 2] = dz * w
        return q
    T = {}
    T["eyeBlinkLeft"] = v(dy=-0.020, w=Leye) * np.where((P[:, 1] > 1.735)[:, None], 1, -1)
    T["eyeBlinkRight"] = v(dy=-0.020, w=Reye) * np.where((P[:, 1] > 1.735)[:, None], 1, -1)
    T["eyesLeft"] = v(dx=-0.018, w=eyeLball + eyeRball)
    T["eyesRight"] = v(dx=0.018, w=eyeLball + eyeRball)
    T["eyesUp"] = v(dy=0.014, w=eyeLball + eyeRball)
    T["eyesDown"] = v(dy=-0.014, w=eyeLball + eyeRball)
    T["browInnerUp"] = v(dy=0.020, w=(Lbrow + Rbrow) * np.clip(1 - np.abs(P[:, 0]) / 0.09, 0, 1))
    T["browDownLeft"] = v(dy=-0.016, w=Lbrow)
    T["browDownRight"] = v(dy=-0.016, w=Rbrow)
    T["jawOpen"] = v(dy=-0.038, dz=-0.010, w=lower)
    T["mouthSmile"] = v(dx=np.sign(P[:, 0]) * 0.022, dy=0.018, w=corners)
    T["mouthFrown"] = v(dx=-np.sign(P[:, 0]) * 0.010, dy=-0.018, w=corners)
    T["mouthFunnel"] = v(dx=-np.sign(P[:, 0]) * 0.022, dz=0.012, w=mouth)
    T["mouthPucker"] = v(dx=-np.sign(P[:, 0]) * 0.030, dz=0.018, w=mouth)
    T["viseme_MBP"] = v(dy=np.where(P[:, 1] > 1.635, -0.010, 0.010), w=mouth)
    T["viseme_FV"] = v(dy=0.010, dz=-0.006, w=lower)
    T["viseme_A"] = v(dy=np.where(P[:, 1] > 1.635, 0.010, -0.030), w=mouth)
    T["viseme_E"] = v(dx=np.sign(P[:, 0]) * 0.018, dy=np.where(P[:, 1] > 1.635, 0.004, -0.012), w=mouth)
    T["viseme_I"] = v(dx=np.sign(P[:, 0]) * 0.024, dy=np.where(P[:, 1] > 1.635, 0.003, -0.009), w=mouth)
    T["viseme_O"] = v(dx=-np.sign(P[:, 0]) * 0.018, dy=np.where(P[:, 1] > 1.635, 0.010, -0.018), dz=0.012, w=mouth)
    T["viseme_U"] = v(dx=-np.sign(P[:, 0]) * 0.026, dy=np.where(P[:, 1] > 1.635, 0.006, -0.012), dz=0.016, w=mouth)
    return T

doc, blob = read_glb(SRC)
original_anims = json.dumps(doc.get("animations", []), sort_keys=True, separators=(",", ":"))
pr = doc["meshes"][0]["primitives"][0]
P = accessor(doc, blob, pr["attributes"]["POSITION"]).astype(np.float32)
J0 = accessor(doc, blob, pr["attributes"]["JOINTS_0"])
W0 = accessor(doc, blob, pr["attributes"]["WEIGHTS_0"])
J1 = accessor(doc, blob, pr["attributes"]["JOINTS_1"])
W1 = accessor(doc, blob, pr["attributes"]["WEIGHTS_1"])
names = [doc["nodes"][x].get("name") for x in doc["skins"][0]["joints"]]
hi = names.index("Head")
headw = np.zeros(len(P), np.float32)
for c in range(4):
    headw += np.where(J0[:, c] == hi, W0[:, c], 0)
    headw += np.where(J1[:, c] == hi, W1[:, c], 0)
targets = build_targets(P, headw)
target_names = list(targets)
newblob = bytearray(blob)
while len(newblob) % 4:
    newblob.append(0)
prim_targets = []
for name, delta in targets.items():
    start = len(newblob)
    raw = np.asarray(delta, dtype="<f4").tobytes()
    newblob.extend(raw)
    bv = len(doc.setdefault("bufferViews", []))
    doc["bufferViews"].append({"buffer": 0, "byteOffset": start, "byteLength": len(raw), "target": 34962, "name": name + "_POSITION"})
    ac = len(doc.setdefault("accessors", []))
    doc["accessors"].append({"bufferView": bv, "componentType": 5126, "count": len(P), "type": "VEC3", "min": delta.min(0).tolist(), "max": delta.max(0).tolist(), "name": name + "_POSITION"})
    prim_targets.append({"POSITION": ac})
pr["targets"] = prim_targets
mesh = doc["meshes"][0]
mesh["weights"] = [0.0] * len(target_names)
mesh.setdefault("extras", {})["targetNames"] = target_names
for n in doc["nodes"]:
    if n.get("mesh") == 0:
        n["weights"] = [0.0] * len(target_names)
controls = {
    "CTRL_EYES": ["eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight", "eyesUp", "eyesDown"],
    "CTRL_BROWS": ["browInnerUp", "browDownLeft", "browDownRight"],
    "CTRL_MOUTH": ["mouthSmile", "mouthFrown", "mouthFunnel", "mouthPucker"],
    "CTRL_JAW": ["jawOpen"],
    "CTRL_VISEMES": ["viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U"]
}
mesh["extras"]["iskraFacialRig"] = {
    "schema": "ISKRA_facial_rig/2.0",
    "controls": controls,
    "neutralAtZero": True,
    "runtime": "glTF morph target weights",
    "notes": "CTRL names are semantic runtime mappings. Blender custom shapes are intentionally not exported."
}
doc.setdefault("asset", {}).setdefault("extras", {})["iskraFacialRig"] = {
    "targetNames": target_names,
    "controls": controls,
    "speechVisemes": ["viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U"]
}
doc["buffers"][0]["byteLength"] = len(newblob)
assert json.dumps(doc.get("animations", []), sort_keys=True, separators=(",", ":")) == original_anims
jb = json.dumps(doc, separators=(",", ":"), ensure_ascii=False).encode()
jb += b" " * ((-len(jb)) % 4)
newblob.extend(b"\0" * ((-len(newblob)) % 4))
total = 12 + 8 + len(jb) + 8 + len(newblob)
with OUT.open("wb") as f:
    f.write(struct.pack("<4sII", b"glTF", 2, total))
    f.write(struct.pack("<II", len(jb), 0x4E4F534A))
    f.write(jb)
    f.write(struct.pack("<II", len(newblob), 0x004E4942))
    f.write(newblob)
print("WROTE", OUT, OUT.stat().st_size, "targets", len(target_names), "animations", len(doc.get("animations", [])))
