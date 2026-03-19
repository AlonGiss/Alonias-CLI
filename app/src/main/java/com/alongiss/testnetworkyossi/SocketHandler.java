package com.alongiss.testnetworkyossi;

import android.os.Handler;

import java.net.Socket;
import java.security.PublicKey;

import javax.crypto.SecretKey;

public class SocketHandler {
    private static Socket socket;
    private static Handler handler;
    private static PublicKey serverPublicKey;
    private static SecretKey aesKey;
    private static String username;

    // Voice chat shared AES for the current room/session
    private static SecretKey voiceKey;
    private static String voiceKeyRoomId;

    public static synchronized void setUsername(String u) { username = u; }
    public static synchronized String getUsername() { return username; }

    public static synchronized Socket getSocket() {
        return socket;
    }

    public static synchronized void setSocket(Socket s) {
        socket = s;
    }

    public static synchronized Handler getHandler() {
        return handler;
    }

    public static synchronized void setHandler(Handler h) {
        handler = h;
    }

    public static synchronized PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    public static synchronized void setServerPublicKey(PublicKey k) {
        serverPublicKey = k;
    }

    public static synchronized SecretKey getAesKey() {
        return aesKey;
    }

    public static synchronized void setAesKey(SecretKey k) {
        aesKey = k;
    }

    public static synchronized boolean readyForEncryptedTraffic() {
        return serverPublicKey != null && aesKey != null;
    }

    public static synchronized void setVoiceKey(String roomId, SecretKey key) {
        voiceKeyRoomId = roomId;
        voiceKey = key;
    }

    public static synchronized SecretKey getVoiceKeyForRoom(String roomId) {
        if (voiceKey == null) return null;
        if (voiceKeyRoomId == null) return null;
        if (!voiceKeyRoomId.equals(roomId)) return null;
        return voiceKey;
    }

    public static synchronized String getVoiceKeyRoomId() {
        return voiceKeyRoomId;
    }

    public static synchronized void clearVoiceKey() {
        voiceKey = null;
        voiceKeyRoomId = null;
    }

    public static synchronized void reset() {
        socket = null;
        serverPublicKey = null;
        aesKey = null;
        voiceKey = null;
        voiceKeyRoomId = null;
        handler = null;
    }
}