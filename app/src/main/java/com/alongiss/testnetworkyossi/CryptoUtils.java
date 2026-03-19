package com.alongiss.testnetworkyossi;

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

public class CryptoUtils {

    public static SecretKey generateAesKey() throws Exception {
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256);
        return gen.generateKey();
    }

    public static PublicKey parseRsaPublicKey(String base64) throws Exception {
        byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public static byte[] rsaEncrypt(PublicKey key, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(data);
    }

    public static byte[] aesEncrypt(SecretKey key, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipher = c.doFinal(plain);

        return ByteBuffer.allocate(iv.length + cipher.length)
                .put(iv)
                .put(cipher)
                .array();
    }

    public static byte[] aesDecrypt(SecretKey key, byte[] input) throws Exception {
        byte[] iv = new byte[12];
        byte[] cipher = new byte[input.length - 12];

        System.arraycopy(input, 0, iv, 0, 12);
        System.arraycopy(input, 12, cipher, 0, cipher.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return c.doFinal(cipher);
    }
}
