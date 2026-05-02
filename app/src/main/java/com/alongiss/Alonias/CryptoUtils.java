package com.alongiss.Alonias;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * CryptoUtils contains helper methods for the encryption system of the app.
 *
 * It supports:
 * - generating AES keys
 * - parsing RSA public keys from Base64 text
 * - encrypting data with RSA
 * - encrypting and decrypting data with AES-GCM
 *
 * In this project:
 * - RSA is used only during the handshake to safely send the AES key.
 * - AES is used for the real encrypted communication with the server.
 */
public class CryptoUtils {

    /**
     * Generates a new random AES key.
     *
     * This key is used for symmetric encryption, meaning the same key
     * is used to encrypt and decrypt messages.
     *
     * @return a new 256-bit AES SecretKey
     */
    public static SecretKey generateAesKey() throws Exception {
        KeyGenerator gen = KeyGenerator.getInstance("AES");

        // Use a 256-bit AES key for strong encryption
        gen.init(256);

        return gen.generateKey();
    }

    /**
     * Converts a Base64 RSA public key string into a PublicKey object.
     *
     * The server sends its public key as Base64 text.
     * The client decodes it and rebuilds the public key object.
     *
     * @param base64 RSA public key encoded as Base64
     * @return PublicKey object that can be used for RSA encryption
     */
    public static PublicKey parseRsaPublicKey(String base64) throws Exception {
        // Decode the Base64 text into raw key bytes
        byte[] decoded = Base64.decode(base64, Base64.DEFAULT);

        // X509EncodedKeySpec is the standard format for encoded public keys
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

        // Build and return the RSA public key
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /**
     * Encrypts data using the server RSA public key.
     *
     * In this project, RSA is mainly used to encrypt the AES key
     * before sending it to the server.
     *
     * @param key  server RSA public key
     * @param data data to encrypt
     * @return encrypted bytes
     */
    public static byte[] rsaEncrypt(PublicKey key, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");

        // Initialize cipher in encryption mode with the server public key
        c.init(Cipher.ENCRYPT_MODE, key);

        return c.doFinal(data);
    }

    /**
     * Encrypts plain data using AES-GCM.
     *
     * AES-GCM provides both:
     * - encryption, so other people cannot read the message
     * - authentication, so changed/corrupted messages are rejected
     *
     * The method creates a random IV for every message.
     * The returned data format is:
     *
     * [12 bytes IV][encrypted data + authentication tag]
     *
     * @param key   AES key used for encryption
     * @param plain original plain message bytes
     * @return IV + encrypted message
     */
    public static byte[] aesEncrypt(SecretKey key, byte[] plain) throws Exception {
        // AES-GCM commonly uses a 12-byte IV
        byte[] iv = new byte[12];

        // Generate a new random IV for this specific message
        new SecureRandom().nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");

        // 128 is the authentication tag size in bits
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

        // Encrypt the data and create the authentication tag
        byte[] cipher = c.doFinal(plain);

        // Return IV first, then the encrypted data
        // This is needed because the receiver must know the IV to decrypt
        return ByteBuffer.allocate(iv.length + cipher.length)
                .put(iv)
                .put(cipher)
                .array();
    }

    /**
     * Decrypts data that was encrypted with AES-GCM.
     *
     * The input must be in the same format created by aesEncrypt:
     *
     * [12 bytes IV][encrypted data + authentication tag]
     *
     * @param key   AES key used for decryption
     * @param input IV + encrypted message
     * @return decrypted plain message bytes
     */
    public static byte[] aesDecrypt(SecretKey key, byte[] input) throws Exception {
        // Extract the 12-byte IV from the start of the input
        byte[] iv = new byte[12];

        // The rest of the input is the encrypted data + authentication tag
        byte[] cipher = new byte[input.length - 12];

        System.arraycopy(input, 0, iv, 0, 12);
        System.arraycopy(input, 12, cipher, 0, cipher.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");

        // Use the same IV and AES key to decrypt
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

        // If the data was changed or the key is wrong, this will throw an exception
        return c.doFinal(cipher);
    }
}