# Lumen V2 GLB integrator — static audit

Source: user-supplied `iskra_glb_face_integrator.py`. No Blender execution.

## Findings

1. **False preservation evidence:** `snapshot()` runs before/after export on the same Blender scene. Unchanged actions/bones in memory do not establish that they survived in the GLB.
2. Exactly 12 shape keys are required, conflicting with the pre-existing 21-target facial rig. This rejects preservation of eye/brow/viseme shapes.
3. Exported object selection is not validated against a re-imported GLB. Armature and accessory meshes may be omitted or unskinned in the artifact.
4. Seven `bpy.data.actions` are not proof of seven exported, correctly bound glTF animations. Action naming and Blender version behavior need validation.
5. Materials are collected in `snapshot` but never compared; UV, textures, skin weights, bone hierarchy and morph deltas are not checked.
6. SHA-256 and nonzero file size prove only file creation, not glTF integrity or visual correctness.
7. `from iskra_face_shapes import SHAPES` relies on script/module import path configuration.
8. Report status IMPLEMENTED overstates evidence. Need export to temporary file, structural parse and Blender round-trip before promotion.

## Required acceptance gate

Compare the original GLB and candidate GLB: animations (samplers/channels/targets), bones and skin bindings, meshes and material/texture references, UVs, and morph names/values. Confirm 0/0.5/1 jaw aperture and visually inspect frontal/profile renders. Preserve the 21-target contract or provide an explicit migration with tests.

Status: STATIC / NO GO. No export, no merge/deploy.
