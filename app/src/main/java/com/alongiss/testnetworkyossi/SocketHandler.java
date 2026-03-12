package com.alongiss.testnetworkyossi;

import android.os.Handler;

import java.net.Socket;



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

    public static synchronized void reset() {
        socket = null;
        serverPublicKey = null;
        aesKey = null;
        handler = null;
    }
}