package pl.iskra.avatar

import android.content.Context
import android.graphics.Bitmap
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
    private var closeup = true
    data class Frame(val id: Long, val at: String, val state: AvatarController.Snapshot, val angle: Float, val face: Boolean)
    data class Preview(val bitmap: Bitmap, val frame: Frame)
    private class Capture(val callback: (Preview?, String?) -> Unit) { var done = false }
    private var capture: Capture? = null
    private var copyBusy = false
    private var copyOwner: Capture? = null
    private var frameId = 0L
    private var presented: Frame? = null
    fun lastFrame(): Frame? = presented
    fun invalidatePreview() { checkMain(); cancelCapture(); schedule() }
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
                if (session?.render(time, state, azimuth, closeup) == true) {
                    val frame = Frame(++frameId, java.time.Instant.now().toString(), state.copy(weights = state.weights.copyOf()), azimuth, closeup)
                    presented = frame
                    if (capture != null) copyFrame(frame)
                }
                schedule()
            } catch (e: Exception) { fail(e) } catch (e: LinkageError) { fail(e) }
        }
    }
    fun setCamera(azimuthRadians: Float, face: Boolean) {
        checkMain(); require(azimuthRadians.isFinite() && azimuthRadians in -1.2f..1.2f)
        azimuth = azimuthRadians; closeup = face
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
                    val bytes = context.assets.open(ASSET_PATH).use { it.readBytes() }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    require(hash == MODEL_SHA256) { "Nieprawidłowy SHA modelu" }
                    val contract = GlbFaceContractReader.read(bytes)
                    handler.post {
                        if (generation == attachment && isAttachedToWindow && session === created) {
                            try { created.load(bytes, contract); onStatus("Model gotowy · 21 morph targets · 7 animacji"); schedule() }
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
        ++attachment; cancelCapture(); unschedule(); worker?.shutdownNow(); worker = null
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
            scheduled = true; Choreographer.getInstance().postFrameCallback(frame)
        }
    }
    private fun unschedule() { Choreographer.getInstance().removeFrameCallback(frame); scheduled = false }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
    companion object {
        const val ASSET_PATH = "avatar/iskra.glb"
        const val MODEL_SHA256 = "451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c"
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
    private val bindTransforms = linkedMapOf<Int, FloatArray>()
    private var lastFrame = 0L
    private var closed = false

    fun initialize(surface: SurfaceView) {
        renderer = engine.createRenderer()
        scene = engine.createScene(); view = engine.createView()
        cameraEntity = EntityManager.get().create(); camera = engine.createCamera(cameraEntity)
        camera.setExposure(16f, 1f / 125f, 100f)
        view.scene = scene; view.camera = camera
        sky = Skybox.Builder().color(.055f, .075f, .11f, 1f).build(engine)
        scene.skybox = sky
        val irradiance = FloatArray(27); irradiance[0] = .8f; irradiance[1] = .8f; irradiance[2] = .8f
        indirect = IndirectLight.Builder().irradiance(3, irradiance).intensity(25_000f).build(engine)
        scene.indirectLight = indirect
        light = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN).color(1f, .96f, .91f).intensity(70_000f)
            .direction(-.3f, -.8f, -1f).castShadows(false).build(engine, light)
        scene.addEntity(light)
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
    fun load(bytes: ByteArray, parsed: GlbFaceContract) {
        check(!closed && asset == null)
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.flip()
        val created = checkNotNull(loader!!.createAsset(buffer)) { "Nie można załadować GLB" }
        asset = created // cleanup owner established before resource upload / validation
        val resources = ResourceLoader(engine)
        try { resources.loadResources(created) } finally { resources.destroy() }
        val entity = created.getEntitiesByName(parsed.nodeName).single()
        morphInstance = engine.renderableManager.getInstance(entity)
        check(morphInstance != 0)
        check(engine.renderableManager.getPrimitiveCount(morphInstance) == parsed.primitiveCount)
        check(created.getMorphTargetNames(entity).toList() == parsed.names)
        check(engine.renderableManager.getMorphTargetCount(morphInstance) == parsed.names.size)
        val a = created.instance.animator
        check(List(a.animationCount) { a.getAnimationName(it) } == parsed.animationNames)
        val transforms = engine.transformManager
        for (id in (created.entities + intArrayOf(created.root)).distinct()) {
            val instance = transforms.getInstance(id)
            if (instance != 0) bindTransforms[instance] = transforms.getTransform(instance, FloatArray(16))
        }
        animator = a; selectedAnimation = null; a.updateBoneMatrices()
        engine.renderableManager.setMorphWeights(morphInstance, FloatArray(parsed.names.size), 0)
        created.releaseSourceData()
        scene.addEntities(created.entities)
    }
    fun render(now: Long, state: AvatarController.Snapshot, angle: Float, closeup: Boolean): Boolean {
        if (closed || !ui.isReadyToRender || asset == null) return false
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, .1f)
        lastFrame = now
        val body = bodyPlayback.frame(state.animation, dt, state.bodyPaused)
        if (body.restoreBindPose) {
            for ((instance, transform) in bindTransforms) engine.transformManager.setTransform(instance, transform)
            selectedAnimation = body.animation
            animationIndex = animator?.let { a -> (0 until a.animationCount).firstOrNull { a.getAnimationName(it) == selectedAnimation } }
            animator?.updateBoneMatrices()
        }
        animator?.let { a -> animationIndex?.let { i ->
            val duration = a.getAnimationDuration(i)
            a.applyAnimation(i, if (duration > 0f) body.seconds % duration else 0f); a.updateBoneMatrices()
        } }
        if (morphInstance != 0) engine.renderableManager.setMorphWeights(morphInstance, state.weights, 0)
        val distance = if (closeup) .74 else 3.4
        val targetY = if (closeup) 1.70 else .95
        camera.lookAt(distance * sin(angle), targetY + (if (closeup) .02 else .04), distance * cos(angle), 0.0, targetY, 0.0, 0.0, 1.0, 0.0)
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
        asset?.let { a ->
            attempt { if (::scene.isInitialized) scene.removeEntities(a.entities) }
            attempt { loader?.destroyAsset(a) }
        }; asset = null
        attempt { loader?.destroy() }; loader = null
        materials?.let { m -> attempt { m.destroyMaterials() }; attempt { m.destroy() } }; materials = null
        if (::scene.isInitialized) { attempt { scene.indirectLight = null }; attempt { scene.skybox = null } }
        indirect?.let { attempt { engine.destroyIndirectLight(it) } }
        sky?.let { attempt { engine.destroySkybox(it) } }
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
