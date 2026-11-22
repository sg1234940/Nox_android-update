package pl.iskra.avatar.test

import android.app.Application
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.iskra.avatar.LabPresentation
import pl.iskra.avatar.LabVoice

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class ConversationScreenTest {
    // Exercise the production activity without opening a real microphone or GPU surface.
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible=true }

    @Test fun hiddenTextStaysHiddenOnIncomingReplyFocusAndAfterReopen() {
        val window=Robolectric.buildActivity(AvatarTestActivity::class.java).create().start().resume()
        val a=window.get()
        val toggle=field(a,"chatToggle").get(a) as android.widget.Button
        val transcript=field(a,"transcript").get(a) as ScrollView
        toggle.performClick()
        a.javaClass.getDeclaredMethod("bubble",String::class.java,Boolean::class.javaPrimitiveType)
            .apply { isAccessible=true }.invoke(a,"Cała odpowiedź",false)
        val input=field(a,"input").get(a) as EditText
        input.onFocusChangeListener.onFocusChange(input,true)
        assertEquals(View.GONE,transcript.visibility)
        toggle.performClick()
        assertEquals(View.VISIBLE,transcript.visibility)
        val bubbles=field(a,"bubbles").get(a) as android.view.ViewGroup
        assertEquals("Cała odpowiedź",(bubbles.getChildAt(0) as android.widget.TextView).text)
        toggle.performClick()
        window.pause().stop().destroy()
        val next=Robolectric.buildActivity(AvatarTestActivity::class.java).create().start().resume()
        try {assertEquals(View.GONE,(field(next.get(),"transcript").get(next.get()) as ScrollView).visibility)}
        finally {next.pause().stop().destroy()}
    }

    @Test fun focusingTheComposerOrOpeningTheChatDrawerKeepsThePendingRequest() {
        val window=Robolectric.buildActivity(AvatarTestActivity::class.java).create().start().resume()
        try {
            val activity=window.get()
            val transport=requireNotNull(field(activity,"lab").get(activity))
            val busy=field(transport,"busy")
            busy.setBoolean(transport,true)
            val input=field(activity,"input").get(activity) as EditText
            input.onFocusChangeListener.onFocusChange(input,true)
            assertTrue("focus cancelled the pending reply",busy.getBoolean(transport))
            assertEquals(View.VISIBLE,(field(activity,"transcript").get(activity) as ScrollView).visibility)
            val mic=field(activity,"action").get(activity) as ImageButton
            assertFalse("mic must not replace an in-flight response",mic.isEnabled)
            input.setText("Następne pytanie")
            assertTrue(busy.getBoolean(transport))
            assertFalse(mic.isEnabled)
            val menu=activity.javaClass.getDeclaredMethod("openDrawer").apply { isAccessible=true }
            menu.invoke(activity)
            assertTrue("opening the drawer cancelled the pending reply",busy.getBoolean(transport))
            val cancel=field(activity,"cancelReply").get(activity) as View
            assertEquals(View.VISIBLE,cancel.visibility)
            cancel.performClick()
            assertFalse(busy.getBoolean(transport))
        } finally {window.pause().stop().destroy()}
    }

    @Test fun ordinaryMicrophoneButtonOpensTranscriptBeforeRecognition() {
        val window=Robolectric.buildActivity(AvatarTestActivity::class.java).create().start().resume()
        try {
            val a=window.get()
            val voiceId="local-"+"b".repeat(32)
            org.robolectric.Shadows.shadowOf(a.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
            var listens=0
            val fake=java.lang.reflect.Proxy.newProxyInstance(LabVoice::class.java.classLoader,arrayOf(LabVoice::class.java)) { _, method, _ ->
                if(method.name=="listen")listens++
                null
            }
            field(a,"voice").set(a,fake)
            field(a,"voices").set(a,listOf(LabVoice.Voice(voiceId,"Test","pl")))
            field(a,"profile").set(a,LabPresentation(voiceId=voiceId))
            field(a,"ears").set(a,listOf("pl"))
            val transcript=field(a,"transcript").get(a) as ScrollView
            transcript.visibility=View.GONE
            (field(a,"action").get(a) as ImageButton).performClick()
            assertEquals(1,listens)
            assertEquals(View.VISIBLE,transcript.visibility)
        } finally {window.pause().stop().destroy()}
    }
}
