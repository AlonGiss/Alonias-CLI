package com.alongiss.Alonias;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import javax.crypto.SecretKey;

public class VoiceChatManager {

    private static final String TAG = "VoiceChat";

    private static final int    VOICE_PORT  = 11134;
    private static final String SERVER_IP   = "192.168.1.229";

    // Audio: 16kHz mono 16-bit PCM (~20ms por frame = 640 bytes)
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN  = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING    = AudioFormat.ENCODING_PCM_16BIT;

    private static final int FRAME_SAMPLES = SAMPLE_RATE / 50;   // 320
    private static final int FRAME_BYTES   = FRAME_SAMPLES * 2;  // 640
    private static final int RECV_BUF_SIZE = 4096;

    private static final int[] AUDIO_SOURCES = new int[] {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER
    };

    // Detección simple de "estoy hablando" SOLO para ducking del playback.
    // No corta envío. Solo baja el volumen del audio remoto mientras hablas.
    private static final double SPEAKING_RMS_THRESHOLD = 700.0;
    private static final int SPEAKING_HANGOVER_FRAMES = 12; // ~240 ms

    // Volúmenes del playback
    private static final float PLAYBACK_VOLUME_SPEAKER_IDLE   = 0.55f;
    private static final float PLAYBACK_VOLUME_SPEAKER_DUCKED = 0.08f;
    private static final float PLAYBACK_VOLUME_SPECTATOR      = 1.0f;

    /**
     * FIX IMPORTANTE:
     * Como al cambiar de Activity el manager nuevo puede arrancar ANTES de que
     * el viejo haga onDestroy()/stop(), el viejo NO debe restaurar el audio si
     * ya existe uno más nuevo activo.
     *
     * latestRoutingOwnerToken identifica al manager más reciente que configuró audio.
     * Solo ese manager puede restaurarlo al hacer stop().
     */
    private static final Object ROUTING_LOCK = new Object();
    private static long routingTokenCounter = 0L;
    private static long latestRoutingOwnerToken = 0L;

    private final Context appContext;
    private final String  roomId;
    private final String  username;
    private final boolean canSpeak;

    private volatile boolean muted   = false;
    private volatile boolean running = false;

    private Thread captureThread;
    private Thread playbackThread;
    private Thread registrationThread;

    private DatagramSocket sharedSocket;
    private volatile int lastWorkingAudioSource = -1;

    private AudioManager audioManager;
    private AudioTrack playbackTrack;

    private int previousMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphoneOn = false;

    // Token de este manager para ownership del audio routing
    private long myRoutingToken = 0L;

    // Estado compartido para ducking de speaker mientras hablo
    private volatile boolean localSpeechLikely = false;
    private volatile int speechHangoverFrames = 0;
    private volatile float lastAppliedPlaybackVolume = -1f;

    public VoiceChatManager(Context context, String roomId, String username, boolean canSpeak) {
        this.appContext = context.getApplicationContext();
        this.roomId = roomId;
        this.username = username;
        this.canSpeak = canSpeak;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        try {
            sharedSocket = new DatagramSocket();
            sharedSocket.setSoTimeout(0);
            Log.d(TAG, "SharedSocket bound to local port " + sharedSocket.getLocalPort()
                    + " -> will send/recv on this port");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create shared socket", e);
            running = false;
            return;
        }

        configureAudioRouting();
        startRegistrationHeartbeat();
        startPlayback();

        if (canSpeak) {
            startCapture();
        } else {
            Log.d(TAG, "canSpeak=false -> capture disabled (spectator)");
        }
    }

