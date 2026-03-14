package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class game_over extends AppCompatActivity {

    private String roomId;
    private String myUsername;

    // Hacia dónde navegar después de recibir lvr~True del servidor
    private static final int DEST_NONE    = 0;
    private static final int DEST_HOME    = 1;
    private static final int DEST_LOBBY   = 2;
    private int pendingDest = DEST_NONE;

    private MaterialButton btnRematch;
    private MaterialButton btnLeave;

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.obj == null) return;
            String text = new String((byte[]) msg.obj, StandardCharsets.UTF_8).trim();

            // Respuesta del servidor al abandonar la sala
            if (text.startsWith("lvr~")) {
                // lvr~True o lvr~False — en cualquier caso navegamos
                navigateToDest();
                return;
            }
            // lpl~roomId~username — another player left (server pushes so remaining players see it)
            if (text.startsWith("lpl~")) {
                String[] p = text.split("~", 3);
                if (p.length >= 3 && roomId != null && roomId.equals(p[1])) {
                    String who = p[2].trim();
                    Toast.makeText(game_over.this, who + " left the room.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        SocketHandler.setHandler(netHandler);

        Intent intent   = getIntent();
        roomId          = intent.getStringExtra("roomId");
        myUsername      = intent.getStringExtra("username");
        int    winner   = intent.getIntExtra("winner",  -1);
        int    scoreA   = intent.getIntExtra("scoreA",   0);
        int    scoreB   = intent.getIntExtra("scoreB",   0);
        String team0raw = intent.getStringExtra("team0players");
        String team1raw = intent.getStringExtra("team1players");
        String reason   = intent.getStringExtra("reason");

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
        btnRematch = findViewById(R.id.btnRematch);
        btnLeave   = findViewById(R.id.btnLeave);

        // ── Determinar equipo del jugador ─────────────────────────────
        boolean iAmInTeam0 = isInTeam(myUsername, team0);
        int myTeam = iAmInTeam0 ? 0 : 1;

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
        // Stay in room and go to Lobby (do NOT send lvr — we remain in the room)
        btnRematch.setOnClickListener(v -> {
            btnRematch.setEnabled(false);
            btnLeave.setEnabled(false);
            pendingDest = DEST_LOBBY;
            navigateToDest();  // Go to lobby immediately, no need to leave
        });

        // ── Botón: Salir ──────────────────────────────────────────────
        // Avisa al servidor que el jugador abandona la sala, luego va al inicio
        btnLeave.setOnClickListener(v -> {
            btnRematch.setEnabled(false);
            btnLeave.setEnabled(false);
            pendingDest = DEST_HOME;
            sendLeaveRoom();
        });
    }

    // ── Envía lvr~roomId al servidor ─────────────────────────────────
    private void sendLeaveRoom() {
        String msg = "lvr~" + roomId;
        new Thread(new tcp_send_recv(netHandler, msg.getBytes(StandardCharsets.UTF_8))).start();

        // Safety timeout: si en 3s no llega respuesta, navegamos igual
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (pendingDest != DEST_NONE) navigateToDest();
        }, 3000);
    }

    // ── Navega según pendingDest ──────────────────────────────────────
    private void navigateToDest() {
        if (pendingDest == DEST_NONE) return;
        int dest = pendingDest;
        pendingDest = DEST_NONE;   // evitar doble ejecución

        if (dest == DEST_LOBBY) {
            // Vuelve al lobby de la misma sala para esperar un nuevo inicio (still in room)
            Intent i = new Intent(this, LobbyActivity.class);
            i.putExtra("roomId",   roomId);
            i.putExtra("username", myUsername);
            i.putExtra("isHost",   false);   // lobby will ask rph~ to get actual host
            i.putExtra("rematch",  true);    // we stayed in room, don't re-join
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            // Sale al inicio de la app
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────
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
