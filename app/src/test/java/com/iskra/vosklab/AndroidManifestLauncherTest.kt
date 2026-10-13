package com.iskra.vosklab

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidManifestLauncherTest {

    @Test
    fun testLauncherActivityDeclarationInManifest() {
        var manifestFile = File("src/main/AndroidManifest.xml")
        if (!manifestFile.exists()) {
            manifestFile = File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val manifestContent = manifestFile.readText()

        assertTrue(
            "Manifest must declare launcher activity com.iskra.vosklab.MainActivity",
            manifestContent.contains("com.iskra.vosklab.MainActivity")
        )

        assertTrue(
            "Manifest activity must contain android.intent.action.MAIN",
            manifestContent.contains("android.intent.action.MAIN")
        )

        assertTrue(
            "Manifest activity must contain android.intent.category.LAUNCHER",
            manifestContent.contains("android.intent.category.LAUNCHER")
        )
    }
}
