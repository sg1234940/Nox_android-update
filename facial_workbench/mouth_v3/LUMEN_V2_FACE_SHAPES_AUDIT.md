# Lumen V2 facial shapes — static review

Source: user-supplied `iskra_face_shapes.py` (12 named shape keys).

## Findings

1. **Destructive:** removes every existing non-Basis shape key. This can destroy eye, brow and viseme targets as well as their animation/driver references.
2. No collision policy for names already present; should refuse or replace only explicitly owned shapes after preserving dependencies.
3. Absolute displacement constants (0.010–0.030) are not scaled to face dimensions.
4. Only vertices explicitly in lip groups move; hard edges at group borders can stretch adjacent faces and cause visible seams.
5. `jawOpen` does not animate a validated jaw/inner cavity system and can move upper lip/corners unexpectedly.
6. `calculate_local_frame` lacks degenerate and orthogonality checks.
7. Validation only proves no moved vertices outside the chosen indices; it does not prove nonzero shape movement, correct mouth aperture, visual quality or absence of self-intersections.
8. Report status IMPLEMENTED is not Blender runtime proof.

## Integration requirements

Preserve existing 21-target facial rig contract or explicitly migrate it with mapping and tests; never silently delete keys. Require source-mesh binding, valid mouth region masks, per-target nonzero checks, aperture progression, continuity and GLB round-trip validation.

Status: STATIC / NO GO. No Blender execution, no GLB, no merge.
