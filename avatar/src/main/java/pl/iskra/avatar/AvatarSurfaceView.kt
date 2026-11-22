package pl.iskra.avatar

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.ByteArrayOutputStream
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.PixelCopy
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import kotlin.math.cos
import kotlin.math.sin

/** Owns every native resource on the UI thread. A detached view may be attached again. */
class AvatarSurfaceView(context: Context) : SurfaceView(context) {
    val controller = AvatarController()
    var onStatus: (String) -> Unit = {}
    private val handler = Handler(Looper.getMainLooper())
    private var session: RenderSession? = null
    private var worker: ExecutorService? = null
    private var attachment = 0L
    private var resumed = false
    private var scheduled = false
    private var previousFrame = 0L
    private var azimuth = 0f
    private var closeup = false
    private var zoom = 1f
    private var hologram = false
    private var brightness = 1f
    private var modelRequest = 0L
    private var backgroundRequest = 0L
    private var selectedFile: File? = null
    private var lastWeights = FloatArray(AvatarController.MORPHS.size)
    private var nextDelay = 0L
    fun setZoom(value: Float) { checkMain(); require(value.isFinite() && value in .65f..1.8f); zoom=value; invalidatePreview() }
    fun setAppearance(value: Boolean, light: Float) { checkMain(); require(light.isFinite() && light in .2f..2f); hologram=value;brightness=light;invalidatePreview() }
    fun loadModel(file: File?, callback: (Boolean) -> Unit) {
        checkMain(); val request=++modelRequest; cancelCapture()
        val owner=session ?: run { callback(false);return }
        worker?.execute {
            try {
                val bytes=if(file==null) context.assets.open(ASSET_PATH).use { it.readBytes() } else file.inputStream().use { it.readBytes() }
                require(bytes.size in 20..157286400)
                if(file==null) require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}==MODEL_SHA256)
                val parsed=GlbFaceContractReader.read(bytes)
                handler.post { if(request==modelRequest && session===owner && isAttachedToWindow) {
                    try { owner.load(bytes,parsed,file==null);selectedFile=file;cancelCapture();schedule();callback(true) }
                    catch(_:Exception){onStatus("Nie udało się zmienić modelu. Zachowano poprzedni.");callback(false)}
                } }
            } catch(_:Exception) {handler.post { if(request==modelRequest){onStatus("Nieprawidłowy lub niedostępny model.");callback(false)} }}
        } ?: callback(false)
    }
    fun setSceneBackground(bitmap: Bitmap?, callback: (Boolean) -> Unit = {}) {
        checkMain();val request=++backgroundRequest
        val owner=session ?: run{bitmap?.recycle();callback(false);return}
        worker?.execute {
            var failed=false
            val bytes=try {bitmap?.let {b -> ByteArrayOutputStream().use {o -> check(b.compress(Bitmap.CompressFormat.PNG,100,o));BackgroundGlb.create(o.toByteArray())}}}
                catch(_:Exception){failed=true;null} catch(_:OutOfMemoryError){failed=true;null} finally{bitmap?.recycle()}
            handler.post {if(session===owner && isAttachedToWindow && request==backgroundRequest){
                if(failed)callback(false)else try{owner.background(bytes);invalidatePreview();callback(true)}catch(_:Exception){callback(false)}
            }}
        } ?: run{bitmap?.recycle();callback(false)}
    }
    data class Frame(val id: Long, val at: String, val state: AvatarController.Snapshot, val angle: Float, val face: Boolean)
    data class Preview(val bitmap: Bitmap, val frame: Frame)
    private class Capture(val callback: (Preview?, String?) -> Unit) { var done = false }
    private var capture: Capture? = null
    private var copyBusy = false
    private var copyOwner: Capture? = null
    private var frameId = 0L
    private var presented: Frame? = null
    fun lastFrame(): Frame? = presented
    fun rigJoints(): List<String> = session?.jointNames.orEmpty()
    fun rigMorphs(): List<String> = session?.faceNames.orEmpty()
    fun jointAngles(): Map<String, JointAngles> = session?.displayedAngles().orEmpty()
    fun jointPositions(): Map<String, FloatArray> = session?.positions().orEmpty()
    fun jointTargets(): Map<String, JointAngles> = session?.requestedAngles().orEmpty()
    fun poseJoints(values: Map<String, JointAngles>, duration: Int) {
        checkMain()
        session?.pose(values, duration)
        invalidatePreview()
    }
    fun resetJoints() { session?.resetPose(); invalidatePreview() }
    fun invalidatePreview() { checkMain(); cancelCapture(); unschedule();nextDelay=0;schedule() }
    /** Copies this SurfaceView only. Never reads the window, gallery or camera. */
    fun capturePreview(callback: (Preview?, String?) -> Unit) {
        checkMain()
        if (!resumed || !isAttachedToWindow || capture != null || copyBusy) { callback(null, "NOT_READY"); return }
        val request = Capture(callback); capture = request
        handler.postDelayed({
            if (!request.done) {
                request.done = true; if (capture === request) capture = null
                if (copyOwner === request) { copyOwner = null; copyBusy = false; schedule() }
                callback(null, "TIMEOUT")
            }
        }, 1500)
        schedule()
    }
    private fun cancelCapture() {
        capture?.let { if (!it.done) { it.done = true; it.callback(null, "STALE") } }
        capture = null; copyOwner = null; copyBusy = false; presented = null
    }
    private fun copyFrame(frame: Frame) {
        val request = capture ?: return
        if (request.done || !resumed || !isAttachedToWindow || width <= 0 || height <= 0) return
        val scale = minOf(1f, 640f / maxOf(width, height))
        val bitmap = try { Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888) }
        catch (_: Exception) { request.done = true; capture = null; request.callback(null, "COPY_FAILED"); return }
        catch (_: OutOfMemoryError) { request.done = true; capture = null; request.callback(null, "COPY_FAILED"); return }
        copyBusy = true; copyOwner = request
        try { PixelCopy.request(this, bitmap, { result ->
            if (copyOwner === request) { copyOwner = null; copyBusy = false }
            if (capture === request) capture = null
            if (request.done) bitmap.recycle()
            else {
                request.done = true
                if (result == PixelCopy.SUCCESS && resumed && isAttachedToWindow) request.callback(Preview(bitmap, frame), null)
                else { bitmap.recycle(); request.callback(null, if (!resumed || !isAttachedToWindow) "STALE" else "COPY_FAILED") }
            }
            schedule()
        }, handler) } catch (_: Exception) {
            if (copyOwner === request) { copyOwner = null; copyBusy = false }; if (capture === request) capture = null; bitmap.recycle()
            if (!request.done) { request.done = true; request.callback(null, "COPY_FAILED") }; schedule()
        }
    }
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(time: Long) {
            scheduled = false
            if (!resumed || !isAttachedToWindow) return
            try {
                val dt = if (previousFrame == 0L) 0f else ((time - previousFrame) / 1e9f).coerceAtLeast(0f)
                previousFrame = time
                val state = controller.frame(dt)
                if (session?.render(time, state, azimuth, closeup, zoom, hologram, brightness) == true) {
                    val frame = Frame(++frameId, java.time.Instant.now().toString(), state.copy(weights = state.weights.copyOf()), azimuth, closeup)
                    presented = frame
                    if (capture != null) copyFrame(frame)
                }
                val settling=state.weights.indices.any { kotlin.math.abs(state.weights[it]-lastWeights[it])>.001f }
                lastWeights=state.weights.copyOf()
                nextDelay=LabPresentation().frameDelay(session?.transitioning==true || state.phase!=AvatarController.Phase.IDLE || state.animation!=null && !state.bodyPaused,settling,capture!=null)
                if(!state.bodyPaused)nextDelay=minOf(nextDelay,65L)
                schedule()
            } catch (e: Exception) { fail(e) } catch (e: LinkageError) { fail(e) }
        }
    }
    fun setCamera(azimuthRadians: Float, face: Boolean) {
        checkMain(); require(azimuthRadians.isFinite() && azimuthRadians in -1.2f..1.2f)
        azimuth = azimuthRadians; closeup = face; invalidatePreview()
    }
    fun setResumed(value: Boolean) {
        checkMain(); resumed = value; previousFrame = 0
        if (value) schedule() else { cancelCapture(); unschedule(); controller.suspendPlayback() }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); checkMain()
        val generation = ++attachment
        onStatus("Ładowanie poprawionego modelu…")
        try {
            Filament.init(); Gltfio.init()
            val created = RenderSession(context)
            session = created
            created.initialize(this)
            worker = Executors.newSingleThreadExecutor()
            worker!!.execute {
                try {
                    val bytes = selectedFile?.readBytes() ?: context.assets.open(ASSET_PATH).use { it.readBytes() }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    require(selectedFile != null || hash == MODEL_SHA256) { "Nieprawidłowy SHA modelu" }
                    val contract = GlbFaceContractReader.read(bytes)
                    handler.post {
                        if (generation == attachment && isAttachedToWindow && session === created) {
                            try { created.load(bytes, contract,selectedFile==null); onStatus("Model gotowy · ${contract.names.size} ustawień twarzy · ${contract.joints.size} kości"); schedule() }
                            catch (e: Exception) { fail(e) } catch (e: LinkageError) { fail(e) }
                        }
                    }
                } catch (e: Exception) {
                    handler.post { if (generation == attachment && isAttachedToWindow) fail(e) }
                }
            }
            schedule()
        } catch (e: Exception) { fail(e) } catch (e: LinkageError) { fail(e) }
    }
    override fun onDetachedFromWindow() {
        ++attachment; ++modelRequest; ++backgroundRequest; cancelCapture(); unschedule(); worker?.shutdownNow(); worker = null
        controller.suspendPlayback(); previousFrame = 0
        releaseSession()
        super.onDetachedFromWindow()
    }
    private fun fail(e: Throwable) {
        cancelCapture(); unschedule(); ++attachment; worker?.shutdownNow(); worker = null; controller.suspendPlayback()
        releaseSession()
        onStatus("Nie udało się uruchomić awatara: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}")
    }
    private fun releaseSession() {
        val old = session; session = null
        try { old?.close() } catch (_: Exception) { onStatus("Błąd zwalniania zasobów awatara") }
    }
    private fun schedule() {
        if (resumed && isAttachedToWindow && session != null && !scheduled && !copyBusy) {
            scheduled = true; Choreographer.getInstance().postFrameCallbackDelayed(frame,nextDelay)
        }
    }
    private fun unschedule() { Choreographer.getInstance().removeFrameCallback(frame); scheduled = false }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
    companion object {
        const val ASSET_PATH = "avatar/iskra.glb"
        const val MODEL_SHA256 = "4fbcbdcec544b293d229fbd4e3fc82d9b9e608b1265c849f5334798fccd683c7"
    }
}

