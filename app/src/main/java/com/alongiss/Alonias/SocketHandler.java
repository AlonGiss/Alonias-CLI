package com.alongiss.Alonias;

import android.os.Handler;

import java.net.Socket;
import java.security.PublicKey;

import javax.crypto.SecretKey;

/**
 * SocketHandler is a global helper class that stores the main TCP socket
 * and shared connection data for the app.
 *
 * This class allows different Activities / Managers in the app to access
 * the same socket, encryption keys, username, and UI handler.
 */
public class SocketHandler {

    // The main TCP socket used to communicate with the server
    private static Socket socket;

    // Handler used to send messages from background threads to the UI thread
    private static Handler handler;

    // Server public RSA key, used during the encryption setup
    private static PublicKey serverPublicKey;

    // AES key used for encrypted TCP communication with the server
    private static SecretKey aesKey;

    // Username of the currently connected player
    private static String username;

    // AES key used only for voice chat in the current room/session
    private static SecretKey voiceKey;

    // The room id that the current voice key belongs to
    private static String voiceKeyRoomId;

    // Lock object used to prevent multiple threads from writing to the TCP socket at the same time
    private static final Object SEND_LOCK = new Object();

    // True when the TCP listener thread is already running
    private static boolean listenerRunning = false;

    /**
     * Saves the username of the current player.
     */
    public static synchronized void setUsername(String u) {
        username = u;
    }

    /**
     * Returns the username of the current player.
     */
    public static synchronized String getUsername() {
        return username;
    }

    /**
     * Returns the current TCP socket.
     */
    public static synchronized Socket getSocket() {
        return socket;
    }

    /**
     * Saves the main TCP socket.
     */
    public static synchronized void setSocket(Socket s) {
        socket = s;
    }

    /**
     * Returns the Handler used for updating the UI from background threads.
     */
    public static synchronized Handler getHandler() {
        return handler;
    }

    /**
     * Saves the Handler used by the app for UI communication.
     */
    public static synchronized void setHandler(Handler h) {
        handler = h;
    }

    /**
     * Returns the server public RSA key.
     */
    public static synchronized PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    /**
     * Saves the server public RSA key.
     */
    public static synchronized void setServerPublicKey(PublicKey k) {
        serverPublicKey = k;
    }

    /**
     * Returns the AES key used for encrypted TCP messages.
     */
    public static synchronized SecretKey getAesKey() {
        return aesKey;
    }

    /**
     * Saves the AES key used for encrypted TCP messages.
     */
    public static synchronized void setAesKey(SecretKey k) {
        aesKey = k;
    }

    /**
     * Checks if the app is ready to send and receive encrypted TCP traffic.
     *
     * The app is ready only when:
     * - the socket exists
     * - the socket is not closed
     * - the server public key exists
     * - the AES key exists
     */
    public static synchronized boolean readyForEncryptedTraffic() {
        return socket != null
                && !socket.isClosed()
                && serverPublicKey != null
                && aesKey != null;
    }

    /**
     * Saves the AES key used for voice chat in a specific room.
     *
     * Each room can have its own voice key, so the room id is saved together
     * with the key to avoid using the wrong key in another room.
     */
    public static synchronized void setVoiceKey(String roomId, SecretKey key) {
        voiceKeyRoomId = roomId;
        voiceKey = key;
    }

    /**
     * Returns the voice chat AES key only if it belongs to the requested room.
     *
     * If there is no key, no room id, or the room id does not match,
     * the method returns null.
     */
    public static synchronized SecretKey getVoiceKeyForRoom(String roomId) {
        if (voiceKey == null) return null;
        if (voiceKeyRoomId == null) return null;
        if (!voiceKeyRoomId.equals(roomId)) return null;
        return voiceKey;
    }

    /**
     * Returns the room id connected to the current voice chat key.
     */
    public static synchronized String getVoiceKeyRoomId() {
        return voiceKeyRoomId;
    }

    /**
     * Clears the current voice chat key and its room id.
     *
     * This should be called when leaving a room or ending a voice session.
     */
    public static synchronized void clearVoiceKey() {
        voiceKey = null;
        voiceKeyRoomId = null;
    }

    /**
     * Returns the lock object used when sending messages through TCP.
     *
     * This helps prevent two threads from sending data at the same time
     * and mixing messages together.
     */
    public static Object getSendLock() {
        return SEND_LOCK;
    }

    /**
     * Marks the TCP listener as started.
     *
     * Returns true if the listener was not already running.
     * Returns false if another listener is already running.
     */
    public static synchronized boolean markListenerStarted() {
        if (listenerRunning) return false;
        listenerRunning = true;
        return true;
    }

    /**
     * Marks the TCP listener as stopped.
     */
    public static synchronized void markListenerStopped() {
        listenerRunning = false;
    }

    /**
     * Resets all saved connection data.
     *
     * This closes the socket if it exists and clears all keys, handler,
     * voice chat data, and listener state.
     *
     * Usually called when the user disconnects, logs out, or the app needs
     * to restart the connection cleanly.
     */
    public static synchronized void reset() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
            // Ignored because reset should continue even if closing the socket fails
        }

        socket = null;
        serverPublicKey = null;
        aesKey = null;
        voiceKey = null;
        voiceKeyRoomId = null;
        handler = null;
        listenerRunning = false;
    }
}