    public synchronized void stop() {
        running = false;

        try {
            if (sharedSocket != null && !sharedSocket.isClosed()) {
                sharedSocket.close();
            }
        } catch (Exception ignored) {}

        if (registrationThread != null) {
            registrationThread.interrupt();
            registrationThread = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        if (playbackTrack != null) {
            try { playbackTrack.stop(); } catch (Exception ignored) {}
            try { playbackTrack.release(); } catch (Exception ignored) {}
            playbackTrack = null;
        }

        restoreAudioRoutingIfOwner();
        Log.d(TAG, "VoiceChatManager stopped");
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        Log.d(TAG, "Mute set to " + muted);
    }

    public boolean isMuted() {
        return muted;
    }

    // ---------------------------------------------------------------------
    // AUDIO ROUTING
    // ---------------------------------------------------------------------
    private void configureAudioRouting() {
        try {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                Log.w(TAG, "AudioManager is null");
                return;
            }

            synchronized (ROUTING_LOCK) {
                previousMode = audioManager.getMode();
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

                myRoutingToken = ++routingTokenCounter;
                latestRoutingOwnerToken = myRoutingToken;
            }

            if (canSpeak) {
                // Seguimos en modo comunicación para favorecer AEC,
                // PERO con speaker ON para usar siempre altavoz.
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
                Log.d(TAG, "Audio routing configured: MODE_IN_COMMUNICATION + speaker ON"
                        + " token=" + myRoutingToken
                        + " prevMode=" + previousMode
                        + " prevSpeaker=" + previousSpeakerphoneOn);
            } else {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(true);
                Log.d(TAG, "Audio routing configured: MODE_NORMAL + speaker ON"
                        + " token=" + myRoutingToken
                        + " prevMode=" + previousMode
                        + " prevSpeaker=" + previousSpeakerphoneOn);
            }
        } catch (Exception e) {
            Log.e(TAG, "configureAudioRouting failed", e);
        }
    }

