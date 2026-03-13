package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class game_over extends AppCompatActivity {

    private String roomId;
    private String myUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        Intent intent    = getIntent();
        roomId           = intent.getStringExtra("roomId");
        myUsername       = intent.getStringExtra("username");
        int    winner    = intent.getIntExtra("winner",  -1);
        int    scoreA    = intent.getIntExtra("scoreA",   0);
        int    scoreB    = intent.getIntExtra("scoreB",   0);
        String team0raw  = intent.getStringExtra("team0players");
        String team1raw  = intent.getStringExtra("team1players");
        String reason    = intent.getStringExtra("reason");

        String[] team0 = (team0raw != null && !team0raw.isEmpty()) ? team0raw.split(";") : new String[0];
        String[] team1 = (team1raw != null && !team1raw.isEmpty()) ? team1raw.split(";") : new String[0];

        // ── Vistas ────────────────────────────────────────────────────
        TextView tvTitle        = findViewById(R.id.tvGameOverTitle);
        TextView tvResult       = findViewById(R.id.tvResult);
        TextView tvScoreA       = findViewById(R.id.tvScoreA);
        TextView tvScoreB       = findViewById(R.id.tvScoreB);
        TextView tvTeam0Players = findViewById(R.id.tvTeam0Players);
        TextView tvTeam1Players = findViewById(R.id.tvTeam1Players);
        TextView tvReason       = findViewById(R.id.tvReason);
        MaterialButton btnRematch = findViewById(R.id.btnRematch);
        MaterialButton btnLeave   = findViewById(R.id.btnLeave);

        // ── Determinar si yo soy del equipo ganador ───────────────────
        boolean iAmInTeam0 = isInTeam(myUsername, team0);
        int myTeam          = iAmInTeam0 ? 0 : 1;

        // ── Título / resultado ────────────────────────────────────────
        if (winner == -1) {
            tvTitle.setText("¡EMPATE!");
            tvResult.setText("Ningún equipo ganó esta vez.");
        } else if (winner == myTeam) {
            tvTitle.setText("¡GANASTE!");
            tvResult.setText("Tu equipo se llevó la victoria 🏆");
        } else {
            tvTitle.setText("PERDISTE");
            tvResult.setText("El equipo rival ganó esta vez.");
        }

        // ── Puntajes ─────────────────────────────────────────────────
        tvScoreA.setText(String.format(Locale.US, "Equipo A: %d puntos", scoreA));
        tvScoreB.setText(String.format(Locale.US, "Equipo B: %d puntos", scoreB));

        // ── Jugadores por equipo ──────────────────────────────────────
        tvTeam0Players.setText("Equipo A: " + formatPlayers(team0, myUsername));
        tvTeam1Players.setText("Equipo B: " + formatPlayers(team1, myUsername));

        // ── Razón del fin ─────────────────────────────────────────────
        if ("PLAYER_LEFT".equals(reason)) {
            tvReason.setVisibility(View.VISIBLE);
            tvReason.setText("⚠ Un jugador abandonó la partida.");
        } else {
            tvReason.setVisibility(View.GONE);
        }

        // ── Botón: Jugar de nuevo ─────────────────────────────────────
        btnRematch.setOnClickListener(v -> {
            // Le pedimos al servidor que reinicie el juego en la misma sala
            // Usamos el mismo mecanismo que start: stg~roomId
            // La Activity vuelve al lobby y espera el stg~True
            Intent lobbyIntent = new Intent(this, LobbyActivity.class);
            lobbyIntent.putExtra("roomId",   roomId);
            lobbyIntent.putExtra("username", myUsername);
            lobbyIntent.putExtra("rematch",  true);   // flag para que el lobby muestre "esperando rematch"
            lobbyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(lobbyIntent);
            finish();
        });

        // ── Botón: Salir ──────────────────────────────────────────────
        btnLeave.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
            finish();
        });
    }

    /** Formatea la lista de jugadores, marcando al usuario actual con "(tú)" */
    private String formatPlayers(String[] players, String me) {
        if (players == null || players.length == 0) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(players[i]);
            if (players[i].equals(me)) sb.append(" (tú)");
        }
        return sb.toString();
    }

    private boolean isInTeam(String username, String[] team) {
        if (username == null || team == null) return false;
        for (String p : team) {
            if (username.equals(p)) return true;
        }
        return false;
    }


}