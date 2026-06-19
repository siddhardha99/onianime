package com.onianime.crypto

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Port of ani-cli's response decryption ("process_response" + the key derivation at the top of
 * the script).
 *
 * ani-cli derives the AES-256 key as:  printf '<seed>' | openssl dgst -sha256  -> 32 raw bytes.
 * The encrypted "tobeparsed" blob is standard base64 and laid out as:
 *
 *   [ 1 byte header ][ 12-byte IV ][ ciphertext ... ][ 16-byte trailer ]
 *
 * The CTR counter is the 12-byte IV followed by the 4-byte big-endian block index `00000002`.
 * Decryption is AES-256-CTR with no padding.
 */
object AllAnimeCrypto {

    /** SHA-256 of the seed string => 32-byte AES-256 key (matches `openssl dgst -sha256`). */
    fun keyFromSeed(seed: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))

    /**
     * Decrypts the base64 `tobeparsed` blob and returns the plaintext JSON string.
     *
     * Offsets default to ani-cli's current layout but are parameterised so the remote config can
     * adjust them if allanime changes the framing without an app update.
     */
    fun decryptToBeParsed(
        blobBase64: String,
        keySeed: String,
        ivOffset: Int = 1,
        ivLen: Int = 12,
        headerLen: Int = 13,
        tagLen: Int = 16,
        ctrSuffixHex: String = "00000002",
    ): String {
        val data = Base64.getDecoder().decode(blobBase64.trim())
        require(data.size > headerLen + tagLen) { "tobeparsed blob too small (${data.size} bytes)" }

        val iv = data.copyOfRange(ivOffset, ivOffset + ivLen)
        val counter = iv + hexToBytes(ctrSuffixHex) // 12 + 4 = 16-byte CTR block
        val ciphertext = data.copyOfRange(headerLen, data.size - tagLen)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyFromSeed(keySeed), "AES"),
                IvParameterSpec(counter),
            )
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
