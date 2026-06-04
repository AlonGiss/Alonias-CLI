package com.alongiss.Alonias;

import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class tcp_send_recv implements Runnable {

    private static final String TAG = "TCP";

    private static final int LEN_SIZE = 9;

    private static final String IP = " 192.168.1.229";
    private static final int PORT = 11133;

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int HANDSHAKE_TIMEOUT_MS = 4000;

    private static final Object CONNECT_LOCK = new Object();

    private static volatile boolean serverMarkedDown = false;
    private static volatile boolean connectionLostAlreadyNotified = false;

    private final byte[] plainPayload;
    private final Handler handler;

    public tcp_send_recv(Handler h, byte[] data) {
        this.handler = h;
        this.plainPayload = data;
        SocketHandler.setHandler(h);
    }

    public static void allowConnectionRetry() {
        serverMarkedDown = false;
        connectionLostAlreadyNotified = false;
    }

    public static boolean isServerMarkedDown() {
        return serverMarkedDown;
    }

    @Override
    public void run() {
        try {
            if (serverMarkedDown) {
                notifyConnectionLostOnce();
                return;
            }

            Socket sk = ensureConnected();

            int waitedMs = 0;

            while (!SocketHandler.readyForEncryptedTraffic()) {
                if (serverMarkedDown) {
                    notifyConnectionLostOnce();
                    return;
                }

                Thread.sleep(10);
                waitedMs += 10;

                if (waitedMs >= HANDSHAKE_TIMEOUT_MS) {
                    throw new IOException("Timeout waiting for RSA/AES handshake");
                }
            }

            SecretKey aes = SocketHandler.getAesKey();

            if (aes == null) {
                throw new IOException("AES key is null after handshake");
            }

            byte[] encrypted = CryptoUtils.aesEncrypt(aes, plainPayload);
            sendFrame(sk, concat("DATA|".getBytes(StandardCharsets.US_ASCII), encrypted));

            serverMarkedDown = false;
            connectionLostAlreadyNotified = false;

        } catch (Exception e) {
            Log.e(TAG, "Send error", e);

            markServerDown();
            notifyConnectionLostOnce();
            safeResetSocket();
        }
    }

    private Socket ensureConnected() throws Exception {
        synchronized (CONNECT_LOCK) {
            if (serverMarkedDown) {
                throw new IOException("Server already marked down");
            }

            Socket sk = SocketHandler.getSocket();

            if (sk == null || sk.isClosed() || !sk.isConnected()) {
                SocketHandler.reset();

                sk = new Socket();
                sk.connect(new InetSocketAddress(IP, PORT), CONNECT_TIMEOUT_MS);
                sk.setTcpNoDelay(true);

                SocketHandler.setSocket(sk);

                if (SocketHandler.markListenerStarted()) {
                    new Thread(new Listener(sk), "TcpListener").start();
                }
            } else {
                if (SocketHandler.markListenerStarted()) {
                    new Thread(new Listener(sk), "TcpListener").start();
                }
            }

            return sk;
        }
    }

    private void sendFrame(Socket sk, byte[] payload) throws IOException {
        byte[] header = String.format(Locale.US, "%09d|", payload.length)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] frame = new byte[header.length + payload.length];

        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);

        synchronized (SocketHandler.getSendLock()) {
            OutputStream out = sk.getOutputStream();
            out.write(frame);
            out.flush();
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];

        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);

        return r;
    }

    private void markServerDown() {
        serverMarkedDown = true;
    }

    private void notifyConnectionLostOnce() {
        if (connectionLostAlreadyNotified) {
            return;
        }

        connectionLostAlreadyNotified = true;

        try {
            Handler h = SocketHandler.getHandler();

            if (h == null) {
                h = handler;
            }

            if (h != null) {
                Message msg = h.obtainMessage();
                msg.obj = "srv~down";
                h.sendMessage(msg);
            }
        } catch (Exception ignored) {
        }

        ServerDownUI.showFromAnyThread();
    }

    private void safeResetSocket() {
        try {
            SocketHandler.reset();
        } catch (Exception ignored) {
        }
    }

    class Listener implements Runnable {

        private final Socket socket;
        private final DataInputStream dis;

        Listener(Socket sk) throws IOException {
            this.socket = sk;
            this.dis = new DataInputStream(new BufferedInputStream(sk.getInputStream()));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    byte[] header = new byte[LEN_SIZE + 1];
                    dis.readFully(header);

                    if (header[LEN_SIZE] != (byte) '|') {
                        throw new IOException("Malformed frame header: missing | delimiter");
                    }

                    String lenText = new String(header, 0, LEN_SIZE, StandardCharsets.US_ASCII);
                    int len = Integer.parseInt(lenText);

                    if (len < 0 || len > 10_000_000) {
                        throw new IOException("Invalid frame length: " + len);
                    }

                    byte[] payload = new byte[len];
                    dis.readFully(payload);

                    if (startsWith(payload, "PUB|")) {
                        handlePublicKey(payload);
                        continue;
                    }

                    if (startsWith(payload, "DATA|")) {
                        handleDataFrame(payload);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Listener error", e);

                markServerDown();
                notifyConnectionLostOnce();
                safeResetSocket();

            } finally {
                SocketHandler.markListenerStopped();
            }
        }

        private void handlePublicKey(byte[] payload) throws Exception {
            String base64Key = new String(
                    payload,
                    4,
                    payload.length - 4,
                    StandardCharsets.UTF_8
            );

            SocketHandler.setServerPublicKey(
                    CryptoUtils.parseRsaPublicKey(base64Key)
            );

            SecretKey aes = CryptoUtils.generateAesKey();
            SocketHandler.setAesKey(aes);

            byte[] encAes = CryptoUtils.rsaEncrypt(
                    SocketHandler.getServerPublicKey(),
                    aes.getEncoded()
            );

            sendFrame(
                    socket,
                    concat("KEY|".getBytes(StandardCharsets.US_ASCII), encAes)
            );

            serverMarkedDown = false;
            connectionLostAlreadyNotified = false;

            Log.d(TAG, "RSA/AES handshake completed");
        }

        private void handleDataFrame(byte[] payload) throws Exception {
            SecretKey aes = SocketHandler.getAesKey();

            if (aes == null) {
                Log.w(TAG, "Received DATA frame before AES key was ready");
                return;
            }

            byte[] decrypted = CryptoUtils.aesDecrypt(aes, slice(payload, 5));
            String text = new String(decrypted, StandardCharsets.UTF_8);

            if (text.startsWith("vky~")) {
                handleVoiceKey(text);
                return;
            }

            Handler h = SocketHandler.getHandler();

            if (h == null) {
                h = handler;
            }

            if (h != null) {
                Message msg = h.obtainMessage();
                msg.obj = decrypted;
                h.sendMessage(msg);
            }
        }

        private void handleVoiceKey(String text) {
            try {
                String[] p = text.split("~", 3);

                if (p.length >= 3) {
                    String roomId = p[1];

                    byte[] rawKey = Base64.decode(p[2], Base64.DEFAULT);
                    SecretKey voiceKey = new SecretKeySpec(rawKey, "AES");

                    SocketHandler.setVoiceKey(roomId, voiceKey);

                    Log.d(TAG, "Voice key received for room " + roomId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse voice key frame", e);
            }
        }

        private boolean startsWith(byte[] d, String s) {
            byte[] p = s.getBytes(StandardCharsets.US_ASCII);

            if (d.length < p.length) {
                return false;
            }

            for (int i = 0; i < p.length; i++) {
                if (d[i] != p[i]) {
                    return false;
                }
            }

            return true;
        }

        private byte[] slice(byte[] d, int from) {
            byte[] r = new byte[d.length - from];

            System.arraycopy(d, from, r, 0, r.length);

            return r;
        }
    }
}