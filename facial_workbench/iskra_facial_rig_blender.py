import bpy
import sys
import math
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SRC = args[0] if args else "base_basic_pbr.glb"
OUT = args[1] if len(args) > 1 else "iskra_avatar_facial_rig_blender.glb"
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)
mesh = next(o for o in bpy.context.scene.objects if o.type == "MESH")
arm = next(o for o in bpy.context.scene.objects if o.type == "ARMATURE")
if not mesh.data.shape_keys:
    mesh.shape_key_add(name="Basis")

def g(v, c, s):
    return math.exp(-0.5 * sum(((v[i] - c[i]) / s[i]) ** 2 for i in range(3)))

def front(v):
    return max(0, min(1, (v.z - .035) / .11))

def mask(v, c, s):
    return g(v, c, s) * front(v)

def add(name, delta):
    k = mesh.shape_key_add(name=name)
    for i, p in enumerate(mesh.data.vertices):
        d = delta(p.co)
        k.data[i].co = p.co + Vector(d)
    return k

L = (-.055, 1.735, .145)
R = (.055, 1.735, .145)
LS = (.05, .029, .045)
M = (0, 1.635, .145)
MS = (.10, .05, .055)

def eye(side):
    c = L if side == "L" else R
    return lambda v: (0, (-.020 if v.y > 1.735 else .020) * mask(v, c, LS), 0)

add("eyeBlinkLeft", eye("L"))
add("eyeBlinkRight", eye("R"))

for n, dx, dy in [
    ("eyesLeft", -.018, 0), ("eyesRight", .018, 0),
    ("eyesUp", 0, .014), ("eyesDown", 0, -.014)
]:
    add(n, lambda v, dx=dx, dy=dy: (
        dx * (mask(v, L, (.036, .034, .038)) + mask(v, R, (.036, .034, .038))),
        dy * (mask(v, L, (.036, .034, .038)) + mask(v, R, (.036, .034, .038))),
        0
    ))

add("browInnerUp", lambda v: (
    0, .020 * (
        mask(v, (-.055, 1.785, .135), (.06, .025, .05)) +
        mask(v, (.055, 1.785, .135), (.06, .025, .05))
    ) * max(0, 1 - abs(v.x) / .09), 0
))
add("browDownLeft", lambda v: (0, -.016 * mask(v, (-.055, 1.785, .135), (.06, .025, .05)), 0))
add("browDownRight", lambda v: (0, -.016 * mask(v, (.055, 1.785, .135), (.06, .025, .05)), 0))

def mouth(v):
    return mask(v, M, MS)

def lower(v):
    return mouth(v) * max(0, min(1, (1.655 - v.y) / .055))

def corners(v):
    return mouth(v) * max(0, min(1, abs(v.x) / .045))

add("jawOpen", lambda v: (0, -.038 * lower(v), -.010 * lower(v)))
add("mouthSmile", lambda v: (math.copysign(.022, v.x) * corners(v), .018 * corners(v), 0))
add("mouthFrown", lambda v: (-math.copysign(.010, v.x) * corners(v), -.018 * corners(v), 0))
add("mouthFunnel", lambda v: (-math.copysign(.022, v.x) * mouth(v), 0, .012 * mouth(v)))
add("mouthPucker", lambda v: (-math.copysign(.030, v.x) * mouth(v), 0, .018 * mouth(v)))

for n, dx, up, down, dz in [
    ("viseme_MBP", 0, -.010, .010, 0),
    ("viseme_FV", 0, 0, .010, -.006),
    ("viseme_A", 0, .010, -.030, 0),
    ("viseme_E", .018, .004, -.012, 0),
    ("viseme_I", .024, .003, -.009, 0),
    ("viseme_O", -.018, .010, -.018, .012),
    ("viseme_U", -.026, .006, -.012, .016)
]:
    add(n, lambda v, dx=dx, up=up, down=down, dz=dz: (
        math.copysign(dx, v.x) * mouth(v),
        (up if v.y > 1.635 else down) * mouth(v),
        dz * mouth(v)
    ))

controls = {
    "CTRL_EYES": ["eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight", "eyesUp", "eyesDown"],
    "CTRL_BROWS": ["browInnerUp", "browDownLeft", "browDownRight"],
    "CTRL_MOUTH": ["mouthSmile", "mouthFrown", "mouthFunnel", "mouthPucker"],
    "CTRL_JAW": ["jawOpen"],
    "CTRL_VISEMES": ["viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U"]
}
for ctrl, names in controls.items():
    arm[ctrl] = 0.0
    for name in names:
        fcurve = mesh.data.shape_keys.key_blocks[name].driver_add("value")
        driver = fcurve.driver
        driver.expression = f"max(0,min(1,{ctrl}))"
        var = driver.variables.new()
        var.name = ctrl
        var.type = "SINGLE_PROP"
        var.targets[0].id = arm
        var.targets[0].data_path = f'["{ctrl}"]'

bpy.ops.export_scene.gltf(
    filepath=OUT, export_format="GLB", export_morph=True,
    export_morph_normal=False, export_animations=True, export_skins=True
)
print("EXPORTED", OUT)
