package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public abstract class BaseGameActivity extends AppCompatActivity {

    protected String roomId;
    protected String myUsername;
    protected String explainerUser;
    protected int scoreA, scoreB;

    protected TextView tvTimer;
    protected TextView tvScoreA;
    protected TextView tvScoreB;

    // Dialog que mostramos cuando alguien se desconecta (para poder cerrarlo después)
    private AlertDialog disconnectDialog;

    protected final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Object o = msg.obj;
            if (o == null) return;
            String text = (o instanceof byte[])
                    ? new String((byte[]) o, StandardCharsets.UTF_8)
                    : String.valueOf(o);
            onServerMessage(text);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId     = getIntent().getStringExtra("roomId");
        myUsername = getIntent().getStringExtra("username");
        SocketHandler.setHandler(netHandler);
    }

    protected void onServerMessage(String text) {
        text = text.trim();

        if (text.startsWith("upd~")) {
            handleUpd(text);
        } else if (text.startsWith("wrd~")) {
            handleWrd(text);
        } else if (text.startsWith("gss~")) {
            handleGuessResult(text);
        } else if (text.startsWith("stg~")) {
            handleStartResult(text);
        } else if (text.startsWith("rol~")) {
            handleRolChange(text);
        } else if (text.startsWith("fin~")) {
            handleGameFinished(text);
        } else if (text.startsWith("end~")) {
            handleGameEnd(text);
        } else if (text.startsWith("dsc~")) {
            handlePlayerDisconnected(text);
        } else if (text.startsWith("rsm~")) {
            handleGameResumed(text);
        } else {
            onOtherMessage(text);
        }
    }

    // -------------------------------------------------------
    // rol~roomId~EXPLAINER|GUESSER|SPECTATOR
    // -------------------------------------------------------
    private void handleRolChange(String text) {
        String[] p = text.split("~");
        if (p.length < 3) return;
        String newRol = p[2].trim();

        Class<?> activityClass;
        if ("EXPLAINER".equals(newRol))    activityClass = activity_explainer.class;
        else if ("GUESSER".equals(newRol)) activityClass = activity_guesser.class;
        else                                activityClass = activity_spectator.class;

        if (!this.getClass().equals(activityClass)) {
            Intent intent = new Intent(this, activityClass);
            intent.putExtra("roomId",   roomId);
            intent.putExtra("username", myUsername);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }

    // -------------------------------------------------------
    // upd~roomId~explainer~timeLeft~scoreA~scoreB
    // -------------------------------------------------------
    private void handleUpd(String text) {
        String[] p = text.split("~");
        if (p.length < 6) return;

        explainerUser = p[2];
        int timeLeft  = safeInt(p[3]);
        scoreA        = safeInt(p[4]);
        scoreB        = safeInt(p[5]);

        if (tvTimer != null) tvTimer.setText(formatSeconds(timeLeft));
        updateScoresUI(scoreA, scoreB);
        onUpd(explainerUser, timeLeft, scoreA, scoreB);
    }

    // -------------------------------------------------------
    // wrd~roomId~WORD
    // -------------------------------------------------------
    private void handleWrd(String text) {
        String[] p = text.split("~", 3);
        if (p.length < 3) return;
        if (roomId != null && !roomId.equals(p[1])) return;
        onWord(p[2]);
    }

    // -------------------------------------------------------
    // gss~True | gss~False
    // -------------------------------------------------------
    private void handleGuessResult(String text) {
        String[] p = text.split("~");
        if (p.length < 2) return;
        onGuessResult("True".equalsIgnoreCase(p[1]));
    }

    // -------------------------------------------------------
    // stg~True | stg~False~reason
    // -------------------------------------------------------
    private void handleStartResult(String text) {
        String[] p = text.split("~");
        boolean ok    = p.length >= 2 && "True".equalsIgnoreCase(p[1]);
        String reason = p.length >= 3 ? p[2] : "";
        onStartResult(ok, reason);
    }

    // -------------------------------------------------------
    // fin~roomId~winner~scoreA~scoreB~team0players~team1players~reason
    // winner: 0, 1, o -1 (empate)
    // team0players / team1players: nombres separados por ";"
    // -------------------------------------------------------
    private void handleGameFinished(String text) {
        String[] p = text.split("~", 8);
        if (p.length < 7) return;

        int    winner      = safeInt(p[2]);
        int    sA          = safeInt(p[3]);
        int    sB          = safeInt(p[4]);
        String team0players = p.length > 5 ? p[5] : "";
        String team1players = p.length > 6 ? p[6] : "";
        String reason       = p.length > 7 ? p[7] : "";

        // Cerrar dialog de desconexión si estaba abierto
        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Intent intent = new Intent(this, activity_game_over.class);
        intent.putExtra("roomId",       roomId);
        intent.putExtra("username",     myUsername);
        intent.putExtra("winner",       winner);
        intent.putExtra("scoreA",       sA);
        intent.putExtra("scoreB",       sB);
        intent.putExtra("team0players", team0players);
        intent.putExtra("team1players", team1players);
        intent.putExtra("reason",       reason);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // -------------------------------------------------------
    // end~roomId~reason  — juego terminado abruptamente
    // -------------------------------------------------------
    private void handleGameEnd(String text) {
        String[] p   = text.split("~");
        String reason = p.length >= 3 ? p[2] : "GAME_OVER";

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Toast.makeText(this, "Juego terminado: " + reason, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // -------------------------------------------------------
    // dsc~roomId~username~secondsToWait
    // Un jugador se desconectó — juego pausado
    // -------------------------------------------------------
    private void handlePlayerDisconnected(String text) {
        String[] p = text.split("~");
        if (p.length < 4) return;

        String who     = p[2];
        String seconds = p[3];

        // Cerrar dialog anterior si lo había
        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        disconnectDialog = new AlertDialog.Builder(this)
                .setTitle("Jugador desconectado")
                .setMessage(who + " se desconectó.\nEsperando reconexión (" + seconds + "s)…\n\nEl juego está pausado.")
                .setCancelable(false)
                .create();
        disconnectDialog.show();
    }

    // -------------------------------------------------------
    // rsm~roomId~username
    // El jugador desconectado volvió — juego reanudado
    // -------------------------------------------------------
    private void handleGameResumed(String text) {
        String[] p = text.split("~");
        String who = p.length >= 3 ? p[2] : "El jugador";

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Toast.makeText(this, who + " reconectó. ¡Seguimos!", Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------
    // Helpers UI
    // -------------------------------------------------------
    protected void updateScoresUI(int a, int b) {
        if (tvScoreA != null) tvScoreA.setText(String.format(Locale.US, "TU EQUIPO: %d", a));
        if (tvScoreB != null) tvScoreB.setText(String.format(Locale.US, "RIVAL: %d", b));
    }

    protected static String formatSeconds(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    protected void sendToServer(String plainText) {
        new Thread(new tcp_send_recv(netHandler, plainText.getBytes(StandardCharsets.UTF_8))).start();
    }

    protected static int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // -------------------------------------------------------
    // Hooks abstractos
    // -------------------------------------------------------
    protected abstract void onUpd(String explainer, int timeLeft, int scoreA, int scoreB);
    protected abstract void onWord(String word);
    protected abstract void onGuessResult(boolean correct);
    protected abstract void onStartResult(boolean ok, String reason);
    protected void onOtherMessage(String text) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disconnectDialog != null) disconnectDialog.dismiss();
    }
}
