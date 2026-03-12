package com.alongiss.testnetworkyossi;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.SecretKey;

public class tcp_send_recv implements Runnable {

    private static final int LEN_SIZE = 9;
    private final String ip = "192.168.1.229";
    private static final int port = 11133;
    private final byte[] plainPayload;
    private final Handler handler;

    public tcp_send_recv(Handler h, byte[] data) {
        this.handler = h;
        this.plainPayload = data;
        SocketHandler.setHandler(h);
    }

    @Override
    public void run() {
        try {
            Socket sk = SocketHandler.getSocket();
            if (sk == null) {
                sk = new Socket(ip, port);
                SocketHandler.setSocket(sk);
                new Thread(new Listener(sk)).start();
            }

            // wait until AES ready (after PUB exchange)
            while (!SocketHandler.readyForEncryptedTraffic()) {
                Thread.sleep(10);
            }

            SecretKey aes = SocketHandler.getAesKey();
            byte[] encrypted = CryptoUtils.aesEncrypt(aes, plainPayload);

            sendFrame(sk, concat("DATA|".getBytes(), encrypted));

        } catch (Exception e) {
            Log.e("TCP", "Send error", e);
        }
    }

    private void sendFrame(Socket sk, byte[] payload) throws IOException {
        String header = String.format(Locale.US, "%09d|", payload.length);
        OutputStream out = sk.getOutputStream();
        out.write(header.getBytes());
        out.write(payload);
        out.flush();
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    class Listener implements Runnable {
        private final DataInputStream dis;

        Listener(Socket sk) throws IOException {
            dis = new DataInputStream(new BufferedInputStream(sk.getInputStream()));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    byte[] header = new byte[LEN_SIZE + 1];
                    dis.readFully(header);
                    int len = Integer.parseInt(new String(header, 0, LEN_SIZE));
                    byte[] payload = new byte[len];
                    dis.readFully(payload);

                    if (startsWith(payload, "PUB|")) {
                        String base64Key = new String(payload, 4, payload.length - 4);
                        SocketHandler.setServerPublicKey(
                                CryptoUtils.parseRsaPublicKey(base64Key)
                        );

                        SecretKey aes = CryptoUtils.generateAesKey();
                        SocketHandler.setAesKey(aes);

                        byte[] encAes = CryptoUtils.rsaEncrypt(
                                SocketHandler.getServerPublicKey(),
                                aes.getEncoded()
                        );

                        sendFrame(SocketHandler.getSocket(),
                                concat("KEY|".getBytes(), encAes)
                        );
                        continue;
                    }

                    if (startsWith(payload, "DATA|")) {
                        byte[] decrypted = CryptoUtils.aesDecrypt(
                                SocketHandler.getAesKey(),
                                slice(payload, 5)
                        );

                        Handler h = SocketHandler.getHandler();
                        if (h != null) {
                            Message msg = h.obtainMessage();
                            msg.obj = decrypted;
                            h.sendMessage(msg);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("TCP", "Listener error", e);
                SocketHandler.reset();
            }
        }

        private boolean startsWith(byte[] d, String s) {
            byte[] p = s.getBytes(StandardCharsets.US_ASCII);
            if (d.length < p.length) return false;
            for (int i = 0; i < p.length; i++)
                if (d[i] != p[i]) return false;
            return true;
        }

        private byte[] slice(byte[] d, int from) {
            byte[] r = new byte[d.length - from];
            System.arraycopy(d, from, r, 0, r.length);
            return r;
        }
    }
}
