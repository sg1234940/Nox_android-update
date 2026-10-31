# Iskra Avatar Lab 0.5: native control and visual feedback

This is a standalone laboratory, not an integration into the production Iskra APK. The app connects to the same Iskra gateway and identity; it contains no provider key, second model, memory store or avatar text-directive parser. Application id remains `pl.iskra.avatar.test.v4` so 0.5 updates the separate 0.4 app. The original 0.3 app can remain installed.

## Phone test

First use **Sprawdź podgląd lokalnie**: the dialog must show only the rendered avatar, with current face/body/camera. This does not send a request. Check neutral, smile, open mouth, a body animation, face/full-body views and pause/resume/background.

After the server candidate is reviewed, deployed and `ISKRA_AVATAR_LAB_V1=on`, write a normal message and press **Wyślij do Iskry**. The request itself enables avatar capability for that foreground attempt. Example: “Uśmiechnij się lekko, obejrzyj efekt i jeśli trzeba skoryguj. Na końcu powiedz, co zrobiłaś.” Iskra decides the settings and whether to request a preview. No special wording is required by the router.

Until server activation, the client reports the missing capability before posting a model request. It does not claim a connected avatar or silently use a different provider. Default gateway URL is the production app's existing URL; it can be changed for a review/test server.

## Contract

One `avatar_control` tool: `action=SET|STATE|RESET`, optional `weights` (21 existing channels, 0–1), `animation` (7 existing clips or null), `body_paused`, `camera={face,angle}`, `preview` (default true). SET changes only specified fields. RESET restores neutral face/body. There is no independent hand/joint posing or real TTS lip synchronization yet; the speech controls remain a simulation.

Commands arrive only in a completed `done.avatar_request` with a server-issued `phone/v1` receipt. The client returns the original call id, args and a tool result under the same conversation/revision. Partial text, an incomplete stream, mixed/unbound requests and stale/background attempts cannot execute. A request-local 16-entry cache does not evict old effects; conflicting call-id reuse is refused. New manual input cancels the old attempt. A network failure never automatically retries a POST or implies rollback.

Validation precedes UI mutations. Frames use the controller's existing opening budget. `requested` is the desired state; `frame.pose` is the actual submitted state, including smoothed/budgeted weights. PixelCopy copies only AvatarSurfaceView, after settling and a successful render submission. Rendering is held during the copy. Preview timeout/cancellation resumes rendering; failed capture is separate from a movement that already applied.

Images are JPEG, max 640 px / 240 KiB, with exact byte hash, dimensions and frame metadata. They stay in the current attempt. No gallery, camera, microphone, whole-screen capture or file sharing is used. Local previews are recycled on dismissal/cancellation.

The server validates the receipt and result. Only sealed image results are projected into native vision parts at the provider boundary, with explicit tool provenance. The transient provider image envelope is not a new message from Sławek and is not written as a user turn in RAM/history. No base64 image becomes prompt text. Existing identity, permissions, RAM, QMAPA and ordinary clients retain their contracts.

## Build / reviewed asset

JDK17, Gradle8.13, Android SDK36/build-tools36.0.0, Filament1.66.0. Run Gradle from this standalone settings root; the production application's settings/dependencies are unchanged.

The unchanged v0.4 GLB is distributed in the test APK, not duplicated as a large Git blob. Obtain the APK artifact from the task and run:

```
python avatar-lab/install_asset.py /path/Iskra_Avatar_Test_v0.5.apk
gradle -p avatar-lab :avatar:testDebugUnitTest :avatar-test:testDebugUnitTest :avatar-test:assembleDebug
```

Model SHA256: `451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c`. Asset page: https://chatgpt.com/library?file_id=libfile_50ffd8cd0b388191ac243db6ee7d500a&account_id=personal

## Evidence and limits

JVM checks cover controller/state, original GLB contract, all command names/limits, requested vs rendered pose, replay budget/order and real local HTTPS continuation/no-retry/finality behavior. Server tests exercise an actual local HTTP streaming route with scripted provider: command → phone reply containing a 180665-byte JPEG → native vision → another command → final. This does not prove the production model's choices or physical Android PixelCopy/Filament behavior. Those remain phone/runtime tests. No server merge, deployment, flag activation or production APK change is included in this candidate.
