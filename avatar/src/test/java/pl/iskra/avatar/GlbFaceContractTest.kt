package pl.iskra.avatar

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject

class GlbFaceContractTest {
    private val source get() = File("../avatar-test/src/main/assets/avatar/iskra.glb").readBytes()
    private fun alteredJson(change: (JSONObject) -> Unit): ByteArray {
        val b = source; val n = ByteBuffer.wrap(b, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val j = JSONObject(String(b, 20, n, Charsets.UTF_8)); change(j)
        val text = j.toString().toByteArray(); val len = (text.size + 3) / 4 * 4
        val out = ByteBuffer.allocate(20 + len + b.size - 20 - n).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546c67).putInt(2).putInt(out.capacity()).putInt(len).putInt(0x4e4f534a)
        out.put(text); repeat(len - text.size) { out.put(32.toByte()) }; out.put(b, 20 + n, b.size - 20 - n)
        return out.array()
    }
    @Test fun exactCorrectedAssetContract() {
        val b = source
        assertEquals(AvatarSurfaceView.MODEL_SHA256, MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) })
        val c = GlbFaceContractReader.read(b)
        assertEquals(2, c.primitiveCount)
        assertEquals(AvatarController.MORPHS, c.names)
        assertEquals(AvatarController.ANIMATIONS, c.animationNames)
    }
    @Test fun openingJawMovesBothCheeksAndHeadIsASkinJoint() {
        val b = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLength = b.getInt(12)
        val gltf = JSONObject(String(b.array(), 20, jsonLength, Charsets.UTF_8))
        val mesh = gltf.getJSONArray("meshes").getJSONObject(0)
        val face = mesh.getJSONArray("primitives").getJSONObject(0)
        val jaw = face.getJSONArray("targets").getJSONObject(AvatarController.MORPHS.indexOf("jawOpen"))
        val pos = gltf.getJSONArray("accessors").getJSONObject(jaw.getInt("POSITION"))
        val view = gltf.getJSONArray("bufferViews").getJSONObject(pos.getInt("bufferView"))
        val offset = 28 + jsonLength + pos.optInt("byteOffset", 0) + view.optInt("byteOffset", 0)
        // Two landmarks above the corners of the lips on the reviewed mesh.
        for (vertex in listOf(12138, 52508)) {
            val down = b.getFloat(offset + vertex * 12 + 4)
            assertTrue("cheek $vertex should follow the jaw", down in -.009f..-.005f)
        }
        val joints = gltf.getJSONArray("skins").getJSONObject(0).getJSONArray("joints")
        val nodes = gltf.getJSONArray("nodes")
        assertTrue((0 until joints.length()).any { nodes.getJSONObject(joints.getInt(it)).optString("name") == "Head" })
    }
    @Test(expected = IllegalArgumentException::class) fun corruptHeaderRejected() {
        GlbFaceContractReader.read(source.apply { this[0] = 0 })
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedRejected() { GlbFaceContractReader.read(source.copyOf(40)) }
    @Test(expected = IllegalArgumentException::class) fun swappedMorphNamesRejected() {
        GlbFaceContractReader.read(alteredJson { j ->
            val names = j.getJSONArray("meshes").getJSONObject(0).getJSONObject("extras").getJSONArray("targetNames")
            names.put(0, "eyeBlinkRight"); names.put(1, "eyeBlinkLeft")
        })
    }
    @Test(expected = IllegalArgumentException::class) fun missingBodyAnimationRejected() {
        GlbFaceContractReader.read(alteredJson { it.getJSONArray("animations").remove(0) })
    }
    @Test(expected = IllegalArgumentException::class) fun nonNeutralMorphDefaultsRejected() {
        GlbFaceContractReader.read(alteredJson { it.getJSONArray("meshes").getJSONObject(0).getJSONArray("weights").put(9, 1) })
    }
    @Test(expected = IllegalArgumentException::class) fun differentCavityTargetsRejected() {
        GlbFaceContractReader.read(alteredJson {
            val target = it.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(1).getJSONArray("targets").getJSONObject(0)
            target.put("POSITION", target.getInt("POSITION") + 1)
        })
    }
    @Test(expected = IllegalArgumentException::class) fun differentCavityAttributesRejected() {
        GlbFaceContractReader.read(alteredJson {
            val attrs = it.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(1).getJSONObject("attributes")
            attrs.put("POSITION", attrs.getInt("POSITION") + 1)
        })
    }
    @Test(expected = IllegalArgumentException::class) fun missingMouthSchemaRejected() {
        GlbFaceContractReader.read(alteredJson {
            it.getJSONArray("meshes").getJSONObject(0).getJSONObject("extras").getJSONObject("iskraMouthRepair").put("schema", "unknown")
        })

    }
    @Test(expected = IllegalArgumentException::class) fun externalBufferCannotReadPrivateFiles() {
        GlbFaceContractReader.read(alteredJson { it.getJSONArray("buffers").getJSONObject(0).put("uri", "file:///private/model.bin") })
    }
    @Test(expected = IllegalArgumentException::class) fun externalImageCannotReachTheNetwork() {
        GlbFaceContractReader.read(alteredJson { it.getJSONArray("images").getJSONObject(0).put("uri", "https://remote.invalid/face.png") })
    }
}
