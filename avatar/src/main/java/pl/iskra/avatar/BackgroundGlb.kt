package pl.iskra.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A self-contained unlit background quad. Image bytes come only from the owner's selected bitmap. */
object BackgroundGlb {
    fun create(png: ByteArray): ByteArray {
        require(png.size in 8..16_777_216)
        val imageSize = (png.size + 3) / 4 * 4
        val bin = ByteBuffer.allocate(48 + 32 + 12 + imageSize).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floatArrayOf(-10f,-10f,0f,10f,-10f,0f,10f,10f,0f,-10f,10f,0f)) bin.putFloat(f)
        for (f in floatArrayOf(0f,1f,1f,1f,1f,0f,0f,0f)) bin.putFloat(f)
        for (i in shortArrayOf(0,1,2,0,2,3)) bin.putShort(i)
        bin.put(png)
        val json = """{"asset":{"version":"2.0"},"extensionsUsed":["KHR_materials_unlit"],"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"TEXCOORD_0":1},"indices":2,"material":0}]}],"materials":[{"doubleSided":true,"extensions":{"KHR_materials_unlit":{}},"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"metallicFactor":0,"roughnessFactor":1}}],"textures":[{"source":0}],"images":[{"bufferView":3,"mimeType":"image/png"}],"buffers":[{"byteLength":${bin.capacity()}}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48,"target":34962},{"buffer":0,"byteOffset":48,"byteLength":32,"target":34962},{"buffer":0,"byteOffset":80,"byteLength":12,"target":34963},{"buffer":0,"byteOffset":92,"byteLength":${png.size}}],"accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[-10,-10,0],"max":[10,10,0]},{"bufferView":1,"componentType":5126,"count":4,"type":"VEC2"},{"bufferView":2,"componentType":5123,"count":6,"type":"SCALAR"}]}""".toByteArray()
        val jSize=(json.size+3)/4*4
        return ByteBuffer.allocate(12+8+jSize+8+bin.capacity()).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546c67);putInt(2);putInt(capacity());putInt(jSize);putInt(0x4e4f534a);put(json)
            repeat(jSize-json.size){put(32.toByte())};putInt(bin.capacity());putInt(0x004e4942);put(bin.array())
        }.array()
    }
}
