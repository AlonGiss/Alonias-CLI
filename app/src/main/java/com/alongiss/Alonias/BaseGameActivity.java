package com.alongiss.Alonias;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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

    private AlertDialog disconnectDialog;
    private AlertDialog countdownDialog;
    private AlertDialog serverDownDialog;

    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (roomId != null && !roomId.isEmpty()) {
                sendToServer("hrb~" + roomId);
            }

            heartbeatHandler.postDelayed(this, 3000);
        }
    };
    protected final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Object o = msg.obj;

            if (o == null) {
                return;
            }

            String text = (o instanceof byte[])
                    ? new String((byte[]) o, StandardCharsets.UTF_8)
                    : String.valueOf(o);

            onServerMessage(text);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        roomId = getIntent().getStringExtra("roomId");
        myUsername = getIntent().getStringExtra("username");

        SocketHandler.setHandler(netHandler);

        heartbeatHandler.postDelayed(heartbeatRunnable, 3000);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });
    }

    protected void showLeaveGameDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Leave game?")
                .setMessage("If you leave, the match will end for the other players.")
                .setPositiveButton("Leave", (dialog, which) -> leaveGameAndGoHome())
                .setNegativeButton("Cancel", null)
                .show();
    }

    protected void leaveGameAndGoHome() {
        /*
         * We do NOT close the socket here.
         * Closing the socket would force the user to log in again.
         *
         * Instead, we tell the server this is a voluntary leave.
         * The server will end the match for the OTHER players with fin~...
         */
        if (roomId != null && !roomId.isEmpty()) {
            sendToServer("lgm~" + roomId);
        }

        Intent intent = new Intent(BaseGameActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
        finish();
    }
    protected void onServerMessage(String text) {
        text = text.trim();

        if (text.startsWith("srv~down")) {
            handleServerDown();
        } else if (text.startsWith("upd~")) {
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
        } else if (text.startsWith("hst~")) {
            handleHostTransfer(text);
        } else if (text.startsWith("cd~")) {
            handleCountdown(text);
        } else if (text.startsWith("err~")) {
            String[] p = text.split("~", 2);
            String reason = p.length >= 2 ? p[1] : "";
            Toast.makeText(this, ClientMessageUtils.genericServerError(reason), Toast.LENGTH_SHORT).show();
        } else {
            onOtherMessage(text);
        }
    }

    private void handleServerDown() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        if (countdownDialog != null && countdownDialog.isShowing()) {
            countdownDialog.dismiss();
        }

        if (serverDownDialog != null && serverDownDialog.isShowing()) {
            return;
        }

        serverDownDialog = new AlertDialog.Builder(this)
                .setTitle("Connection lost")
                .setMessage(ClientMessageUtils.connectionLostMessage())
                .setCancelable(false)
                .setPositiveButton("Back to login", (dialog, which) -> {
                    SocketHandler.reset();

                    Intent intent = new Intent(BaseGameActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                    finish();
                })
                .create();

        serverDownDialog.show();
    }

    private void handleRolChange(String text) {
        String[] p = text.split("~");

        if (p.length < 3) {
            return;
        }

        String newRol = p[2].trim();

        Class<?> activityClass;

        if ("EXPLAINER".equals(newRol)) {
            activityClass = activity_explainer.class;
        } else if ("GUESSER".equals(newRol)) {
            activityClass = activity_guesser.class;
        } else {
            activityClass = activity_spectator.class;
        }

        if (!this.getClass().equals(activityClass)) {
            Intent intent = new Intent(this, activityClass);

            intent.putExtra("roomId", roomId);
            intent.putExtra("username", myUsername);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish();
        }
    }

    private void handleCountdown(String text) {
        String[] p = text.split("~");

        if (p.length < 3) {
            return;
        }

        if (roomId != null && !roomId.equals(p[1])) {
            return;
        }

        String n = p[2].trim();

        if (countdownDialog != null && countdownDialog.isShowing()) {
            countdownDialog.dismiss();
        }

        countdownDialog = new AlertDialog.Builder(this)
                .setTitle("")
                .setMessage(n)
                .setCancelable(false)
                .create();

        if (countdownDialog.getWindow() != null) {
            countdownDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        countdownDialog.show();

        TextView msgView = countdownDialog.findViewById(android.R.id.message);

        if (msgView != null) {
            msgView.setTextSize(72);
            msgView.setGravity(android.view.Gravity.CENTER);
            msgView.setTextColor(0xFFFFFFFF);
        }
    }

    private void handleUpd(String text) {
        if (countdownDialog != null && countdownDialog.isShowing()) {
            countdownDialog.dismiss();
        }

        String[] p = text.split("~");

        if (p.length < 6) {
            return;
        }

        explainerUser = p[2];

        int timeLeft = safeInt(p[3]);
        scoreA = safeInt(p[4]);
        scoreB = safeInt(p[5]);

        if (tvTimer != null) {
            tvTimer.setText(formatSeconds(timeLeft));
        }

        updateScoresUI(scoreA, scoreB);

        onUpd(explainerUser, timeLeft, scoreA, scoreB);
    }

    private void handleWrd(String text) {
        String[] p = text.split("~", 3);

        if (p.length < 3) {
            return;
        }

        if (roomId != null && !roomId.equals(p[1])) {
            return;
        }

        onWord(p[2]);
    }

    private void handleGuessResult(String text) {
        String[] p = text.split("~");

        if (p.length < 2) {
            return;
        }

        onGuessResult("True".equalsIgnoreCase(p[1]));
    }

    private void handleStartResult(String text) {
        String[] p = text.split("~");

        boolean ok = p.length >= 2 && "True".equalsIgnoreCase(p[1]);
        String reason = p.length >= 3 ? p[2] : "";

        onStartResult(ok, reason);
    }

    private void handleGameFinished(String text) {
        String[] p = text.split("~", 8);

        if (p.length < 7) {
            return;
        }

        int winner = safeInt(p[2]);
        int sA = safeInt(p[3]);
        int sB = safeInt(p[4]);

        String team0players = p.length > 5 ? p[5] : "";
        String team1players = p.length > 6 ? p[6] : "";
        String reason = p.length > 7 ? p[7] : "";

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Intent intent = new Intent(this, game_over.class);

        intent.putExtra("roomId", roomId);
        intent.putExtra("username", myUsername);
        intent.putExtra("winner", winner);
        intent.putExtra("scoreA", sA);
        intent.putExtra("scoreB", sB);
        intent.putExtra("team0players", team0players);
        intent.putExtra("team1players", team1players);
        intent.putExtra("reason", reason);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
        finish();
    }

    private void handleGameEnd(String text) {
        String[] p = text.split("~");

        String reason = p.length >= 3 ? p[2] : "GAME_OVER";

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Toast.makeText(this, ClientMessageUtils.gameEndedMessage(reason), Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, HomeActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
        finish();
    }

    private void handlePlayerDisconnected(String text) {
        String[] p = text.split("~");

        if (p.length < 4) {
            return;
        }

        String who = p[2];
        String seconds = p[3];

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        disconnectDialog = new AlertDialog.Builder(this)
                .setTitle("Player disconnected")
                .setMessage(ClientMessageUtils.playerDisconnectedMessage(who) + "\n\nWaiting " + seconds + "s.")
                .setCancelable(false)
                .create();

        disconnectDialog.show();
    }

    private void handleGameResumed(String text) {
        String[] p = text.split("~");

        String who = p.length >= 3 ? p[2] : "";

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        Toast.makeText(this, ClientMessageUtils.playerReconnectedMessage(who), Toast.LENGTH_SHORT).show();
    }

    private void handleHostTransfer(String text) {
        String[] p = text.split("~");

        if (p.length < 3) {
            return;
        }

        onHostTransferred(p[2]);
    }

    protected void onHostTransferred(String newHostUsername) {
    }

    protected void updateScoresUI(int a, int b) {
        if (tvScoreA != null) {
            tvScoreA.setText(String.format(Locale.US, (getString(R.string.team_a)) + " %d", a));
        }

        if (tvScoreB != null) {
            tvScoreB.setText(String.format(Locale.US, ((getString(R.string.team_b)) + ": %d"), b));
        }
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
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    protected abstract void onUpd(String explainer, int timeLeft, int scoreA, int scoreB);

    protected abstract void onWord(String word);

    protected abstract void onGuessResult(boolean correct);

    protected abstract void onStartResult(boolean ok, String reason);

    protected void onOtherMessage(String text) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        heartbeatHandler.removeCallbacks(heartbeatRunnable);

        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
        }

        if (countdownDialog != null && countdownDialog.isShowing()) {
            countdownDialog.dismiss();
        }

        if (serverDownDialog != null && serverDownDialog.isShowing()) {
            serverDownDialog.dismiss();
        }
    }
}