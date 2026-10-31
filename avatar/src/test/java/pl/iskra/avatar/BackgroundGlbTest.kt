package pl.iskra.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackgroundGlbTest {
 @Test fun selectedImageIsEmbeddedWithBoundedAlignedBufferViews(){
  val image=ByteArray(101){it.toByte()};val b=BackgroundGlb.create(image);val h=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
  assertEquals(0x46546c67,h.int);assertEquals(2,h.int);assertEquals(b.size,h.int);val n=h.int;assertEquals(0x4e4f534a,h.int)
  val text=ByteArray(n);h.get(text);val j=JSONObject(String(text));assertFalse(j.getJSONArray("images").getJSONObject(0).has("uri"))
  assertEquals(j.getJSONArray("buffers").getJSONObject(0).getInt("byteLength"),h.int);assertEquals(0x004e4942,h.int)
  val bin=ByteArray(h.remaining());h.get(bin);assertArrayEquals(image,bin.copyOfRange(92,92+image.size));assertEquals(0,bin.size%4)
 }
 @Test fun absentAndOversizedImagesAreRefusedBeforeNativeLoading(){assertThrows(IllegalArgumentException::class.java){BackgroundGlb.create(ByteArray(1))};assertThrows(IllegalArgumentException::class.java){BackgroundGlb.create(ByteArray(16_777_217))}}
}
