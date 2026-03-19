package com.alongiss.Alonias;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

public class activity_spectator extends BaseGameActivity {

    private TextView tvRoomInfo;
    private TextView tvTurnInfo;
    private TextView tvStatus;
    private MaterialButton btnLeave;
    private MaterialButton btnMuteVoice;

    private VoiceChatManager voiceChat;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spectator);

        tvTimer  = findViewById(R.id.tvTimer);
        tvScoreA = findViewById(R.id.tvScoreTeamA);
        tvScoreB = findViewById(R.id.tvScoreTeamB);

        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        tvTurnInfo = findViewById(R.id.tvTurnInfo);
        tvStatus   = findViewById(R.id.tvStatus);
        btnLeave   = findViewById(R.id.btnLeave);
        btnMuteVoice = findViewById(R.id.btnMuteVoice);

        tvRoomInfo.setText("ROOM: " + roomId);

        // Espectador solo escucha
        voiceChat = new VoiceChatManager(this,roomId, myUsername, false);
        voiceChat.start();

        btnLeave.setOnClickListener(v -> finish());

        btnMuteVoice.setOnClickListener(v -> {
            if (voiceChat == null) return;
            boolean nowMuted = !voiceChat.isMuted();
            voiceChat.setMuted(nowMuted);
            btnMuteVoice.setText(nowMuted ? "UNMUTE VOZ" : "MUTE VOZ");
        });
    }

    // FIX: firma actualizada — timeLeft en vez de endEpoch
    @Override
    protected void onUpd(String explainer, int timeLeft, int scoreA, int scoreB) {
        tvTurnInfo.setText("TURNO: " + explainer + " (EXPLICA)");
    }

    @Override
    protected void onWord(String word) {
        // Spectators no reciben la palabra
    }

    @Override
    protected void onGuessResult(boolean correct) {
        // Spectators no adivinan
    }

    @Override
    protected void onStartResult(boolean ok, String reason) {
        showStatus(ok ? "Juego iniciado" : ("No se pudo iniciar: " + reason));
    }

    private void showStatus(String s) {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(s);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceChat != null) {
            voiceChat.stop();
            voiceChat = null;
        }
    }
}
