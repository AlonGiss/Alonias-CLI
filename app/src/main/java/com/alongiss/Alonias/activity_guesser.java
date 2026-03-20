package com.alongiss.Alonias;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

public class activity_guesser extends BaseGameActivity {

    private TextView tvRoomInfo;
    private TextView tvExplainer;
    private EditText etGuess;
    private MaterialButton btnSendGuess;
    private TextView tvResult;
    private MaterialButton btnMuteVoice;

    private VoiceChatManager voiceChat;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guesser);

        tvTimer = findViewById(R.id.tvTimer);
        tvScoreA = findViewById(R.id.tvScoreYou);
        tvScoreB = findViewById(R.id.tvScoreOther);

        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        etGuess = findViewById(R.id.etGuess);
        btnSendGuess = findViewById(R.id.btnSendGuess);
        tvResult = findViewById(R.id.tvResult);
        btnMuteVoice = findViewById(R.id.btnMuteVoice);

        tvRoomInfo.setText("ROOM: " + roomId);

        voiceChat = new VoiceChatManager(this, roomId, myUsername, true);
        voiceChat.start();

        btnSendGuess.setOnClickListener(v -> {
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) return;
            sendToServer("gss~" + roomId + "~" + guess);
        });

        btnMuteVoice.setOnClickListener(v -> {
            if (voiceChat == null) return;
            boolean nowMuted = !voiceChat.isMuted();
            voiceChat.setMuted(nowMuted);
            btnMuteVoice.setText(nowMuted ? "UNMUTE VOICE" : "MUTE VOICE");
            showResult(ClientMessageUtils.voiceChatMutedMessage(nowMuted), false);
        });
    }

    @Override
    protected void onUpd(String explainer, int timeLeft, int scoreA, int scoreB) {

        boolean amIExplainer = myUsername != null && myUsername.equals(explainer);
        btnSendGuess.setEnabled(!amIExplainer);
        if (amIExplainer) {
            showResult("You are the explainer now.", true);
        }
    }

    @Override
    protected void onWord(String word) {
    }

    @Override
    protected void onGuessResult(boolean correct) {
        if (correct) {
            showResult(ClientMessageUtils.guessMessage(true), false);
            etGuess.setText("");
        } else {
            showResult(ClientMessageUtils.guessMessage(false), true);
        }
    }

    @Override
    protected void onStartResult(boolean ok, String reason) {
        showResult(ok ? ClientMessageUtils.lobbyStartSuccessMessage() : ClientMessageUtils.lobbyStartMessage(reason), !ok);
    }

    private void showResult(String s, boolean error) {
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText(s);
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