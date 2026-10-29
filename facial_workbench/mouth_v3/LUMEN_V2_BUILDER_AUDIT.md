# ISKRA MOUTH MESH V2 builder — static audit

Source: complete user-supplied builder in conversation. No Blender execution.

## Blocking defects

- Trusts `safe_for_builder` from the V2 analyzer even though that flag is only a color/PCA heuristic; does not check report schema, source mesh identity/hash, or topology evidence.
- `create_mouth_opening` selects polygons by centroid in an approximate ellipse; no ordered, validated cut loop, face-island connectivity check, or geometric collision check.
- A rim is accepted if merely six global boundary vertices lie in a projected box. Does not verify a single closed loop, correct winding, or manifoldness.
- `create_closed_ellipsoid` duplicates pole positions at each longitude, creating degenerate quads. It is a separate object, not stitched to the rim.
- `add_skin_modifier` weights every cavity/teeth/tongue vertex to Head only; no verified jaw/lower-face deformation or coordinated mouth movement.
- `calculate_local_frame` can produce invalid axes when centroids coincide or horizontal and vertical directions are parallel; depth sign derived from head centroid is ambiguous.
- `--cavity-depth` is a hard-coded world-independent dimension and is not validated against head size.
- The source object is hidden before downstream validation; the output .blend is saved with unverified geometry.
- Build report says IMPLEMENTED and original unchanged without proving mesh identity, watertightness, UV correctness, or deformation.
- The script does not generate morph targets or a verified GLB.

## Required fixes before any cutting

1. Bind analysis to the exact source mesh fingerprint and ordered vertex/face topology.
2. Require independently verified cut loop, orientation, and mesh connectivity; otherwise fail closed.
3. Make all modifications transactional on a copy; verify rim, manifoldness, UV and armature before saving.
4. Replace separate unconnected cavity primitive with a stitched and tested mouth cavity.
5. Validate poles, normals, degenerate faces, skinning, and the jawOpen deformation.
6. Report TESTED only after real Blender execution and round-trip checks.

## Status

STATIC / NO GO. No Blender run, no generated GLB, no Android changes.
