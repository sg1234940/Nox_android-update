package pl.iskra.avatar.test

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local connection setting, never chat data. Ciphertext alone is backed up nowhere. */
internal class GatewayPasswordStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gateway_access_v1", Context.MODE_PRIVATE)
    private val alias = "iskra_gateway_password_v1"
    private fun origin(base: String): String {
        val uri = URI(base.trim().trimEnd('/'))
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Wymagany jest adres HTTPS." }
        return "https://" + uri.host.lowercase(java.util.Locale.ROOT) + ":" + (if (uri.port == -1) 443 else uri.port)
    }
    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(alias, null) as? SecretKey
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generateKey()
    }
    @Synchronized fun read(base: String): String = try {
        val bound = origin(base)
        if (prefs.getString("origin", null) != bound) "" else {
            val stored = prefs.getString("ciphertext", null)
            if (stored == null) "" else {
                val parts = stored.split(':')
                require(parts.size == 2)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: error("Brak klucza"), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                cipher.updateAAD(bound.toByteArray(Charsets.UTF_8))
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }
        }
    } catch (_: Exception) { "" } // Unavailable key requires re-entry, never a plaintext fallback.
    @Synchronized fun write(base: String, password: String) {
        require(password.length <= 256 && password.none { it.code < 32 || it.code == 127 }) { "Nieprawidłowy format hasła." }
        if (password.isEmpty()) {
            check(prefs.edit().clear().commit()) { "Nie zapisano ustawień dostępu." }
            return
        }
        val bound = origin(base)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(bound.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("origin", bound).putString("ciphertext", stored).commit()) { "Nie zapisano ustawień dostępu." }
    }
}
