package com.onianime.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoTest {

    /** Encrypt a known payload with the same scheme, then assert decryptToBeParsed recovers it. */
    @Test
    fun decrypt_roundTrip() {
        val seed = "Xot36i3lK3:v1"
        val plaintext = """{"sourceUrls":[{"sourceUrl":"--7c...","sourceName":"Default"}]}"""

        val key = AllAnimeCrypto.keyFromSeed(seed)
        assertEquals("AES-256 key must be 32 bytes", 32, key.size)

        val iv = ByteArray(12) { it.toByte() }
        val counter = iv + AllAnimeCrypto.hexToBytes("00000002")
        val enc = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(counter))
        }
        val ciphertext = enc.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // [1 header][12 IV][ciphertext][16 trailer]
        val blob = byteArrayOf(0x00) + iv + ciphertext + ByteArray(16)
        val blobB64 = Base64.getEncoder().encodeToString(blob)

        val decrypted = AllAnimeCrypto.decryptToBeParsed(blobB64, seed)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun providerDecoder_passesThroughPlainUrls() {
        val url = "https://www.mp4upload.com/embed-abc.html"
        assertEquals(url, ProviderDecoder.decode(url))
    }

    /** Round-trip the obfuscation table to catch chunking / off-by-one bugs in decode(). */
    @Test
    fun providerDecoder_decodesHexPairs() {
        val reverse = buildReverseTable()
        val expected = "/apivtwo/abc123?x=1&y=2"
        val encoded = "--" + expected.map { c ->
            reverse[c.toString()] ?: error("char '$c' not in table")
        }.joinToString("")
        assertEquals(expected, ProviderDecoder.decode(encoded))
        assertTrue(ProviderDecoder.decode("--").isEmpty())
    }

    private fun buildReverseTable(): Map<String, String> {
        // mirror of ProviderDecoder.TABLE (kept private there)
        val pairs = listOf(
            "79" to "A", "7a" to "B", "7b" to "C", "7c" to "D", "7d" to "E", "7e" to "F",
            "7f" to "G", "70" to "H", "71" to "I", "72" to "J", "73" to "K", "74" to "L",
            "75" to "M", "76" to "N", "77" to "O", "68" to "P", "69" to "Q", "6a" to "R",
            "6b" to "S", "6c" to "T", "6d" to "U", "6e" to "V", "6f" to "W", "60" to "X",
            "61" to "Y", "62" to "Z", "59" to "a", "5a" to "b", "5b" to "c", "5c" to "d",
            "5d" to "e", "5e" to "f", "5f" to "g", "50" to "h", "51" to "i", "52" to "j",
            "53" to "k", "54" to "l", "55" to "m", "56" to "n", "57" to "o", "48" to "p",
            "49" to "q", "4a" to "r", "4b" to "s", "4c" to "t", "4d" to "u", "4e" to "v",
            "4f" to "w", "40" to "x", "41" to "y", "42" to "z", "08" to "0", "09" to "1",
            "0a" to "2", "0b" to "3", "0c" to "4", "0d" to "5", "0e" to "6", "0f" to "7",
            "00" to "8", "01" to "9", "15" to "-", "16" to ".", "67" to "_", "46" to "~",
            "02" to ":", "17" to "/", "07" to "?", "1b" to "#", "63" to "[", "65" to "]",
            "78" to "@", "19" to "!", "1c" to "$", "1e" to "&", "10" to "(", "11" to ")",
            "12" to "*", "13" to "+", "14" to ",", "03" to ";", "05" to "=", "1d" to "%",
        )
        return pairs.associate { (hex, ch) -> ch to hex }
    }
}
