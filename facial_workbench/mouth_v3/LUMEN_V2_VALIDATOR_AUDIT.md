# Lumen V2.2 face validation — static audit

Source: user-supplied `iskra_face_validation.py`. Not executed.

## Blockers

1. Syntax error at `def group_indices(... ) -> listgroup = ...`; cannot compile.
2. If no face candidate exists, most critical shape, UV, weights, and geometry checks are skipped. The suite can report a misleading high pass ratio; missing face must be a hard fail.
3. If Basis or lip groups are absent, important morph/aperture tests are skipped instead of failing.
4. Checks only names of actions, not glTF animation channels, samplers, node bindings or sampled bone transforms.
5. Bone names alone do not verify hierarchy, bind matrices or skin joint weights.
6. UV signature checks layer names and lengths, not UV coordinates or texture references. Material slots compare names only.
7. `mesh_has_valid_faces` checks polygon vertex counts, not zero area, manifoldness, boundary topology or self-intersections.
8. Support objects are optional by detection; a missing required mouth cavity may go unnoticed. No rim-to-cavity stitching proof.
9. Index-by-index geometry comparison assumes vertex ordering survives Blender GLB export/import. This must be tested or replaced by stable source mapping.
10. `jawOpen` checks distance between two centroids, which can grow without forming a real visible mouth opening.
11. The test checks no movement of CHIN, which may conflict with an anatomically valid jaw motion; it is a project-specific policy, not a universal quality test.
12. No guaranteed JSON report on exceptions during import or validation.

## Acceptance criteria

Always emit a report on failure. Treat missing required components as explicit FAIL. Compare glTF structure and sampled animation results; validate topology, cavity connectivity, UV and texture data, and actual jaw aperture. Preserve legacy 21-target contract or explicitly migrate with tests.

Status: STATIC / NO GO. No Blender runtime, no GLB generated, no merge/deploy.
