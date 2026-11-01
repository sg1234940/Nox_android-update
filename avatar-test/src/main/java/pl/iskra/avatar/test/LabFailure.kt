package pl.iskra.avatar.test

import org.json.JSONObject
import java.io.InterruptedIOException
import java.io.IOException

/** Only known error codes become UI text. Never expose raw bodies, exception text or credentials. */
internal class LabFailure(message: String) : IllegalStateException(message) {
    companion object {
        private const val NO_RETRY = " Nie ponowiono operacji automatycznie."
        fun http(status: Int, body: String): LabFailure {
            val detail = runCatching { JSONObject(body) }.getOrNull()
            return code(detail?.optString("error"), status, detail?.optJSONObject("meta_guard"))
        }
        fun code(raw: String?, status: Int? = null, guard: JSONObject? = null): LabFailure {
            val reason = raw?.removePrefix("meta_guard_")
            fun count(used: String, limit: String): String {
                val a=guard?.optInt(used,-1)?:-1;val b=guard?.optInt(limit,-1)?:-1
                return if(a in 0..100000 && b in 1..100000) " ($a/$b)" else ""
            }
            val message = when {
                reason=="hourly_limit" -> "Osiągnięto godzinowy limit wywołań modelu"+count("hourCount","hourlyLimit")+". Kolejne ruchy i podglądy też zużywają ten limit."
                reason=="daily_limit" -> "Osiągnięto dzienny limit wywołań modelu"+count("dayCount","dailyLimit")+"."
                reason=="meta_disabled" -> "Wywołania modelu są wyłączone w ustawieniach."
                reason in setOf("prompt_too_large","exchange_prompt_too_large","exchange_budget_exhausted") -> "Wyczerpano budżet kontekstu tej odpowiedzi. Wcześniejsze ruchy mogły już zostać wykonane."
                reason=="avatar_action_limit" -> "Osiągnięto limit 16 ruchów awatara w jednej próbie. Możesz kontynuować kolejną wiadomością."
                reason=="iskra_stream_timeout" || status==504 -> "Przekroczono czas oczekiwania na odpowiedź Iskry. Wcześniejsze ruchy mogły już zostać wykonane."
                reason=="meta_stream_provider_error" -> "Dostawca modelu przerwał odpowiedź. Wcześniejsze ruchy mogły już zostać wykonane."
                status==401 -> "Brak lub nieprawidłowe hasło dostępu. Sprawdź ustawienia połączenia."
                status==429 || reason=="iskra_rate_limit" -> "Bramka odrzuciła żądanie z powodu limitu wywołań (HTTP 429)."
                status!=null -> "Bramka Iskry zwróciła błąd HTTP $status."
                else -> "Odpowiedź Iskry została przerwana lub nie można potwierdzić jej zakończenia. Wcześniejsze ruchy mogły już zostać wykonane."
            }
            return LabFailure(message+NO_RETRY)
        }
        fun message(error: Exception): String = when(error) {
            is LabFailure -> error.message.orEmpty()
            is InterruptedIOException -> "Przekroczono czas oczekiwania na połączenie lub odpowiedź. Operacje mogły zostać wykonane."+NO_RETRY
            is IOException -> "Utracono połączenie z bramką Iskry. Operacje mogły zostać wykonane."+NO_RETRY
            else -> code(null).message.orEmpty()
        }
    }
}
