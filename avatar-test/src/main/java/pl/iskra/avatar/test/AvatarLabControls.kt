package pl.iskra.avatar.test

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.*
import org.json.JSONObject
import pl.iskra.avatar.AvatarCommandProtocol as Protocol
import pl.iskra.avatar.AvatarController
import pl.iskra.avatar.AvatarSurfaceView
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Foreground-only lab adapter. Manual controls always take over from a model attempt. */
class AvatarLabControls(private val activity: Activity, private val avatar: AvatarSurfaceView,
    private val begin: () -> Unit, private val send: (AvatarController.Command) -> Unit,
    private val camera: () -> Pair<Float, Boolean>, private val setCamera: (Float, Boolean) -> Unit,
    private val refresh: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private val conversation = UUID.randomUUID().toString()
    private var revision = 0L
    @Volatile private var inForeground = false
    @Volatile private var busy = false
    @Volatile private var client: AvatarLabClient? = null
    private var applying = false
    private lateinit var output: TextView
    private lateinit var sendButton: Button
    private var previewDialog: AlertDialog? = null
    private data class Execution(val result: JSONObject, val preview: AvatarSurfaceView.Preview? = null)

    fun install(parent: LinearLayout) {
        val prefs = activity.getSharedPreferences("avatar_lab_connection", 0)
        val address = EditText(activity).apply { setSingleLine(true); hint = "Adres bramki HTTPS"; setText(prefs.getString("url", "https://nox-android.onrender.com")); contentDescription = "Adres bramki Iskry" }
        parent.addView(address)
        val input = EditText(activity).apply { hint = "Napisz Iskrze, jaki ruch chcesz sprawdzić"; minLines = 1; maxLines = 3 }
        parent.addView(input)
        sendButton = Button(activity).apply { text = "Wyślij do Iskry"; isAllCaps = false }
        output = TextView(activity).apply { text = "W tej próbie Iskra może sterować awatarem i oglądać jego podgląd. Możesz też sprawdzić obraz lokalnie."; setTextColor(0xffe3ebf8.toInt()); setTextIsSelectable(true) }
        sendButton.setOnClickListener {
            if (!inForeground) return@setOnClickListener
            if (busy) return@setOnClickListener
            val text = input.text.toString().trim()
            if (text.isBlank() || text.length > 8000) { output.text = "Wpisz wiadomość do 8000 znaków."; return@setOnClickListener }
            val base = address.text.toString().trim()
            prefs.edit().putString("url", base).apply()
            cancel(); val owner = generation.incrementAndGet(); begin(); busy = true; sendButton.isEnabled = false
            val attempt = AvatarLabClient(); client = attempt; val current = { generation.get() == owner && inForeground }
            val calls = Protocol.Calls(); val rev = ++revision; output.text = "Łączę z Iskrą…"
            worker.execute {
                val streamed = StringBuilder()
                try {
                    val answer = attempt.run(base, conversation, rev, text, current, onText = { delta ->
                        streamed.append(delta); handler.post { if (current()) output.text = streamed.toString() }
                    }, execute = { request -> execute(request, calls, current) })
                    handler.post { if (current()) output.text = answer.ifBlank { "Odpowiedź zakończona." } }
                } catch (e: Exception) { handler.post { if (current()) output.text = "${e.message ?: "Przerwano próbę."}\nPo błędzie nie ponawiam ruchu automatycznie." } }
                finally { handler.post { if (generation.get() == owner) { busy = false; sendButton.isEnabled = true; client = null; refresh() } } }
            }
        }
        parent.addView(sendButton)
        parent.addView(Button(activity).apply { text = "Przerwij sterowanie Iskry"; isAllCaps = false; setOnClickListener { cancel(); output.text = "Przerwano. Suwaki są do Twojej dyspozycji." } })
        parent.addView(Button(activity).apply {
            text = "Sprawdź podgląd lokalnie"; isAllCaps = false
            setOnClickListener {
                cancel(); val owner = generation.get()
                avatar.capturePreview { preview, error ->
                    if (generation.get() != owner || !inForeground || activity.isFinishing || activity.isDestroyed) { preview?.bitmap?.recycle(); return@capturePreview }
                    if (preview == null) output.text = "Podgląd niegotowy: ${error ?: "COPY_FAILED"}."
                    else {
                        val image = ImageView(activity).apply { setImageBitmap(preview.bitmap); adjustViewBounds = true; contentDescription = "Podgląd awatara dla Iskry" }
                        val dialog = AlertDialog.Builder(activity).setTitle("Taki obraz może dostać Iskra").setView(image).setPositiveButton("Zamknij",null).create()
                        previewDialog = dialog
                        dialog.setOnDismissListener { image.setImageDrawable(null); preview.bitmap.recycle(); if (previewDialog === dialog) previewDialog = null }
                        dialog.show()
                    }
                }
            }
        })
        parent.addView(output)
    }
    fun resumed(value: Boolean) { inForeground = value; if (!value) cancel() }
    fun manual() { if (!applying && busy) cancel() }
    private fun cancel() {
        generation.incrementAndGet(); client?.cancel(); client = null; busy = false
        previewDialog?.dismiss(); previewDialog = null
        if (::sendButton.isInitialized) sendButton.isEnabled = true
        avatar.invalidatePreview()
    }
    fun destroy() { cancel(); worker.shutdownNow() }
    private fun frame(f: AvatarSurfaceView.Frame) = JSONObject().put("id", f.id).put("at", f.at).put("pose", Protocol.pose(f.state, f.angle, f.face))
    private fun execute(request: JSONObject, calls: Protocol.Calls, current: () -> Boolean): JSONObject {
        val id = request.optString("call_id"); val args = request.optJSONObject("arguments")
        if (!id.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) || args == null || !Protocol.valid(args)) return Protocol.failure("INVALID_ARGUMENTS")
        calls.cached(id, args)?.let { return it }
        if (!calls.canExecute()) return Protocol.failure("REPLAY_LIMIT")
        val latch = CountDownLatch(1); val abandoned = AtomicBoolean(false); val result = AtomicReference<Execution>(); val completionLock = Any()
        fun finish(value: Execution) {
            synchronized(completionLock) {
                if (abandoned.get()) value.preview?.bitmap?.recycle() else { result.set(value); latch.countDown() }
            }
        }
        handler.post {
            if (abandoned.get() || !current()) { finish(Execution(Protocol.failure("STALE", "STALE"))); return@post }
            if (avatar.lastFrame() == null) { finish(Execution(Protocol.failure("NOT_READY", "UNAVAILABLE"))); return@post }
            applying = true
            try {
                when (args.getString("action")) {
                    "RESET" -> send(AvatarController.Command.Reset)
                    "SET" -> {
                        args.optJSONObject("weights")?.let { w -> w.keys().forEach { send(AvatarController.Command.Weight(it, w.getDouble(it).toFloat())) } }
                        if (args.has("animation")) send(AvatarController.Command.Animation(if (args.isNull("animation")) null else args.getString("animation")))
                        if (args.has("body_paused")) send(AvatarController.Command.BodyPaused(args.getBoolean("body_paused")))
                        args.optJSONObject("camera")?.let { setCamera(it.getDouble("angle").toFloat(), it.getBoolean("face")) }
                    }
                }
            } finally { applying = false }
            refresh()
            val (angle, face) = camera()
            val base = JSONObject().put("operation", "AVATAR").put("ok", true).put("state", if (args.getString("action") == "STATE") "READ" else "APPLIED")
                .put("executed", args.getString("action") != "STATE").put("requested", Protocol.pose(avatar.controller.snapshot(), angle, face))
                .put("frame", JSONObject.NULL).put("preview", JSONObject.NULL).put("preview_error", JSONObject.NULL)
            if (!args.optBoolean("preview", true)) { finish(Execution(base)); return@post }
            handler.postDelayed({
                if (abandoned.get() || !current()) { finish(Execution(base.put("preview_error", "STALE"))); return@postDelayed }
                avatar.capturePreview { preview, error ->
                    if (preview != null) finish(Execution(base.put("frame", frame(preview.frame)), preview))
                    else finish(Execution(base.put("preview_error", error ?: "COPY_FAILED")))
                }
            }, 400)
        }
        if (!latch.await(3, TimeUnit.SECONDS)) {
            synchronized(completionLock) { abandoned.set(true); result.getAndSet(null)?.preview?.bitmap?.recycle() }
            error("Telefon nie potwierdził ruchu w czasie. Stan wymaga sprawdzenia.")
        }
        val completed = result.get() ?: error("Brak wyniku telefonu.")
        val reply = completed.result
        completed.preview?.let { preview ->
            try {
                var bytes = ByteArray(0)
                for (quality in listOf(80, 65, 50, 35)) {
                    val stream = ByteArrayOutputStream(); check(preview.bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)); bytes = stream.toByteArray()
                    if (bytes.size <= 245760) break
                }
                check(bytes.size in 1..245760)
                reply.put("preview", JSONObject().put("data_url", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                    .put("width", preview.bitmap.width).put("height", preview.bitmap.height))
            } catch (_: Exception) { reply.put("preview_error", "ENCODING_FAILED") }
            catch (_: OutOfMemoryError) { reply.put("preview_error", "ENCODING_FAILED") }
            finally { preview.bitmap.recycle() }
        }
        calls.remember(id, args, reply)
        return reply
    }
}
