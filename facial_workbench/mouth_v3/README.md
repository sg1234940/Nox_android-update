# ISKRA MOUTH V3 — repaired candidate builder

This is a **candidate authoring tool**, not a finished Iskra mouth or a production
GLB exporter. It does not guess lip landmarks. Original assets are never edited.

## What is implemented

- One shared `ISKRA_MOUTH_CUT_PLAN/3` schema and canonical geometry fingerprint.
  `/1` and `/2` plans are rejected; renaming the schema is not a migration.
- Explicit approved mouth vertices and their protected complement, connected disk
  selection, directed cut boundary, correct winding and inward direction.
- At most 10% of source surface can be removed. This fixed safety limit is not
  anatomical proof and cannot be overridden by a plan field.
- Immutable original-vertex/face IDs. Reconstruction does not use BMesh deletion
  or index tables. Unused cut vertices are removed through explicit mapping.
- Source boundaries and disconnected components are preserved; edge and vertex
  manifoldness are checked. Existing openings cannot serve as a new cut.
- A simple, almost planar, star-shaped rim is required for this ring/fan algorithm.
  More complex loops block rather than receive a guessed triangulation.
- BVH broad phase plus triangle narrow phase detects cavity/skin and cavity/self
  collisions on Basis, each existing shape, and the current combined pose.
- Retained UV **corners**, original material slots, sharp flags/custom normals,
  vertex groups and weights are preserved. New walls get a strip UV chart and the
  cap gets a disk chart, with a separate untextured interior material.
- New weights use the rim's verified source weights; the back vertex uses their
  convex average. All existing relative shape keys are extended from the same
  source provenance, including names/order, relative-key links and simple action.
- Unsupported drivers/NLA, absolute or masked keys, color/custom attributes, wire edges,
  constraints and non-armature modifiers block before editing; none are deleted.
- Candidate creation is transactional. Failed objects, meshes, keys and owned
  interior materials are cleaned up. CLI produces a JSON report only and never
  clears an artist's foreground scene, saves BLEND or exports GLB.

## Run tests

Pure Python (no third-party dependency):

```bash
python -m unittest discover -s facial_workbench/mouth_v3 \
  -p test_iskra_mouth_cut_plan.py -v
```

Real Blender integration, including a temporary synthetic GLB round trip with
21 targets and seven skeletal clips:

```bash
blender --background --factory-startup --threads 2 --python-exit-code 1 \
  --python facial_workbench/mouth_v3/run_iskra_mouth_blender_tests.py -- \
  base_basic_pbr.glb /tmp/iskra-mouth-tests.json
```

The round-trip GLB is created in a temporary directory and deleted. It is a test
fixture, not an edited Iskra asset. The real GLB is imported for source inspection
and a no-plan rejection/preservation check, not cut without an anatomical plan.

## Inspect the real input and prepare a draft

```bash
blender --background --factory-startup --python-exit-code 1 \
  --python facial_workbench/mouth_v3/iskra_mouth_mesh_analyzer.py -- \
  --input base_basic_pbr.glb --report /tmp/mouth-analysis.json \
  --mesh-json /tmp/mouth-mesh.json --draft-plan /tmp/mouth-plan.json
```

The analyzer identifies the skinned mesh and excludes Blender's Icosphere bone
helper. The draft deliberately has empty landmarks and `anatomy_reviewed:false`.
A model-specific anatomical review must supply exact loop/face/region indices,
upper/lower contiguous arcs, inward direction and depth ratio; it is not an
inference from texture, PCA, global boundaries or the name of a bone.

Validate the supplied plan without Blender:

```bash
python facial_workbench/mouth_v3/iskra_mouth_cut_plan.py \
  --mesh-json /tmp/mouth-mesh.json --plan /tmp/mouth-plan.json \
  --report /tmp/plan-check.json
```

The builder's report-only CLI is:

```bash
blender --background --factory-startup --python-exit-code 1 \
  --python facial_workbench/mouth_v3/iskra_mouth_builder_v3.py -- \
  --input base_basic_pbr.glb --plan /tmp/mouth-plan.json \
  --report /tmp/mouth-build.json
```

For authoring in an existing Blender script, `build(object, plan)` returns an
independent `(candidate_object, report)` only after validation. The caller owns
that object and must call `dispose(candidate_object)` when finished. No output
path is accepted by the builder. `CANDIDATE_VALIDATED` is not visual approval,
continuous morph-combination proof, Filament approval or an export manifest.

## Boundaries that remain deliberate

The actual `base_basic_pbr.glb` contains no morph targets; the legacy generator
creates 21. The repaired builder preserves and extends existing targets rather
than rerunning that generator, which would replace the target list.

The source/source-result maps describe **Blender topology before export**. They
must not be passed off as a GLB V2.3 manifest: export can split/reorder vertices.
A production exporter still needs a proven exported correspondence, a validator
contract that preserves all 21 targets, seven original animation data streams,
visual mouth/aperture checks and a real Android/Filament test. The full Work V2.3
validator is not present in this directory; the static audit is not its source.

See `REPAIR_VALIDATION.md` for actual results, input hashes and limitations.
