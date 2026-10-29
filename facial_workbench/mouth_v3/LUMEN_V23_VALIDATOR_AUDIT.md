# ISKRA FACE VALIDATION 2.3 — static review

Source: user-supplied validator 2.3 in conversation. No Blender execution.

## Improvements over 2.2
- Reads original and result GLB data and checks SHA-bound manifest.
- Compares animation channels, interpolation, times and accessor values, including sparse accessors.
- Compares skin joints/inverse-bind matrices and Blender bone parent/rest data.
- Uses explicit result-to-original mapping and central lip pairs.
- Attempts to emit a report on validation failures.

## Blocking issues
1. Exact 12-shape contract conflicts with legacy 21-shape facial rig. No preservation/migration check.
2. Manifest is trusted; SHA binding proves file identity, not correctness of lip landmarks or correspondence.
3. No proof of rim-to-cavity connection, manifoldness or self-intersection avoidance.
4. Morph target combinations and Android Filament behavior remain untested.
5. The GLB animation parser assumes all animated nodes have unique nonempty names; valid GLB assets may not meet this stricter project policy.
6. The `material()` recursive walker resolves any nested `index` under a parent ending in `Texture` as a texture index; extensions need explicit schema-aware handling.
7. The mapping checks outside-mouth coverage but cannot independently establish correct correspondence for duplicated vertices.
8. The result-to-original mapping is not checked for source mouth vertex coverage or false -1 assignments in a way that proves original mouth preservation.
9. `close_sets` is set-equivalence within tolerance, not bijective correspondence; duplicate/merged corner attributes can pass.
10. Blender import can change vertex ordering, triangulation, names, and material structure. Manifest mapping needs proof against the imported representation.
11. Missing cavity is allowed unless `--require-support` is provided; a separate cavity mesh still does not prove stitching.
12. Visual validation is explicitly NOT_PERFORMED; PASS must be reported only as structural scope.

## Status
STATIC review. No Blender run, no GLB generated, no merge/deploy.
