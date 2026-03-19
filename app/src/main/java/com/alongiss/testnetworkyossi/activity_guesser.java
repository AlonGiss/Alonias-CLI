package com.alongiss.testnetworkyossi;

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

        tvTimer  = findViewById(R.id.tvTimer);
        tvScoreA = findViewById(R.id.tvScoreYou);
        tvScoreB = findViewById(R.id.tvScoreOther);

        tvRoomInfo  = findViewById(R.id.tvRoomInfo);
        tvExplainer = findViewById(R.id.tvExplainer);
        etGuess     = findViewById(R.id.etGuess);
        btnSendGuess = findViewById(R.id.btnSendGuess);
        tvResult    = findViewById(R.id.tvResult);
        btnMuteVoice = findViewById(R.id.btnMuteVoice);

        tvRoomInfo.setText("ROOM: " + roomId);

        // Guessers pueden hablar
        voiceChat = new VoiceChatManager(this,roomId, myUsername, true);
        voiceChat.start();

        btnSendGuess.setOnClickListener(v -> {
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) return;
            // gss~roomId~guessText
            sendToServer("gss~" + roomId + "~" + guess);
        });

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
        tvExplainer.setText("EXPLICA: " + explainer);

        boolean amIExplainer = myUsername != null && myUsername.equals(explainer);
        btnSendGuess.setEnabled(!amIExplainer);
        if (amIExplainer) {
            showResult("Ahora sos explicador.", true);
        }
    }

    @Override
    protected void onWord(String word) {
        // Guessers no reciben la palabra
    }

    @Override
    protected void onGuessResult(boolean correct) {
        if (correct) {
            showResult("✅ CORRECTO!", false);
            etGuess.setText("");
        } else {
            showResult("❌ NO", true);
        }
    }

    @Override
    protected void onStartResult(boolean ok, String reason) {
        showResult(ok ? "Juego iniciado" : ("No se pudo iniciar: " + reason), !ok);
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
