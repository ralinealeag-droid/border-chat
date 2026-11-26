package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2eeCryptoUnitTest {

    @Test
    fun `ecdh p256 key exchange and aes256 gcm roundtrip succeeds and rejects tampered data`() {
        val random = SecureRandom()
        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }
        val alice = kpg.generateKeyPair()
        val bob = kpg.generateKeyPair()

        // Generate room AES-256 key
        val roomKey = KeyGenerator.getInstance("AES").apply { init(256, random) }.generateKey()

        // Alice derives shared KEK with Bob's public key
        val kaAlice = KeyAgreement.getInstance("ECDH").apply {
            init(alice.private)
            doPhase(bob.public, true)
        }
        val kekAlice = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(kaAlice.generateSecret()), "AES")

        val wrapIv = ByteArray(12).also { random.nextBytes(it) }
        val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, kekAlice, GCMParameterSpec(128, wrapIv))
        }
        val wrappedRoomKey = wrapCipher.doFinal(roomKey.encoded)

        // Bob derives identical KEK with Alice's public key and unwraps room key
        val kaBob = KeyAgreement.getInstance("ECDH").apply {
            init(bob.private)
            doPhase(alice.public, true)
        }
        val kekBob = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(kaBob.generateSecret()), "AES")
        val unwrapCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, kekBob, GCMParameterSpec(128, wrapIv))
        }
        val unwrappedRoomKey = SecretKeySpec(unwrapCipher.doFinal(wrappedRoomKey), "AES")

        // Encrypt message using roomKey, decrypt using unwrappedRoomKey
        val msg = "Pesan rahasia E2EE + Lokasi Realtime"
        val msgIv = ByteArray(12).also { random.nextBytes(it) }
        val enc = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, roomKey, GCMParameterSpec(128, msgIv))
        }.doFinal(msg.toByteArray(StandardCharsets.UTF_8))

        assertNotEquals(msg, String(enc, StandardCharsets.UTF_8))

        val dec = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, unwrappedRoomKey, GCMParameterSpec(128, msgIv))
        }.doFinal(enc)
        assertEquals(msg, String(dec, StandardCharsets.UTF_8))

        // Tamper ciphertext byte -> must fail GCM authentication tag check
        val tampered = enc.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        assertThrows(AEADBadTagException::class.java) {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, unwrappedRoomKey, GCMParameterSpec(128, msgIv))
            }.doFinal(tampered)
        }
    }
}
