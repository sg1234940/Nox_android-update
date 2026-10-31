package pl.iskra.avatar

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Strict, pre-gltfio validation of the one-mesh Iskra GLB contract. */
data class GlbFaceContract(
    val nodeName: String,
    val names: List<String>,
    val animationNames: List<String>,
    val primitiveCount: Int = 1,
) {
    val indices: Map<String, Int> = names.withIndex().associate { it.value to it.index }
}

object GlbFaceContractReader {
    const val SCHEMA = "ISKRA_facial_rig/2.0"
    val EXPECTED = listOf(
        "eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight", "eyesUp", "eyesDown",
        "browInnerUp", "browDownLeft", "browDownRight", "jawOpen", "mouthSmile",
        "mouthFrown", "mouthFunnel", "mouthPucker", "viseme_MBP", "viseme_FV",
        "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U"
    )
    val REQUIRED_ANIMATIONS = setOf(
        "HumanDancingTwerk", "HumanForwardJump", "HumanHappyIdle",
        "HumanHipHopDancing2", "HumanJoyfulJump", "HumanOffensiveIdle", "HumanSitting3"
    )

    fun read(bytes: ByteArray, nodeName: String = "model"): GlbFaceContract {
        require(bytes.size >= 20) { "GLB truncated" }
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(header.int == 0x46546c67 && header.int == 2) { "Not GLB 2.0" }
        require(header.int == bytes.size) { "GLB length mismatch" }
        val jsonLength = header.int
        require(header.int == 0x4e4f534a) { "Missing JSON chunk" }
        require(jsonLength > 0 && jsonLength % 4 == 0 && jsonLength <= header.remaining()) { "Invalid JSON length" }
        val jsonBytes = ByteArray(jsonLength)
        header.get(jsonBytes)
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8).trimEnd(' ', '\u0000', '\n', '\r', '\t'))
        val nodes = root.getJSONArray("nodes")
        val meshes = root.getJSONArray("meshes")
        val matches = (0 until nodes.length()).filter { nodes.getJSONObject(it).optString("name") == nodeName }
        require(matches.size == 1) { "Expected one node '$nodeName', found ${matches.size}" }
        val node = nodes.getJSONObject(matches.single())
        val meshId = node.getInt("mesh")
        require(meshId in 0 until meshes.length()) { "Bad mesh index" }
        val mesh = meshes.getJSONObject(meshId)
        val extras = mesh.getJSONObject("extras")
        require(extras.getJSONObject("iskraFacialRig").getString("schema") == SCHEMA) { "Wrong facial schema" }
        val nameArray = extras.getJSONArray("targetNames")
        val names = (0 until nameArray.length()).map { nameArray.getString(it) }
        require(names == EXPECTED) { "Morph target order/names mismatch" }
        val primitives = mesh.getJSONArray("primitives")
        require(primitives.length() in 1..2) { "Unsupported primitive count" }
        if (primitives.length() == 2) {
            require(extras.getJSONObject("iskraMouthRepair").getString("schema") == "ISKRA_MOUTH_SEAM/1") { "Unverified mouth primitive" }
            val skin = primitives.getJSONObject(0)
            val cavity = primitives.getJSONObject(1)
            // Both primitives belong to the same morph entity and index the SAME
            // vertex / target arrays. One setMorphWeights call applies to both.
            require(skin.getJSONObject("attributes").toString() == cavity.getJSONObject("attributes").toString()) { "Cavity attribute mapping mismatch" }
            require(skin.getJSONArray("targets").toString() == cavity.getJSONArray("targets").toString()) { "Cavity target mapping mismatch" }
            require(skin.getInt("material") != cavity.getInt("material")) { "Cavity needs a separate material" }
        }
        val targets = primitives.getJSONObject(0).getJSONArray("targets")
        require(targets.length() == names.size) { "Morph primitive count mismatch" }
        for (i in 0 until targets.length()) require(targets.getJSONObject(i).has("POSITION")) { "Missing POSITION morph $i" }
        val weights = mesh.getJSONArray("weights")
        require(weights.length() == names.size) { "Default weights count mismatch" }
        for (i in 0 until weights.length()) require(weights.getDouble(i).isFinite() && weights.getDouble(i) == 0.0) { "Non-neutral morph default" }
        val animations = root.getJSONArray("animations")
        val animationNames = (0 until animations.length()).map { animations.getJSONObject(it).getString("name") }
        require(animationNames.size == animationNames.toSet().size && animationNames.toSet().containsAll(REQUIRED_ANIMATIONS)) { "Body animation contract mismatch" }
        return GlbFaceContract(nodeName, names, animationNames, primitives.length())
    }
}
