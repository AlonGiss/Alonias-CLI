package com.alongiss.Alonias;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

public class activity_explainer extends BaseGameActivity {

    private TextView tvWord;
    private TextView tvRoomInfo;
    private TextView tvStatus;
    private MaterialButton btnSkip;
    private MaterialButton btnMuteVoice;

    private VoiceChatManager voiceChat;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explainer);

        tvTimer = findViewById(R.id.tvTimer);
        tvScoreA = findViewById(R.id.tvScoreYou);
        tvScoreB = findViewById(R.id.tvScoreOther);

        tvWord = findViewById(R.id.tvWord);
        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        tvStatus = findViewById(R.id.tvStatus);

        btnSkip = findViewById(R.id.btnSkip);
        btnMuteVoice = findViewById(R.id.btnMuteVoice);

        tvRoomInfo.setText("ROOM: " + roomId + " • YOUR TURN");

        voiceChat = new VoiceChatManager(this, roomId, myUsername, true);
        voiceChat.start();

        btnSkip.setOnClickListener(v -> {
            sendToServer("skp~" + roomId);
            showStatus("Skip requested...", false);
        });

        btnMuteVoice.setOnClickListener(v -> {
            if (voiceChat == null) return;
            boolean nowMuted = !voiceChat.isMuted();
            voiceChat.setMuted(nowMuted);
            btnMuteVoice.setText(nowMuted ? "UNMUTE VOICE" : "MUTE VOICE");
            showStatus(ClientMessageUtils.voiceChatMutedMessage(nowMuted), false);
        });
    }

    @Override
    protected void onUpd(String explainer, int timeLeft, int scoreA, int scoreB) {
        boolean amIExplainer = myUsername != null && myUsername.equals(explainer);
        if (!amIExplainer) {
            tvRoomInfo.setText("ROOM: " + roomId + " • NOT YOUR TURN");
            tvWord.setText("—");
            btnSkip.setEnabled(false);
            showStatus("Now explaining: " + explainer, false);
        } else {
            tvRoomInfo.setText("ROOM: " + roomId + " • YOUR TURN");
            btnSkip.setEnabled(true);
        }
    }

    @Override
    protected void onWord(String word) {
        tvWord.setText(word);
        showStatus("New word received.", false);
    }

    @Override
    protected void onGuessResult(boolean correct) {
    }

    @Override
    protected void onStartResult(boolean ok, String reason) {
        showStatus(ok ? ClientMessageUtils.lobbyStartSuccessMessage() : ClientMessageUtils.lobbyStartMessage(reason), !ok);
    }

    private void showStatus(String s, boolean error) {
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