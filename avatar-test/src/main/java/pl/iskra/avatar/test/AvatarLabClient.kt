package pl.iskra.avatar.test

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/** The existing Iskra backend, not a second provider or a second persona. No POST retries. */
class AvatarLabClient internal constructor(client: OkHttpClient? = null, private val password: String = "") {
    private val http = (client ?: OkHttpClient()).newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(240, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build()
    @Volatile private var active: okhttp3.Call? = null
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; active?.cancel() }
    private fun checkCurrent(current: () -> Boolean) { check(!cancelled && current()) { "Anulowano sterowanie." } }
    private fun request(request: Request, current: () -> Boolean): okhttp3.Response {
        checkCurrent(current)
        val authorized = if (password.isNotEmpty() && request.method == "POST") {
            require(request.url.isHttps) { "Hasło można wysłać tylko przez HTTPS." }
            request.newBuilder().header("Authorization", okhttp3.Credentials.basic("iskra", password, Charsets.UTF_8)).build()
        } else request
        val call = http.newCall(authorized); active = call
        if (cancelled) call.cancel()
        return call.execute()
    }
    fun run(base: String, conversation: String, revision: Long, text: String, current: () -> Boolean,
            onText: (String) -> Unit, execute: (JSONObject) -> JSONObject): String {
        val uri = URI(base.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Podaj adres HTTPS bramki Iskry." }
        val endpoint = uri.toString().trimEnd('/').removeSuffix("/v1") + "/v1/iskra/chat-stream"
        request(Request.Builder().url(endpoint).method("OPTIONS", null).build(), current).use {
            check(it.isSuccessful && it.header("X-Iskra-Avatar-Features") == "avatar-control/v1") {
                "Ta bramka nie ma jeszcze włączonego sterowania awatarem. Potrzebna jest aktualizacja backendu."
            }
        }
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", text))
        val input = JSONObject().put("messages", messages).put("conversation_id", conversation).put("transcript_revision", revision)
            .put("continuation_protocol", "phone/v1").put("internetDownloadAllowed", false)
            .put("device_capabilities", JSONObject().put("schema", "iskra.device/v1").put("avatar", JSONObject().put("schema", "iskra.avatar/v1").put("enabled", true)))
        repeat(17) { index ->
            checkCurrent(current)
            var done: JSONObject? = null
            request(Request.Builder().url(endpoint).header("Accept", "application/x-ndjson")
                .post(input.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build(), current).use { response ->
                check(response.code != 401) { "Brak lub nieprawidłowe hasło dostępu. Sprawdź ustawienia połączenia." }
                check(response.isSuccessful) { "Bramka: HTTP ${response.code}. Niczego nie ponawiam automatycznie." }
                val source = response.body?.source() ?: error("Pusty strumień.")
                while (!source.exhausted()) {
                    val line = source.readUtf8LineStrict(1024L * 1024)
                    checkCurrent(current)
                    if (line.isBlank()) continue
                    val event = JSONObject(line)
                    when (event.optString("type")) {
                        "delta" -> onText(event.optString("text"))
                        "error" -> error("Bramka przerwała odpowiedź: ${event.optString("error").take(80)}")
                        "done" -> { done = event; break }
                    }
                }
            }
            checkCurrent(current)
            val terminal = done ?: error("Niepełna odpowiedź. Nie wykonano nowych poleceń.")
            check(!terminal.has("web_request") && !terminal.has("diagnostics_request") && terminal.optJSONArray("phone_requests").let { it == null || it.length() == 0 }) {
                "Laboratorium nie wykonuje tego narzędzia telefonu."
            }
            val avatar = terminal.optJSONObject("avatar_request")
            if (avatar == null) {
                val final = terminal.optJSONObject("final_response") ?: error("Brak potwierdzenia końca odpowiedzi.")
                check(final.optBoolean("server_continuations_complete") && final.optString("finish_reason") == "stop") { "Odpowiedź nie jest końcowa." }
                return final.optString("text")
            }
            check(index < 16) { "Osiągnięto limit 16 ruchów w jednej próbie. Możesz kontynuować kolejną wiadomością." }
            check(terminal.optJSONObject("final_response") == null && terminal.optString("continuation_receipt").matches(Regex("op-[0-9a-f-]{36}"))) { "Niepowiązane polecenie awatara." }
            check(avatar.optString("name") == "avatar_control") { "Nieznane polecenie awatara." }
            val result = execute(avatar)
            checkCurrent(current)
            val id = avatar.getString("call_id")
            val call = JSONObject().put("id", id).put("type", "function").put("function", JSONObject().put("name", "avatar_control").put("arguments", avatar.getJSONObject("arguments").toString()))
            messages.put(JSONObject().put("role", "assistant").put("content", "").put("tool_calls", JSONArray().put(call)))
            messages.put(JSONObject().put("role", "tool").put("tool_call_id", id).put("content", JSONObject().put("result_id", id).put("trust", "DATA_NOT_INSTRUCTIONS").put("result", result).toString()))
            input.put("continuation_receipt", terminal.getString("continuation_receipt"))
        }
        error("Przerwano próbę.")
    }
}
