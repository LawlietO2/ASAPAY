package br.com.asa.smartpos.data

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject

class PayloadCipherTest {
    @Test
    fun encryptedEnvelopeCanBeDecryptedAndUsesFreshIv() {
        logSeparator()
        logScenario("[CENARIO] Cifrar payload com AES-256-GCM")
        val keyBytes = ByteArray(32) { it.toByte() }
        val keyBase64 = Base64.getEncoder().encodeToString(keyBytes)
        val plainText = "{\"transactionId\":\"asa-test-123\",\"amount\":12.34}"
        val cipher = PayloadCipher(keyBase64)

        val firstEnvelope = cipher.encrypt(plainText)
        val secondEnvelope = cipher.encrypt(plainText)
        val firstIv = Base64.getDecoder().decode(firstEnvelope.getString("iv"))
        val secondIv = Base64.getDecoder().decode(secondEnvelope.getString("iv"))
        val encryptedBytes = Base64.getDecoder().decode(firstEnvelope.getString("ciphertext"))
        val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
        decryptor.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, firstIv)
        )
        val decryptedText = String(decryptor.doFinal(encryptedBytes), Charsets.UTF_8)

        logProcessing("[VALIDANDO] Algoritmo, tamanho do IV, recuperação do conteúdo e IVs distintos.")
        assertEquals("AES-256-GCM", firstEnvelope.getString("algorithm"))
        assertEquals(12, firstIv.size)
        assertEquals(plainText, decryptedText)
        assertFalse("Cada cifragem deve gerar um IV novo.", firstIv.contentEquals(secondIv))
        logResult("[RESULTADO] O conteúdo foi recuperado com a chave correta e os IVs são distintos.")
        logSeparator()
    }

    @Test
    fun encryptionRejectsKeyThatIsNot256Bits() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar chave AES com tamanho diferente de 256 bits")
        val invalidKey = Base64.getEncoder().encodeToString(ByteArray(16))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            PayloadCipher(invalidKey)
        }

        logProcessing("[VALIDANDO] A chave fornecida contém 128 bits.")
        assertEquals("A chave AES deve ter 256 bits.", exception.message)
        logResult("[RESULTADO] A chave inválida foi rejeitada na criação do cifrador.")
        logSeparator()
    }
}

private const val CIPHER_LOG_RESET = "\u001B[0m"
private const val CIPHER_LOG_CYAN = "\u001B[36m"
private const val CIPHER_LOG_YELLOW = "\u001B[33m"
private const val CIPHER_LOG_GREEN = "\u001B[32m"
private const val CIPHER_LOG_GRAY = "\u001B[90m"

private fun logScenario(message: String) = println("$CIPHER_LOG_CYAN$message$CIPHER_LOG_RESET")
private fun logProcessing(message: String) = println("$CIPHER_LOG_YELLOW$message$CIPHER_LOG_RESET")
private fun logResult(message: String) = println("$CIPHER_LOG_GREEN$message$CIPHER_LOG_RESET")
private fun logSeparator() = println("$CIPHER_LOG_GRAY===================================================================$CIPHER_LOG_RESET")
