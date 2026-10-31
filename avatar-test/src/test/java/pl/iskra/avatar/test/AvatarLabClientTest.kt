package pl.iskra.avatar.test

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AvatarLabClientTest {
    private fun fixture(password: String = "", work: (MockWebServer, AvatarLabClient) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val server = MockWebServer(); val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(serverTls.sslSocketFactory(), false); server.start()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val http = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        try { work(server, AvatarLabClient(http, password)) } finally { server.shutdown() }
    }
    private fun advertised() = MockResponse().setResponseCode(204).setHeader("X-Iskra-Avatar-Features", "avatar-control/v1")
    private val receipt = "op-11111111-1111-4111-8111-111111111111"
    private fun request(receiptValue: String = receipt) = JSONObject().put("type", "done").put("continuation_receipt", receiptValue)
        .put("final_response", JSONObject.NULL).put("avatar_request", JSONObject().put("name", "avatar_control").put("call_id", "move_1")
            .put("arguments", JSONObject("""{"action":"SET","weights":{"mouthSmile":0.3},"preview":false}"""))).toString()
    private fun final() = JSONObject().put("type", "done").put("final_response", JSONObject().put("finish_reason", "stop")
        .put("server_continuations_complete", true).put("text", "Gotowe")).toString()
    @Test fun passwordIsOnlyInHeadersOfEveryPostIncludingContinuation() = fixture(" Zażółć: gęślą ") { server, client ->
        server.enqueue(advertised()); server.enqueue(MockResponse().setBody(request() + "\n")); server.enqueue(MockResponse().setBody(final() + "\n"))
        client.run(server.url("/").toString(), "c", 1, "uśmiech", { true }, {}, { JSONObject().put("ok", true) })
        assertNull(server.takeRequest().getHeader("Authorization"))
        repeat(2) {
            val req = server.takeRequest()
            assertEquals(okhttp3.Credentials.basic("iskra", " Zażółć: gęślą ", Charsets.UTF_8), req.getHeader("Authorization"))
            assertFalse(req.body.readUtf8().contains("gęślą"))
        }
    }
    @Test fun incorrectPasswordDoesNotRetryOrExecuteAndShowsAUsefulMessage() = fixture("wrong") { server, client ->
        server.enqueue(advertised()); server.enqueue(MockResponse().setResponseCode(401)); var executed = 0
        val error = assertThrows(IllegalStateException::class.java) {
            client.run(server.url("/").toString(), "c", 1, "x", { true }, {}, { executed++; JSONObject() })
        }
        assertTrue(error.message.orEmpty().contains("hasło")); assertFalse(error.message.orEmpty().contains("wrong"))
        assertEquals(0, executed); assertEquals(2, server.requestCount)
    }
    @Test fun realTlsTransportPreservesReceiptOwnerAndExactCallResultPair() = fixture { server, client ->
        server.enqueue(advertised()); server.enqueue(MockResponse().setBody(request() + "\n")); server.enqueue(MockResponse().setBody(final() + "\n"))
        var executed = 0
        val answer = client.run(server.url("/").toString(), "conversation", 1, "Uśmiechnij się", { true }, {}, { r -> executed++; assertEquals("move_1",r.getString("call_id")); JSONObject().put("operation", "AVATAR").put("ok", true) })
        assertEquals("Gotowe",answer); assertEquals(1,executed)
        assertEquals("OPTIONS",server.takeRequest().method)
        val original = JSONObject(server.takeRequest().body.readUtf8()); val resumed = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(original.getString("conversation_id"),resumed.getString("conversation_id")); assertEquals(original.getLong("transcript_revision"),resumed.getLong("transcript_revision"))
        assertEquals(receipt,resumed.getString("continuation_receipt"))
        val messages = resumed.getJSONArray("messages"); assertEquals(3,messages.length())
        assertEquals(original.getJSONArray("messages").getJSONObject(0).toString(),messages.getJSONObject(0).toString())
        assertEquals("move_1",messages.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        val result = JSONObject(messages.getJSONObject(2).getString("content")); assertEquals("DATA_NOT_INSTRUCTIONS",result.getString("trust")); assertEquals("move_1",result.getString("result_id"))
    }
    @Test fun unsupportedBackendDoesNotPostOrExecute() = fixture { server, client ->
        server.enqueue(MockResponse().setResponseCode(204)); var n = 0
        assertThrows(IllegalStateException::class.java) { client.run(server.url("/").toString(),"c",1,"x",{true},{},{ n++; JSONObject() }) }
        assertEquals(0,n); assertEquals(1,server.requestCount)
    }
    @Test fun incompleteStreamNeverExecutesACommand() = fixture { server, client ->
        server.enqueue(advertised()); server.enqueue(MockResponse().setBody("""{"type":"delta","text":"zaraz ruszę"}"""+"\n")); var n = 0
        assertThrows(IllegalStateException::class.java) { client.run(server.url("/").toString(),"c",1,"x",{true},{},{ n++; JSONObject() }) }
        assertEquals(0,n)
    }
    @Test fun malformedReceiptAndMixedHandoffNeverExecute() {
        for (raw in listOf(request("unbound"), JSONObject(request()).put("web_request",JSONObject()).toString(), JSONObject(request()).put("final_response",JSONObject()).toString())) fixture { server, client ->
            server.enqueue(advertised()); server.enqueue(MockResponse().setBody(raw+"\n")); var n = 0
            assertThrows(IllegalStateException::class.java) { client.run(server.url("/").toString(),"c",1,"x",{true},{},{ n++; JSONObject() }) }; assertEquals(0,n)
        }
    }
    @Test fun cancelledOrStaleOwnerDoesNotReachNetwork() = fixture { server, client ->
        client.cancel(); assertThrows(IllegalStateException::class.java) { client.run(server.url("/").toString(),"c",1,"x",{true},{},{ JSONObject() }) }; assertEquals(0,server.requestCount)
    }
    @Test fun requestDoesNotSilentlyFollowRedirectOrRetryAfterHttpFailure() = fixture { server, client ->
        server.enqueue(advertised()); server.enqueue(MockResponse().setResponseCode(503)); var n=0
        assertThrows(IllegalStateException::class.java) { client.run(server.url("/").toString(),"c",1,"x",{true},{},{ n++; JSONObject() }) }; assertEquals(0,n); assertEquals(2,server.requestCount)
    }
}
