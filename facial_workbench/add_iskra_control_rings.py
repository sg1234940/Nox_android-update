import bpy
import math
from mathutils import Vector
COLLECTION = "ISKRA_CONTROLS"
def ensure_collection():
    c = bpy.data.collections.get(COLLECTION)
    if c is None:
        c = bpy.data.collections.new(COLLECTION)
        bpy.context.scene.collection.children.link(c)
    c.hide_render = True
    return c
def ring(name, radius, color):
    mesh = bpy.data.meshes.new(name + "_mesh")
    verts = []
    edges = []
    segments = 48
    for i in range(segments):
        a = 2 * math.pi * i / segments
        verts.append((math.cos(a) * radius, 0, math.sin(a) * radius))
        edges.append((i, (i + 1) % segments))
    mesh.from_pydata(verts, edges, [])
    obj = bpy.data.objects.new(name, mesh)
    ensure_collection().objects.link(obj)
    obj.color = color
    obj.hide_render = True
    return obj
def get_rig():
    return next((o for o in bpy.context.scene.objects if o.type == "ARMATURE"), None)
def new_control_bone(rig, name, parent_name, offset):
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = rig.data.edit_bones.new(name)
    parent = rig.data.edit_bones.get(parent_name) if parent_name else None
    base = parent.head.copy() if parent else Vector((0, 0, 0))
    eb.head = base + Vector(offset)
    eb.tail = eb.head + Vector((0, .04, 0))
    if parent:
        eb.parent = parent
    bpy.ops.object.mode_set(mode="POSE")
    return rig.pose.bones[name]
def assign(pb, shape, scale=1.0):
    pb.custom_shape = shape
    pb.custom_shape_scale_xyz = (scale, scale, scale)
    pb.rotation_mode = "XYZ"
def main():
    rig = get_rig()
    if not rig:
        raise RuntimeError("No armature found. Import base_basic_pbr.glb first.")
    ensure_collection()
    bones = rig.pose.bones
    head = next((n for n in ["Head", "head", "mixamorig:Head"] if n in bones), None)
    jaw = next((n for n in ["Jaw", "jaw", "mixamorig:Jaw"] if n in bones), None)
    eyeL = next((n for n in ["Eye.L", "eye.L", "LeftEye", "mixamorig:LeftEye"] if n in bones), None)
    eyeR = next((n for n in ["Eye.R", "eye.R", "RightEye", "mixamorig:RightEye"] if n in bones), None)
    specs = [
        ("CTRL_FACE_MASTER", head, (0, -.16, .02), .16, (1.0, .30, .03, 1)),
        ("CTRL_BROWS", head, (0, -.13, .10), .10, (1.0, .72, .05, 1)),
        ("CTRL_MOUTH", jaw or head, (0, -.13, -.08), .09, (1.0, .18, .03, 1)),
        ("CTRL_JAW", jaw or head, (0, -.11, -.13), .075, (.9, .08, .03, 1)),
        ("CTRL_EYES", head, (0, -.14, .055), .075, (.12, .65, 1, 1))
    ]
    for name, parent, off, r, col in specs:
        if name in bones:
            pb = bones[name]
        else:
            pb = new_control_bone(rig, name, parent, off)
        shp = bpy.data.objects.get("WGT_" + name) or ring("WGT_" + name, r, col)
        assign(pb, shp)
    for side, parent, x in [("L", eyeL, -.045), ("R", eyeR, .045)]:
        name = "CTRL_EYE_" + side
        if parent:
            pb = bones.get(name) or new_control_bone(rig, name, parent, (x, -.11, 0))
            shp = bpy.data.objects.get("WGT_" + name) or ring("WGT_" + name, .045, (.12, .8, 1, 1))
            assign(pb, shp)
    bpy.ops.object.mode_set(mode="OBJECT")
    out = bpy.path.abspath("//iskra_avatar_control_rings.blend")
    bpy.ops.wm.save_as_mainfile(filepath=out)
    print("Saved", out)
main()
