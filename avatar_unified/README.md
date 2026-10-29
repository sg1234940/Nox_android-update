# Iskra Avatar Engine — unified candidate

This is a **source-only candidate**, not a compiled or Android-tested release.

Files:
- `GlbFaceContract.kt` — strict GLB 2.0 JSON contract for 21 morph targets and seven animations.
- `IskraFaceMotion.kt` — pure Kotlin face state and independent viseme cross-fade.
- `IskraLipSync.kt` — validated viseme cues sampled against real audio playback position.
- `AvatarEngine.kt` — Filament resource lifecycle, single-owner thread, body animation, face updates.

Install together in `iskra/src/main/java/pl/iskra/app/avatar/`. Requires Filament 1.77.3 and Android module. Run Gradle compile and actual-device tests before merging. No repository files were modified.

**Important:** The model used to inspect the contract is `iskra_avatar_facial_rig.glb` from the conversation. Its `jawOpen` morph has already failed the visual test (chin stretching). This code cannot fix that geometry. The V2 Blender generator with different morph names is not compatible. Do not ship until a corrected GLB passes visual inspection and exact contract verification.

**API verification needed:** Filament 1.77.3 Java signatures for `getMorphTargetNames`, `getMorphTargetCount`, `AssetLoader`, and `ResourceLoader`. Confirm `releaseSourceData()` timing and native error behavior. Source-only static checks are not a substitute for compilation.

**Concurrency and lifecycle:** Create and use on the Filament owning thread; call `update(nowNanos, audioPositionMs)` once per rendered frame. Audio position must come from the actual player. `close()` before destroying the Engine.

**Scope limitation:** No voice phoneme aligner, no camera/renderer/surface layer, no corrected GLB, no Android test results. Manual morph overrides are intentionally not exposed; the `FaceState` is the single source of truth.
