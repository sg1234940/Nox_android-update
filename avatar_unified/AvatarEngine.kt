package pl.iskra.app.avatar

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One owner thread; GLB validation and transactional loading; no Surface ownership. */
class AvatarEngine(
    context: Context,
    private val engine: Engine,
    private val scene: Scene,
    private val assetPath: String = "avatar/iskra_avatar_facial_rig.glb",
    private val meshName: String = "model",
) : AutoCloseable {
    companion object { init { Gltfio.init() } }
    private val owner = Thread.currentThread()
    private val appContext = context.applicationContext
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private var renderable = 0
    private var contract: GlbFaceContract? = null
    private var motion: IskraFaceMotion? = null
    private val speech = IskraLipSync()
    private var lastTickNanos: Long? = null
    private var animationIndex: Int? = null
    private var animationStartNanos = 0L
    private var looping = true
    private var closed = false

    private fun onOwner() { check(Thread.currentThread() === owner) { "Filament owner thread violation" } }
    private fun open() { onOwner(); check(!closed) { "AvatarEngine closed" } }
    private fun ready(): IskraFaceMotion { open(); return checkNotNull(motion) { "Avatar not loaded" } }
    val isLoaded get() = asset != null
    val animationCount get() = animator?.animationCount ?: 0

    fun load() {
        open(); check(asset == null) { "Already loaded" }
        val bytes = appContext.assets.open(assetPath).use { it.readBytes() }
        val parsed = GlbFaceContractReader.read(bytes, meshName)
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes).flip()
        val created = checkNotNull(loader.createAsset(buffer)) { "Could not create GLB asset" }
        var sceneAttempted = false
        try {
            val resourceLoader = ResourceLoader(engine)
            try { resourceLoader.loadResources(created) } finally { resourceLoader.destroy() }
            // Filament may still reference source data until resource upload finishes.
            created.releaseSourceData()
            val matches = created.getEntitiesByName(meshName)
            check(matches.size == 1) { "Expected exactly one named morph entity" }
            val entity = matches.single()
            val instance = engine.renderableManager.getInstance(entity)
            check(instance != 0) { "Named node has no renderable" }
            val runtimeNames = created.getMorphTargetNames(entity).toList()
            check(runtimeNames == parsed.names) { "Runtime morph names/order differ from GLB" }
            check(engine.renderableManager.getMorphTargetCount(instance) == parsed.names.size) { "Runtime morph count mismatch" }
            val a = created.instance.animator
            val runtimeAnimations = List(a.animationCount) { a.getAnimationName(it) }
            check(runtimeAnimations == parsed.animationNames) { "Runtime animation mismatch" }
            val face = IskraFaceMotion(parsed)
            engine.renderableManager.setMorphWeights(instance, face.weights(), 0)
            sceneAttempted = true
            scene.addEntities(created.entities)
            asset = created; animator = a; renderable = instance; contract = parsed; motion = face
            lastTickNanos = null
        } catch (failure: Throwable) {
            if (sceneAttempted) try { scene.removeEntities(created.entities) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            try { loader.destroyAsset(created) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    fun setFaceTarget(state: IskraFaceMotion.State) { ready().setTarget(state) }

    fun setFaceImmediately(state: IskraFaceMotion.State) {
        val face = ready(); val candidate = face.encode(state)
        engine.renderableManager.setMorphWeights(renderable, candidate, 0)
        face.setImmediate(state)
    }

    fun startLipSync(cues: List<IskraLipSync.Cue>) { ready(); speech.start(cues) }
    fun stopLipSync() { ready(); speech.stop() }

    /** Pass current audio player position every rendered frame; null when no audio is playing. */
    fun update(nowNanos: Long, audioPositionMs: Long? = null) {
        val face = ready()
        val previous = lastTickNanos ?: nowNanos
        val dt = ((nowNanos - previous).coerceAtLeast(0L) / 1e9).toFloat().coerceAtMost(0.25f)
        lastTickNanos = nowNanos
        val a = animator
        if (a != null && animationIndex != null) {
            val index = animationIndex!!
            val duration = a.getAnimationDuration(index)
            val elapsed = ((nowNanos - animationStartNanos).coerceAtLeast(0L) / 1e9).toFloat()
            val time = if (duration <= 0f) 0f else if (looping) elapsed % duration else elapsed.coerceAtMost(duration)
            a.applyAnimation(index, time); a.updateBoneMatrices()
            if (!looping && elapsed >= duration) animationIndex = null
        }
        if (audioPositionMs != null) {
            val (visemes, jaw) = speech.sample(audioPositionMs)
            face.setTarget(face.target.copy(visemes = visemes, jawOpen = jaw))
        }
        val (next, candidate) = face.candidate(dt)
        if (!candidate.contentEquals(face.weights())) {
            engine.renderableManager.setMorphWeights(renderable, candidate, 0)
            face.commit(next, candidate)
        }
    }

    fun playAnimation(name: String, nowNanos: Long, loop: Boolean = true) {
        ready()
        val a = checkNotNull(animator)
        val index = (0 until a.animationCount).firstOrNull { a.getAnimationName(it) == name }
            ?: throw IllegalArgumentException("Unknown animation $name")
        animationIndex = index; animationStartNanos = nowNanos; looping = loop
    }
    fun stopAnimation() { ready(); animationIndex = null }
    fun animationNames(): List<String> { ready(); val a = checkNotNull(animator); return List(a.animationCount) { a.getAnimationName(it) } }
    fun weights(): FloatArray = ready().weights()

    override fun close() {
        onOwner(); if (closed) return
        closed = true
        val existing = asset
        asset = null; animator = null; contract = null; motion = null; renderable = 0
        var failure: Throwable? = null
        if (existing != null) {
            try { scene.removeEntities(existing.entities) } catch (t: Throwable) { failure = t }
            try { loader.destroyAsset(existing) } catch (t: Throwable) { if (failure == null) failure = t else failure.addSuppressed(t) }
        }
        try { loader.destroy() } catch (t: Throwable) { if (failure == null) failure = t else failure.addSuppressed(t) }
        try { materials.destroyMaterials(); materials.destroy() } catch (t: Throwable) { if (failure == null) failure = t else failure.addSuppressed(t) }
        if (failure != null) throw failure
    }
}
