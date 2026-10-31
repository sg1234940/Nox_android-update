package pl.iskra.avatar

import org.junit.Assert.*
import org.junit.Test

class LabPresentationTest {
 @Test fun fullBodyPolishAndNormalAreDefaults(){val p=LabPresentation().checked();assertFalse(p.face);assertFalse(p.hologram);assertEquals("pl",p.inputLanguage);assertEquals("pl",p.outputLanguage)}
 @Test fun textHasPriorityOverMicState(){val p=LabPresentation();assertEquals("SEND",p.action(true,true));assertEquals("STOP",p.action(false,true));assertEquals("MIC",p.action(false,false))}
 @Test fun idleThrottlesButCaptureAndSpeechRemainResponsive(){val p=LabPresentation();assertEquals(200L,p.frameDelay(false,false,false));assertEquals(16L,p.frameDelay(true,false,false));assertEquals(16L,p.frameDelay(false,true,false));assertEquals(16L,p.frameDelay(false,false,true))}
 @Test fun malformedValuesCannotBecomeAPersistedProfile(){for(p in listOf(LabPresentation(zoom=Float.NaN),LabPresentation(brightness=3f),LabPresentation(model="../escape"),LabPresentation(inputLanguage="not a tag"),LabPresentation(lengthScale=2f)))assertThrows(IllegalArgumentException::class.java){p.checked()}}
}
