package pl.iskra.avatar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class RealAvatarAssetTest {
    @Test fun packagedCandidatePreservesTheRendererContract() {
        val asset = File("../avatar-test/src/main/assets/avatar/iskra.glb")
        val contract = GlbFaceContractReader.read(asset.readBytes())
        assertEquals(25, contract.names.size)
        assertEquals(52, contract.joints.size)
        assertEquals(true, contract.joints.containsAll(listOf("Head","Neck","RightHandIndex3","LeftToeBase")))
        assertEquals(true, contract.names.containsAll(listOf("cheekRaiseLeft","cheekRaiseRight","foreheadLiftLeft","foreheadLiftRight")))
        assertEquals(7, contract.animationNames.size)
        assertEquals(2, contract.primitiveCount)
    }
}
