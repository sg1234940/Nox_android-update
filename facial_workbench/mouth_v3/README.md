# ISKRA MOUTH V3 — safety checkpoint

This branch preserves a corrected, fail-closed starting point for mouth geometry analysis.

- `iskra_mouth_mesh_analyzer.py`: imports GLB and records mesh structure and global boundary components. It **does not** infer mouth rims from unrelated global boundaries, fabricate lip landmarks, modify geometry, or set VERIFIED.
- `iskra_mouth_report_guard.py`: checks that an audit-only report cannot authorize cutting.
- Run: `blender --background --python iskra_mouth_mesh_analyzer.py -- --input base_basic_pbr.glb --report MOUTH_GEOMETRY_REPORT.json`
- Check: `python iskra_mouth_report_guard.py MOUTH_GEOMETRY_REPORT.json`

**Not implemented:** mouth cavity construction, 12 morph targets, validated GLB export, visual tests. Do not treat this checkpoint as a finished avatar. The previous V1 builder is unsafe to run based on texture heuristics. No Android or production changes.