/** No global renderer and no ModelViewer detach listener: exactly one cleanup owner. */
private class RenderSession(context: Context) : AutoCloseable {
    private val engine = Engine.create()
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val display = DisplayHelper(context)
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: com.google.android.filament.View
    private lateinit var camera: Camera
    private var cameraEntity = 0
    private var rimLight = 0
    private var light = 0
    private var sky: Skybox? = null
    private var indirect: IndirectLight? = null
    private var materials: UbershaderProvider? = null
    private var loader: AssetLoader? = null
    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private var morphInstance = 0
    private var swapChain: SwapChain? = null
    private var selectedAnimation: String? = null
    private var animationIndex: Int? = null
    private val bodyPlayback = BodyPlayback()
    // Resting arm angles sampled from the first frame of this scan's HumanHappyIdle clip.
    private val restingArms = mapOf(
        "LeftShoulder" to JointAngles(8.5f,9.6f,-17.8f), "LeftArm" to JointAngles(4.3f,29f,17.7f),
        "LeftForeArm" to JointAngles(7.4f,-1.2f,-12.1f), "LeftHand" to JointAngles(-11.2f,-10.4f,9.3f),
        "RightShoulder" to JointAngles(1f,-9.8f,15.9f), "RightArm" to JointAngles(25.7f,-6.7f,-8.5f),
        "RightForeArm" to JointAngles(6.8f,.4f,5.6f), "RightHand" to JointAngles(-17.3f,-.7f,-23.9f))
    private var builtin = false
    private var idleStarted = 0L
    private val jointPose = JointPose()
    private var jointInstances = emptyMap<String, Int>()
    private val restored = linkedMapOf<Int, FloatArray>()
    private val jointOutput = FloatArray(16)
    private val savedBases = mutableMapOf<Int, FloatArray>()
    val jointNames get() = jointInstances.keys.toList()
    val faceNames get() = parsedContract?.names.orEmpty()
    var transitioning = false
        private set
    private var shownAngles = emptyMap<String, JointAngles>()
    fun displayedAngles() = shownAngles
    fun requestedAngles() = jointPose.targets()
    fun positions(): Map<String, FloatArray> {
        val matrix = FloatArray(16)
        return jointInstances.mapValues { (_, instance) ->
            engine.transformManager.getWorldTransform(instance, matrix)
            floatArrayOf(matrix[12],matrix[13],matrix[14])
        }
    }
    fun pose(values: Map<String, JointAngles>, duration: Int) {
        require(duration in 0..5000 && values.keys.all { it in jointInstances } && values.values.all { it.valid() })
        jointPose.set(values, duration, System.nanoTime(), shownAngles)
    }
    fun resetPose() { jointPose.clear() }
    private val bindTransforms = linkedMapOf<Int, FloatArray>()
    private var lastFrame = 0L
    private var closed = false
    private var parsedContract: GlbFaceContract? = null
    private var lastAppearance: Pair<Boolean,Float>? = null
    private var backgroundAsset: FilamentAsset? = null
    fun background(bytes: ByteArray?) {
        val next=bytes?.let {
            val b=ByteBuffer.allocateDirect(it.size).order(ByteOrder.nativeOrder()).put(it);b.flip()
            val candidate=checkNotNull(loader!!.createAsset(b))
            try {val resources=ResourceLoader(engine);try{resources.loadResources(candidate)}finally{resources.destroy()};candidate.releaseSourceData();candidate}
            catch(e:Exception){loader!!.destroyAsset(candidate);throw e}
        }
        backgroundAsset?.let {scene.removeEntities(it.entities);loader!!.destroyAsset(it)}
        backgroundAsset=next;next?.let {scene.addEntities(it.entities)}
    }