    /**
     * Solo restaura si este manager sigue siendo el dueño más reciente del routing.
     * Si ya arrancó otro manager nuevo, NO tocamos nada para no romperle el audio.
     */
    private void restoreAudioRoutingIfOwner() {
        try {
            if (audioManager == null) return;

            boolean iAmLatestOwner;
            synchronized (ROUTING_LOCK) {
                iAmLatestOwner = (myRoutingToken != 0L && myRoutingToken == latestRoutingOwnerToken);
            }

            if (!iAmLatestOwner) {
                Log.d(TAG, "Skipping audio routing restore because a newer manager owns it."
                        + " myToken=" + myRoutingToken
                        + " latestToken=" + latestRoutingOwnerToken);
                return;
            }

            audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
            audioManager.setMode(previousMode);

            Log.d(TAG, "Audio routing restored by owner"
                    + " token=" + myRoutingToken
                    + " -> mode=" + previousMode
                    + " speaker=" + previousSpeakerphoneOn);
        } catch (Exception e) {
            Log.e(TAG, "restoreAudioRoutingIfOwner failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // REGISTRATION / HEARTBEAT
    // ---------------------------------------------------------------------
    private void startRegistrationHeartbeat() {
        registrationThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                sendRegistration();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Log.d(TAG, "Registration thread finished");
        }, "VoiceRegister");

        registrationThread.start();
    }

    private void sendRegistration() {
        try {
            if (sharedSocket == null || sharedSocket.isClosed()) return;

            InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
            byte[] payload = ("voice_join|" + roomId + "|" + username).getBytes("UTF-8");
            DatagramPacket pkt = new DatagramPacket(payload, payload.length, serverAddr, VOICE_PORT);
            sharedSocket.send(pkt);

            Log.d(TAG, "Registration packet sent roomId=" + roomId
                    + " user=" + username
                    + " localPort=" + sharedSocket.getLocalPort());
        } catch (Exception e) {
            Log.e(TAG, "Registration send failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // CAPTURE — mic -> Android processing -> AES-GCM -> UDP -> server
    // ---------------------------------------------------------------------
    private void startCapture() {
        captureThread = new Thread(() -> {
            AudioRecord recorder = null;
            NoiseSuppressor noiseSuppressor = null;
            AcousticEchoCanceler echoCanceler = null;
            AutomaticGainControl agc = null;

            try {
                int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
                if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf <= 0) {
                    Log.e(TAG, "AudioRecord.getMinBufferSize invalid: " + minBuf);
                    return;
                }

                int recorderBuf = Math.max(minBuf, FRAME_BYTES * 8);

                recorder = createRecorderWithFallback(recorderBuf);
                if (recorder == null) {
                    Log.e(TAG, "Could not initialize AudioRecord with any supported source");
                    return;
                }

                int audioSessionId = recorder.getAudioSessionId();

                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                        if (noiseSuppressor != null) {
                            noiseSuppressor.setEnabled(true);
                            Log.d(TAG, "NoiseSuppressor enabled");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "NoiseSuppressor create failed", e);
                    }
                } else {
                    Log.d(TAG, "NoiseSuppressor not available");
                }

                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(audioSessionId);
                        if (echoCanceler != null) {
                            echoCanceler.setEnabled(true);
                            Log.d(TAG, "AcousticEchoCanceler enabled");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "AcousticEchoCanceler create failed", e);
                    }
                } else {
                    Log.d(TAG, "AcousticEchoCanceler not available");
                }

                if (AutomaticGainControl.isAvailable()) {
                    try {
                        agc = AutomaticGainControl.create(audioSessionId);
                        if (agc != null) {
                            agc.setEnabled(true);
                            Log.d(TAG, "AutomaticGainControl enabled");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "AutomaticGainControl create failed", e);
                    }
                } else {
                    Log.d(TAG, "AutomaticGainControl not available");
                }

                Log.d(TAG, "AudioRecord initialized successfully with source="
                        + audioSourceName(lastWorkingAudioSource)
                        + " sampleRate=" + SAMPLE_RATE
                        + " recorderBuf=" + recorderBuf
                        + " minBuf=" + minBuf);

                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                byte[] audioBuf = new byte[FRAME_BYTES];
                byte[] headerBytes = ("voice|" + roomId + "|" + username + "|").getBytes("UTF-8");

                recorder.startRecording();

                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "AudioRecord.startRecording() did not enter RECORDING state");
                    return;
                }

                Log.d(TAG, "AudioRecord started source=" + audioSourceName(lastWorkingAudioSource)
                        + " sampleRate=" + SAMPLE_RATE
                        + " frameBytes=" + FRAME_BYTES);

                int packetsSent = 0;
                long lastNoKeyLog = 0;
                long lastSpeechLog = 0;

                while (running && !Thread.currentThread().isInterrupted()) {
                    int read = recorder.read(audioBuf, 0, FRAME_BYTES);

                    if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord.read: ERROR_INVALID_OPERATION");
                        break;
                    }
                    if (read == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord.read: ERROR_BAD_VALUE");
                        break;
                    }
                    if (read <= 0) {
                        Log.w(TAG, "AudioRecord.read returned " + read + " - skipping");
                        continue;
                    }

                    double rms = computeRms16(audioBuf, read);
                    updateLocalSpeechState(rms);

                    long now = System.currentTimeMillis();
                    if (now - lastSpeechLog > 3000) {
                        Log.d(TAG, "Mic RMS=" + rms + " localSpeechLikely=" + localSpeechLikely);
                        lastSpeechLog = now;
                    }

                    if (muted) {
                        continue;
                    }

                    SecretKey voiceKey = SocketHandler.getVoiceKeyForRoom(roomId);
                    if (voiceKey == null) {
                        if (now - lastNoKeyLog > 3000) {
                            Log.w(TAG, "No voice AES key yet for room " + roomId + " - not sending audio");
                            lastNoKeyLog = now;
                        }
                        continue;
                    }

                    byte[] plainFrame = new byte[read];
                    System.arraycopy(audioBuf, 0, plainFrame, 0, read);

                    byte[] encryptedFrame = CryptoUtils.aesEncrypt(voiceKey, plainFrame);
                    byte[] payload = new byte[headerBytes.length + encryptedFrame.length];
                    System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
                    System.arraycopy(encryptedFrame, 0, payload, headerBytes.length, encryptedFrame.length);

                    DatagramPacket packet = new DatagramPacket(payload, payload.length, serverAddr, VOICE_PORT);
                    sharedSocket.send(packet);

                    packetsSent++;
                    if (packetsSent % 50 == 0) {
                        Log.d(TAG, "SEND packets=" + packetsSent
                                + " plainAudioBytes=" + read
                                + " encAudioBytes=" + encryptedFrame.length
                                + " totalPayload=" + payload.length
                                + " rms=" + rms
                                + " src=" + audioSourceName(lastWorkingAudioSource)
                                + " dst=" + SERVER_IP + ":" + VOICE_PORT);
                    }
                }

