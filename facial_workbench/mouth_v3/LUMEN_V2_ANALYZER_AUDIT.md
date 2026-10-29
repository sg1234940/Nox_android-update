# Audit of Lumen ISKRA MOUTH MESH V2 analyzer (2026-10-29)

Source: user-supplied script in conversation. This is a review, **not** a validated implementation.

## Blocking findings

1. `for neighbor in adjacencyif neighbor not in remaining:` is invalid Python syntax. The correct loop must iterate `adjacency[current]` and then test membership.
2. `safe_for_builder` is set from a color/PCA region score alone; it does not require a validated cut boundary or mouth interior.
3. Manual region lists are marked `MANUAL_VERIFIED` based only on index range. No evidence of topology or facial landmarks is checked.
4. `has_existing_opening` tests intersection with *any* global boundary vertex. That does not establish a mouth opening, much less separate lip rims.
5. PCA eigenvector sign/order is ambiguous. Head centroid is not a reliable vertical orientation reference.
6. Image selection may choose an image not connected to Principled BSDF Base Color; image pixels are sampled repeatedly, with no UV material-slot correspondence.
7. `replace_vertex_group` removes existing groups by name; unsafe without collision checks.
8. `covariance_axes` may generate duplicate fallback axes in degenerate cases.
9. The report uses `RUNTIME` for inferences that are unverified; confidence is a heuristic, not a probability.

## Required safety contract

- Distinguish CANDIDATE, LANDMARKS_CONFIRMED and TOPOLOGY_CONFIRMED.
- Automatic geometry edits require explicit, model-bound evidence of an ordered closed mouth cut loop, independent upper/lower rim assignments, material/UV compatibility and a reversible build plan.
- Manual JSON must include mesh fingerprint and validated indices; manual input alone is never topology proof.
- Default to BLOCKED and never export a modified GLB on ambiguous evidence.

## Status

STATIC review only. No Blender run, no GLB generated, no merge/deploy.