    fun initialize(surface: SurfaceView) {
        renderer = engine.createRenderer()
        scene = engine.createScene(); view = engine.createView()
        cameraEntity = EntityManager.get().create(); camera = engine.createCamera(cameraEntity)
        camera.setExposure(16f, 1f / 125f, 100f)
        view.scene = scene; view.camera = camera
        sky = Skybox.Builder().color(0f, 0f, 0f, 1f).build(engine)
        scene.skybox = sky
        val irradiance = FloatArray(27); irradiance[0] = .8f; irradiance[1] = .8f; irradiance[2] = .8f
        indirect = IndirectLight.Builder().irradiance(3, irradiance).intensity(20_000f).build(engine)
        scene.indirectLight = indirect
        light = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN).color(1f, .96f, .91f).intensity(66_000f)
            .direction(-.62f, -.54f, -.78f).castShadows(false).build(engine, light)
        scene.addEntity(light)
        rimLight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(.76f, .86f, 1f).intensity(16_000f)
            .direction(.6f, -.3f, .8f).castShadows(false).build(engine, rimLight)
        scene.addEntity(rimLight)
        materials = UbershaderProvider(engine)
        loader = AssetLoader(engine, materials!!, EntityManager.get())
        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }
            override fun onDetachedFromSurface() {
                display.detach()
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }; swapChain = null
            }
            override fun onResized(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    view.viewport = Viewport(0, 0, width, height)
                    camera.setProjection(40.0, width.toDouble() / height, .05, 20.0, Camera.Fov.VERTICAL)
                }
            }
        }
        ui.attachTo(surface)
        display.attach(renderer, surface.display)
    }
    fun load(bytes: ByteArray, parsed: GlbFaceContract, isBuiltin: Boolean) {
        check(!closed)
        val buffer=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes);buffer.flip()
        val created=checkNotNull(loader!!.createAsset(buffer)){"Nie można załadować GLB"}
        try {
            val resources=ResourceLoader(engine);try{resources.loadResources(created)}finally{resources.destroy()}
            val entity=created.getEntitiesByName(parsed.nodeName).single()
            val instance=engine.renderableManager.getInstance(entity)
            check(instance!=0 && engine.renderableManager.getPrimitiveCount(instance)==parsed.primitiveCount)
            check(created.getMorphTargetNames(entity).toList()==parsed.names)
            check(engine.renderableManager.getMorphTargetCount(instance)==parsed.names.size)
            val a=created.instance.animator
            check(List(a.animationCount){a.getAnimationName(it)}==parsed.animationNames)
            val transforms=engine.transformManager
            val nextBinds=linkedMapOf<Int,FloatArray>()
            for(id in (created.entities+intArrayOf(created.root)).distinct()){
                val i=transforms.getInstance(id);if(i!=0)nextBinds[i]=transforms.getTransform(i,FloatArray(16))
            }
            val joints = parsed.joints.mapNotNull { name ->
                val id = created.getEntitiesByName(name).singleOrNull() ?: return@mapNotNull null
                val joint = transforms.getInstance(id)
                if (joint == 0) null else name to joint
            }.toMap()
            a.updateBoneMatrices();engine.renderableManager.setMorphWeights(instance,FloatArray(parsed.names.size),0)
            created.releaseSourceData();scene.addEntities(created.entities)
            val previous=asset
            builtin=isBuiltin; idleStarted=System.nanoTime(); jointInstances = joints; jointPose.clear(); shownAngles=emptyMap(); restored.clear(); savedBases.clear()
            asset=created;animator=a;morphInstance=instance;parsedContract=parsed;lastAppearance=null
            bindTransforms.clear();bindTransforms.putAll(nextBinds);selectedAnimation=null;animationIndex=null;bodyPlayback.frame(null,0f,false)
            previous?.let{scene.removeEntities(it.entities);loader!!.destroyAsset(it)}
        } catch(e:Exception){if(asset!==created)loader!!.destroyAsset(created);throw e}
    }
    fun render(now: Long, state: AvatarController.Snapshot, angle: Float, closeup: Boolean, zoom: Float, hologram: Boolean, brightness: Float): Boolean {
        if (closed || !ui.isReadyToRender || asset == null) return false
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, .1f)
        lastFrame = now
        val body = bodyPlayback.frame(state.animation, dt, state.bodyPaused)
        val transforms = engine.transformManager
        var bonesChanged = restored.isNotEmpty() || body.restoreBindPose
        for ((instance, base) in restored) transforms.setTransform(instance, base)
        restored.clear()
        if (body.restoreBindPose) {
            for ((instance, transform) in bindTransforms) engine.transformManager.setTransform(instance, transform)
            selectedAnimation = body.animation
            animationIndex = animator?.let { a -> (0 until a.animationCount).firstOrNull { a.getAnimationName(it) == selectedAnimation } }
        }
        animator?.let { a -> animationIndex?.let { i ->
            val duration = a.getAnimationDuration(i)
            a.applyAnimation(i, if (duration > 0f) body.seconds % duration else 0f)
            bonesChanged = true
        } }
        val rotations = mutableMapOf<String,JointAngles>()
        if (builtin && state.animation == null) rotations.putAll(restingArms)
        // An explicit command takes precedence, including an intentional zero angle.
        rotations.putAll(jointPose.at(now))
        transitioning = jointPose.moving(now)
        // Layer a slow breath and the spoken accent on top of the sampled local joint frames.
        if (!state.bodyPaused && state.animation == null && "Spine2" in jointInstances) {
            val breath = (.50 * sin((now % 4_600_000_000L) / 4.6e9 * 2 * Math.PI)).toFloat()
            val spine = rotations["Spine2"] ?: JointAngles.ZERO
            rotations["Spine2"] = spine.copy(x = (spine.x + breath).coerceIn(-180f,180f))
        }
        if ("Head" in jointInstances && state.headPitchDegrees > .01f) {
            val head = rotations["Head"] ?: JointAngles.ZERO
            rotations["Head"] = head.copy(x = (head.x + state.headPitchDegrees).coerceIn(-180f,180f))
        }
        shownAngles = rotations
        for ((name, angles) in rotations) {
            if (angles == JointAngles.ZERO) continue
            val instance = jointInstances.getValue(name)
            val base = savedBases.getOrPut(instance) { FloatArray(16) }
            transforms.getTransform(instance, base)
            rotateJoint(base, angles, jointOutput)
            transforms.setTransform(instance, jointOutput)
            restored[instance] = base
            bonesChanged = true
        }
        if (bonesChanged) animator?.updateBoneMatrices()
        if (morphInstance != 0) {
            val weights=state.weights.copyOf(parsedContract!!.names.size)
            if (!state.bodyPaused) {
                val blink=naturalBlink((now-idleStarted).coerceAtLeast(0L))
                weights[0]=maxOf(weights[0],blink)
                weights[1]=maxOf(weights[1],blink*.97f)
            }
            engine.renderableManager.setMorphWeights(morphInstance,weights,0)
        }
        val distance = (if (closeup) .74 else 3.4) / zoom
        val targetY = if (closeup) 1.70 else .95
        camera.lookAt(distance * sin(angle), targetY + (if (closeup) .02 else .04), distance * cos(angle), 0.0, targetY, 0.0, 0.0, 1.0, 0.0)
        if(lastAppearance != (hologram to brightness)) {
            val rm=engine.renderableManager
            for(i in 0 until (parsedContract?.primitiveCount ?: 0)) {
                val m=rm.getMaterialInstanceAt(morphInstance,i)
                val c=parsedContract!!.materialColors.getOrNull(i) ?: floatArrayOf(1f,1f,1f,1f)
                val e=parsedContract!!.materialEmissions.getOrNull(i) ?: floatArrayOf(0f,0f,0f)
                if(m.material.hasParameter("baseColorFactor")) m.setParameter("baseColorFactor",Colors.RgbaType.LINEAR,
                    if(hologram && i==0).12f else c[0],if(hologram && i==0).7f else c[1],if(hologram && i==0)1f else c[2],c[3])
                if(m.material.hasParameter("emissiveFactor")) m.setParameter("emissiveFactor",Colors.RgbType.LINEAR,
                    if(hologram && i==0).05f*brightness else e[0],if(hologram && i==0).35f*brightness else e[1],if(hologram && i==0).8f*brightness else e[2])
            }
            lastAppearance=hologram to brightness
        }
        backgroundAsset?.let {bg ->
            val tm=engine.transformManager;val i=tm.getInstance(bg.root)
            if(i!=0) {val c=cos(angle);val s=sin(angle);val aspect=view.viewport.width.toFloat()/view.viewport.height.coerceAtLeast(1)
                tm.setTransform(i,floatArrayOf(c*aspect,0f,-s*aspect,0f,0f,1f,0f,0f,s,0f,c,0f,-5*s,targetY.toFloat(),-5*c,1f))}
        }
        swapChain?.let { if (renderer.beginFrame(it, now)) { renderer.render(view); renderer.endFrame(); return true } }
        return false
    }
    override fun close() {
        if (closed) return
        closed = true
        bindTransforms.clear()
        var failure: Throwable? = null
        fun attempt(block: () -> Unit) {
            try { block() } catch (t: Throwable) {
                if (failure == null) failure = t else failure!!.addSuppressed(t)
            }
        }
        attempt { ui.detach() }
        backgroundAsset?.let { b -> attempt {scene.removeEntities(b.entities)};attempt {loader?.destroyAsset(b)} };backgroundAsset=null
        asset?.let { a ->
            attempt { if (::scene.isInitialized) scene.removeEntities(a.entities) }
            attempt { loader?.destroyAsset(a) }
        }; asset = null
        attempt { loader?.destroy() }; loader = null
        materials?.let { m -> attempt { m.destroyMaterials() }; attempt { m.destroy() } }; materials = null
        if (::scene.isInitialized) { attempt { scene.indirectLight = null }; attempt { scene.skybox = null } }
        indirect?.let { attempt { engine.destroyIndirectLight(it) } }
        sky?.let { attempt { engine.destroySkybox(it) } }
        if (rimLight != 0) { attempt { engine.destroyEntity(rimLight) }; attempt { EntityManager.get().destroy(rimLight) } }
        if (light != 0) { attempt { engine.destroyEntity(light) }; attempt { EntityManager.get().destroy(light) } }
        if (::view.isInitialized) attempt { engine.destroyView(view) }
        if (::scene.isInitialized) attempt { engine.destroyScene(scene) }
        if (::renderer.isInitialized) attempt { engine.destroyRenderer(renderer) }
        if (cameraEntity != 0) {
            attempt { engine.destroyCameraComponent(cameraEntity) }; attempt { EntityManager.get().destroy(cameraEntity) }
        }
        attempt { engine.destroy() }
        failure?.let { throw IllegalStateException("Avatar resource cleanup failed", it) }
    }
}


/** Post-multiply the local joint by a pitch about its own pivot (column-major). */
internal fun pitchHead(base: FloatArray, degrees: Float, out: FloatArray) {
    val angle = Math.toRadians(degrees.toDouble())
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    base.copyInto(out)
    for (row in 0..3) {
        out[4 + row] = c * base[4 + row] + s * base[8 + row]
        out[8 + row] = c * base[8 + row] - s * base[4 + row]
    }
}

/** Alternate longer and shorter gaps, with quicker closure than reopening. */
internal fun naturalBlink(elapsedNanos: Long): Float {
    val ms = elapsedNanos / 1_000_000 % 9150
    val t = if (ms >= 8630) ms-8630 else ms-3750
    return when (t) {
        in 0..85 -> t/85f
        in 86..210 -> (210-t)/125f
        else -> 0f
    }
}
