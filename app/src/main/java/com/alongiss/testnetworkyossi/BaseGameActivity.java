package com.alongiss.testnetworkyossi;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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

    protected final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Object o = msg.obj;
            if (o == null) return;

            String text;
            if (o instanceof byte[]) {
                text = new String((byte[]) o, StandardCharsets.UTF_8);
            } else {
                text = String.valueOf(o);
            }
            Log.d("RECV_MSG", text);

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
        //text = text.trim();

        //Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

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
        } else if (text.startsWith("end~")) {
            handleGameEnd(text);
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
        if ("EXPLAINER".equals(newRol))       activityClass = activity_explainer.class;
        else if ("GUESSER".equals(newRol))    activityClass = activity_guesser.class;
        else                                   activityClass = activity_spectator.class;

        if (!this.getClass().equals(activityClass)) {
            android.content.Intent intent = new android.content.Intent(this, activityClass);
            intent.putExtra("roomId",   roomId);
            intent.putExtra("username", myUsername);
            // FLAG para que no se acumulen Activities en el back stack
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                          | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

        // p[1] = roomId  (ignoramos si no filtramos por sala)
        explainerUser = p[2];
        int timeLeft  = safeInt(p[3]);
        scoreA        = safeInt(p[4]);
        scoreB        = safeInt(p[5]);

        // Actualizar timer visual directamente con los segundos del servidor
        if (tvTimer != null) {
            tvTimer.setText(formatSeconds(timeLeft));
        }

        updateScoresUI(scoreA, scoreB);

        // *** FIX PRINCIPAL: llamar al hook para que las subclases actualicen su UI ***
        onUpd(explainerUser, timeLeft, scoreA, scoreB);
    }

    // -------------------------------------------------------
    // wrd~roomId~WORD
    // -------------------------------------------------------
    private void handleWrd(String text) {
        String[] p = text.split("~", 3);
        if (p.length < 3) return;

        String rid  = p[1];
        String word = p[2];

        if (roomId != null && !roomId.equals(rid)) return;

        onWord(word);
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
        boolean ok  = (p.length >= 2) && "True".equalsIgnoreCase(p[1]);
        String reason = (p.length >= 3) ? p[2] : "";
        onStartResult(ok, reason);
    }

    // -------------------------------------------------------
    // end~roomId~reason  — el juego terminó (ej: jugador se fue)
    // -------------------------------------------------------
    private void handleGameEnd(String text) {
        String[] p = text.split("~");
        String reason = (p.length >= 3) ? p[2] : "GAME_OVER";

        String msg;
        switch (reason) {
            case "NOT_ENOUGH_PLAYERS": msg = "Un jugador se fue. Fin del juego."; break;
            default:                   msg = "Juego terminado."; break;
        }

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        // Volver a la pantalla principal (ajusta MainActivity por el nombre real tuyo)
        android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                      | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
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

    // -------------------------------------------------------
    // Red
    // -------------------------------------------------------
    protected void sendToServer(String plainText) {
        new Thread(new tcp_send_recv(netHandler, plainText.getBytes(StandardCharsets.UTF_8))).start();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    protected static int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // -------------------------------------------------------
    // Hooks — implementar en cada Activity
    // timeLeft = segundos restantes en el turno
    // -------------------------------------------------------
    protected abstract void onUpd(String explainer, int timeLeft, int scoreA, int scoreB);
    protected abstract void onWord(String word);
    protected abstract void onGuessResult(boolean correct);
    protected abstract void onStartResult(boolean ok, String reason);

    protected void onOtherMessage(String text) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
