package pl.iskra.avatar.test

import android.content.Context
import org.json.JSONObject
import pl.iskra.avatar.LabPresentation

internal class LabPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("avatar_lab_presentation_v1", Context.MODE_PRIVATE)
    fun read(): LabPresentation = try {
        val j = JSONObject(prefs.getString("profile", "{}") ?: "{}")
        require(j.optInt("v", 1) == 1)
        LabPresentation(model=j.optString("model", "builtin"),outfit=j.optString("outfit"),hologram=j.optBoolean("hologram"),
            brightness=j.optDouble("brightness",1.0).toFloat(),angle=j.optDouble("angle",0.0).toFloat(),zoom=j.optDouble("zoom",1.0).toFloat(),
            face=j.optBoolean("face"),background=j.optString("background","black"),inputLanguage=j.optString("inputLanguage","pl"),
            outputLanguage=j.optString("outputLanguage","pl"),voiceId=j.optString("voiceId"),lengthScale=j.optDouble("lengthScale",1.0).toFloat(),
            silenceMs=j.optInt("silenceMs",1200)).checked()
    } catch (_: Exception) { LabPresentation() }
    fun write(p: LabPresentation) {
        p.checked()
        val j = JSONObject().put("v",1).put("model",p.model).put("outfit",p.outfit).put("hologram",p.hologram).put("brightness",p.brightness)
            .put("angle",p.angle).put("zoom",p.zoom).put("face",p.face).put("background",p.background).put("inputLanguage",p.inputLanguage)
            .put("outputLanguage",p.outputLanguage).put("voiceId",p.voiceId).put("lengthScale",p.lengthScale).put("silenceMs",p.silenceMs)
        check(prefs.edit().putString("profile",j.toString()).commit()) { "Nie zapisano wyglądu." }
    }
}