                Log.d(TAG, "Capture loop ended totalSent=" + packetsSent);

            } catch (Exception e) {
                Log.e(TAG, "Capture thread error", e);
            } finally {
                localSpeechLikely = false;
                speechHangoverFrames = 0;
                if (noiseSuppressor != null) {
                    try { noiseSuppressor.release(); } catch (Exception ignored) {}
                }
                if (echoCanceler != null) {
                    try { echoCanceler.release(); } catch (Exception ignored) {}
                }
                if (agc != null) {
                    try { agc.release(); } catch (Exception ignored) {}
                }
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) {}
                    try { recorder.release(); } catch (Exception ignored) {}
                }
                Log.d(TAG, "Capture thread finished");
            }
        }, "VoiceCapture");

        captureThread.start();
    }

    private AudioRecord createRecorderWithFallback(int recorderBuf) {
        for (int source : AUDIO_SOURCES) {
            AudioRecord candidate = null;
            try {
                candidate = new AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        ENCODING,
                        recorderBuf
                );

                int state = candidate.getState();
                if (state == AudioRecord.STATE_INITIALIZED) {
                    lastWorkingAudioSource = source;
                    Log.d(TAG, "AudioRecord init OK with source=" + audioSourceName(source));
                    return candidate;
                } else {
                    Log.w(TAG, "AudioRecord init failed with source=" + audioSourceName(source)
                            + " state=" + state);
                }
            } catch (Exception e) {
                Log.w(TAG, "AudioRecord exception with source=" + audioSourceName(source), e);
            }

            if (candidate != null) {
                try { candidate.release(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // PLAYBACK — server -> UDP -> AES-GCM decrypt -> AudioTrack
    // ---------------------------------------------------------------------
    private void startPlayback() {
        playbackThread = new Thread(() -> {
            try {
                int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
                if (minBuf == AudioTrack.ERROR_BAD_VALUE || minBuf <= 0) {
                    Log.e(TAG, "AudioTrack.getMinBufferSize invalid: " + minBuf);
                    return;
                }

                playbackTrack = new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                        )
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setSampleRate(SAMPLE_RATE)
                                        .setEncoding(ENCODING)
                                        .setChannelMask(CHANNEL_OUT)
                                        .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(minBuf * 4)
                        .build();

                if (playbackTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack failed to initialize");
                    return;
                }

                applyPlaybackVolume(getDesiredPlaybackVolume());

                playbackTrack.play();
                Log.d(TAG, "AudioTrack started sampleRate=" + SAMPLE_RATE
                        + " minBuf=" + minBuf
                        + " listeningOn=sharedSocket port " + sharedSocket.getLocalPort());

                int packetsRecv = 0;
                long lastNoKeyLog = 0;
                long lastVolCheckLog = 0;

                while (running && !Thread.currentThread().isInterrupted()) {
                    byte[] buf = new byte[RECV_BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    try {
                        sharedSocket.receive(packet);
                    } catch (Exception e) {
                        if (!running) break;
                        Log.w(TAG, "receive() exception: " + e.getMessage());
                        continue;
                    }

                    applyPlaybackVolume(getDesiredPlaybackVolume());

                    long now = System.currentTimeMillis();
                    if (now - lastVolCheckLog > 3000) {
                        Log.d(TAG, "Playback volume state localSpeechLikely=" + localSpeechLikely
                                + " targetVol=" + getDesiredPlaybackVolume());
                        lastVolCheckLog = now;
                    }

                    int length = packet.getLength();
                    if (length <= 0) {
                        continue;
                    }

                    if (startsWith(buf, "voice_join|")) {
                        continue;
                    }

                    if (!startsWith(buf, "voice|")) {
                        Log.w(TAG, "Unknown packet prefix len=" + length + " - skipping");
                        continue;
                    }

                    int firstBar  = indexOf(buf, (byte) '|', 0, length);
                    int secondBar = indexOf(buf, (byte) '|', firstBar + 1, length);
                    int thirdBar  = indexOf(buf, (byte) '|', secondBar + 1, length);

                    if (firstBar < 0 || secondBar < 0 || thirdBar < 0) {
                        Log.w(TAG, "Malformed voice packet - missing separators");
                        continue;
                    }

                    String pktRoomId = safeString(buf, firstBar + 1, secondBar - firstBar - 1);
                    String sender    = safeString(buf, secondBar + 1, thirdBar - secondBar - 1);

                    if (!roomId.equals(pktRoomId)) {
                        continue;
                    }

                    if (username.equals(sender)) {
                        continue;
                    }

                    int audioOffset = thirdBar + 1;
                    int encLen = length - audioOffset;
                    if (encLen <= 0) {
                        continue;
                    }

                    SecretKey voiceKey = SocketHandler.getVoiceKeyForRoom(roomId);
                    if (voiceKey == null) {
                        if (now - lastNoKeyLog > 3000) {
                            Log.w(TAG, "No voice AES key yet for room " + roomId + " - cannot decrypt audio");
                            lastNoKeyLog = now;
                        }
                        continue;
                    }

                    byte[] encryptedAudio = new byte[encLen];
                    System.arraycopy(buf, audioOffset, encryptedAudio, 0, encLen);

                    byte[] plainAudio;
                    try {
                        plainAudio = CryptoUtils.aesDecrypt(voiceKey, encryptedAudio);
                    } catch (Exception e) {
                        Log.w(TAG, "Voice decrypt failed from=" + sender + " encLen=" + encLen, e);
                        continue;
                    }

                    int written = playbackTrack.write(plainAudio, 0, plainAudio.length);

                    packetsRecv++;
                    if (packetsRecv % 50 == 0) {
                        Log.d(TAG, "RECV packets=" + packetsRecv
                                + " from=" + sender
                                + " roomId=" + pktRoomId
                                + " encLen=" + encLen
                                + " plainLen=" + plainAudio.length
                                + " written=" + written
                                + " src=" + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                    }
                }

                Log.d(TAG, "Playback loop ended totalRecv=" + packetsRecv);

            } catch (Exception e) {
                Log.e(TAG, "Playback thread error", e);
            } finally {
                if (playbackTrack != null) {
                    try { playbackTrack.stop(); } catch (Exception ignored) {}
                    try { playbackTrack.release(); } catch (Exception ignored) {}
                    playbackTrack = null;
                }
                Log.d(TAG, "Playback thread finished");
            }
        }, "VoicePlayback");

        playbackThread.start();
    }

    // ---------------------------------------------------------------------
    // SPEECH / DUCKING HELPERS
    // ---------------------------------------------------------------------
    private void updateLocalSpeechState(double rms) {
        if (!canSpeak) {
            localSpeechLikely = false;
            speechHangoverFrames = 0;
            return;
        }

        if (rms >= SPEAKING_RMS_THRESHOLD) {
            localSpeechLikely = true;
            speechHangoverFrames = SPEAKING_HANGOVER_FRAMES;
        } else if (speechHangoverFrames > 0) {
            speechHangoverFrames--;
            localSpeechLikely = true;
        } else {
            localSpeechLikely = false;
        }
    }

    private float getDesiredPlaybackVolume() {
        if (!canSpeak) {
            return PLAYBACK_VOLUME_SPECTATOR;
        }
        return localSpeechLikely ? PLAYBACK_VOLUME_SPEAKER_DUCKED : PLAYBACK_VOLUME_SPEAKER_IDLE;
    }

    private void applyPlaybackVolume(float volume) {
        if (playbackTrack == null) return;
        if (Math.abs(lastAppliedPlaybackVolume - volume) < 0.001f) return;
        try {
            playbackTrack.setVolume(volume);
            lastAppliedPlaybackVolume = volume;
        } catch (Exception e) {
            Log.w(TAG, "setVolume failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------
    private static int indexOf(byte[] data, byte value, int from, int max) {
        for (int i = from; i < max; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    private static boolean startsWith(byte[] data, String prefix) {
        byte[] p = prefix.getBytes();
        if (data.length < p.length) return false;
        for (int i = 0; i < p.length; i++) {
            if (data[i] != p[i]) return false;
        }
        return true;
    }

    private static String safeString(byte[] data, int offset, int len) {
        try {
            if (offset < 0 || len < 0 || offset + len > data.length) return "";
            return new String(data, offset, len, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String audioSourceName(int source) {
        if (source == MediaRecorder.AudioSource.MIC) return "MIC";
        if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) return "VOICE_COMMUNICATION";
        if (source == MediaRecorder.AudioSource.DEFAULT) return "DEFAULT";
        if (source == MediaRecorder.AudioSource.CAMCORDER) return "CAMCORDER";
        return "SOURCE_" + source;
    }

    private static double computeRms16(byte[] pcm, int len) {
        int samples = len / 2;
        if (samples <= 0) return 0.0;

        double sum = 0.0;
        for (int i = 0; i + 1 < len; i += 2) {
            int lo = pcm[i] & 0xFF;
            int hi = pcm[i + 1];
            short s = (short) ((hi << 8) | lo);
            double v = s;
            sum += v * v;
        }

        return Math.sqrt(sum / samples);
    }
}