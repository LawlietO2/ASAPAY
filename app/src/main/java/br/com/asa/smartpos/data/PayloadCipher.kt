package br.com.asa.smartpos.data

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM envelope for the challenge's encrypted JSON request body. */
class PayloadCipher(keyBase64: String) {
    private val key = Base64.getDecoder().decode(keyBase64).also {
        require(it.size == 32) { "A chave AES deve ter 256 bits." }
    }

    fun encrypt(plainText: String): JSONObject {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            .put("algorithm", "AES-256-GCM")
    }
}
