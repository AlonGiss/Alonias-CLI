package com.alongiss.testnetworkyossi;

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
    private MaterialButton btnEndTurn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explainer);

        tvTimer  = findViewById(R.id.tvTimer);
        tvScoreA = findViewById(R.id.tvScoreYou);
        tvScoreB = findViewById(R.id.tvScoreOther);

        tvWord    = findViewById(R.id.tvWord);
        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        tvStatus  = findViewById(R.id.tvStatus);

        btnSkip    = findViewById(R.id.btnSkip);
        btnEndTurn = findViewById(R.id.btnEndTurn);

        tvRoomInfo.setText("ROOM: " + roomId + "  •  TU TURNO");

        btnSkip.setOnClickListener(v -> {
            sendToServer("skp~" + roomId);
            showStatus("Skip enviado...", false);
        });

        btnEndTurn.setOnClickListener(v -> {
            sendToServer("end~" + roomId);
            showStatus("Fin de turno enviado...", false);
        });
    }

    // FIX: firma actualizada — timeLeft en vez de endEpoch
    @Override
    protected void onUpd(String explainer, int timeLeft, int scoreA, int scoreB) {
        boolean amIExplainer = myUsername != null && myUsername.equals(explainer);
        if (!amIExplainer) {
            // El turno pasó a otro equipo — el servidor mandará rol~SPECTATOR
            // Mientras tanto actualizamos la UI
            tvRoomInfo.setText("ROOM: " + roomId + "  •  NO ES TU TURNO");
            tvWord.setText("—");
            btnSkip.setEnabled(false);
            btnEndTurn.setEnabled(false);
            showStatus("Ahora explica: " + explainer, false);
        } else {
            tvRoomInfo.setText("ROOM: " + roomId + "  •  TU TURNO");
            btnSkip.setEnabled(true);
            btnEndTurn.setEnabled(true);
        }
    }

    @Override
    protected void onWord(String word) {
        tvWord.setText(word);
        showStatus("Nueva palabra", false);
    }

    @Override
    protected void onGuessResult(boolean correct) {
        // El guess result le llega al guesser, no al explainer
    }

    @Override
    protected void onStartResult(boolean ok, String reason) {
        showStatus(ok ? "Juego iniciado" : ("No se pudo iniciar: " + reason), !ok);
    }

    private void showStatus(String s, boolean error) {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(s);
    }
}
