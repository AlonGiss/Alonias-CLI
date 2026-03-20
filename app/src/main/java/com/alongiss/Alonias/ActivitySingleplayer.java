package com.alongiss.Alonias;

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

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ActivitySingleplayer extends AppCompatActivity {

    private TextView tvWord;
    private TextView tvScore;
    private TextView tvTimer;
    private MaterialButton btnCorrect;
    private MaterialButton btnSkip;

    private int score = 0;
    private String difficulty;
    private int roundTimeSec = 60;
    private int timeLeftSec = 60;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;
    private AlertDialog timeUpDialog;

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
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
        setContentView(R.layout.activity_singleplayer);
        SocketHandler.setHandler(netHandler);

        tvWord = findViewById(R.id.tvWordSingle);
        tvScore = findViewById(R.id.tvScoreSingle);
        tvTimer = findViewById(R.id.tvTimerSingle);
        btnCorrect = findViewById(R.id.btnCorrect);
        btnSkip = findViewById(R.id.btnSkipSingle);

        difficulty = getIntent().getStringExtra("difficulty");
        if (difficulty == null || difficulty.isEmpty()) {
            difficulty = "Easy";
        }

        roundTimeSec = getIntent().getIntExtra("roundTime", 60);
        if (roundTimeSec <= 0) roundTimeSec = 60;

        resetAndStartGame();

        btnCorrect.setOnClickListener(v -> {
            if (timeLeftSec <= 0) return;
            score += 1;
            updateScoreUI();
            sendToServer("spc~" + difficulty);
        });

        btnSkip.setOnClickListener(v -> {
            if (timeLeftSec <= 0) return;
            sendToServer("sps~" + difficulty);
        });
    }

    private void onServerMessage(String text) {
        text = text.trim();

        if (text.startsWith("wrd~")) {
            String[] p = text.split("~", 3);
            if (p.length >= 3) {
                tvWord.setText(p[2]);
            }
            return;
        }

        if (text.startsWith("err~")) {
            String[] p = text.split("~", 2);
            String reason = p.length >= 2 ? p[1] : "";
            Toast.makeText(this, ClientMessageUtils.singleplayerError(reason), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendToServer(String plainText) {
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        new Thread(new tcp_send_recv(netHandler, data)).start();
    }

    private void updateScoreUI() {
        tvScore.setText("Score: " + score);
    }

    private void updateTimerUI() {
        String t = BaseGameActivity.formatSeconds(timeLeftSec);
        tvTimer.setText(t);
    }

    private void resetAndStartGame() {
        if (timeUpDialog != null && timeUpDialog.isShowing()) {
            timeUpDialog.dismiss();
        }

        score = 0;
        timeLeftSec = roundTimeSec;
        updateScoreUI();
        updateTimerUI();

        sendToServer("spr~" + difficulty);
        sendToServer("spw~" + difficulty);

        Toast.makeText(this, ClientMessageUtils.singleplayerStartedMessage(), Toast.LENGTH_SHORT).show();

        startTimer();
    }

    private void startTimer() {
        stopTimer();
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                timeLeftSec -= 1;
                if (timeLeftSec <= 0) {
                    timeLeftSec = 0;
                    updateTimerUI();
                    onTimeUp();
                    return;
                }
                updateTimerUI();
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.postDelayed(tickRunnable, 1000);
    }

    private void stopTimer() {
        if (tickRunnable != null) {
            uiHandler.removeCallbacks(tickRunnable);
        }
        tickRunnable = null;
    }

    private void onTimeUp() {
        stopTimer();
        btnCorrect.setEnabled(false);
        btnSkip.setEnabled(false);

        String msg = String.format(Locale.US, "Score: %d", score);
        timeUpDialog = new AlertDialog.Builder(this)
                .setTitle("Time is up")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Play again", (d, w) -> {
                    btnCorrect.setEnabled(true);
                    btnSkip.setEnabled(true);
                    resetAndStartGame();
                })
                .setNegativeButton("Exit", (d, w) -> {
                    Intent i = new Intent(ActivitySingleplayer.this, HomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .create();
        timeUpDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (timeUpDialog != null && timeUpDialog.isShowing()) {
            timeUpDialog.dismiss();
        }
    }
}