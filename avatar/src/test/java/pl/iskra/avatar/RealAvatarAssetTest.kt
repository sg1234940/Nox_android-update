package pl.iskra.avatar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class RealAvatarAssetTest {
    @Test fun packagedCandidatePreservesTheRendererContract() {
        val asset = File("../avatar-test/src/main/assets/avatar/iskra.glb")
        val contract = GlbFaceContractReader.read(asset.readBytes())
        assertEquals(21, contract.names.size)
        assertEquals(7, contract.animationNames.size)
        assertEquals(2, contract.primitiveCount)
    }
}
