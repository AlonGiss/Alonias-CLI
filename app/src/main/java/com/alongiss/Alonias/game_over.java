package com.alongiss.Alonias;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class game_over extends AppCompatActivity {

    private String roomId;
    private String myUsername;

    private static final int DEST_NONE = 0;
    private static final int DEST_HOME = 1;
    private static final int DEST_LOBBY = 2;
    private int pendingDest = DEST_NONE;

    private MaterialButton btnRematch;
    private MaterialButton btnLeave;

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.obj == null) return;
            String text = new String((byte[]) msg.obj, StandardCharsets.UTF_8).trim();

            if (text.startsWith("lvr~")) {
                navigateToDest();
                return;
            }

            if (text.startsWith("lpl~")) {
                String[] p = text.split("~", 3);
                if (p.length >= 3 && roomId != null && roomId.equals(p[1])) {
                    String who = p[2].trim();
                    Toast.makeText(game_over.this, ClientMessageUtils.playerLeftMessage(who), Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        SocketHandler.setHandler(netHandler);

        Intent intent = getIntent();
        roomId = intent.getStringExtra("roomId");
        myUsername = intent.getStringExtra("username");
        int winner = intent.getIntExtra("winner", -1);
        int scoreA = intent.getIntExtra("scoreA", 0);
        int scoreB = intent.getIntExtra("scoreB", 0);
        String team0raw = intent.getStringExtra("team0players");
        String team1raw = intent.getStringExtra("team1players");
        String reason = intent.getStringExtra("reason");

        String[] team0 = (team0raw != null && !team0raw.isEmpty()) ? team0raw.split(";") : new String[0];
        String[] team1 = (team1raw != null && !team1raw.isEmpty()) ? team1raw.split(";") : new String[0];

        TextView tvTitle = findViewById(R.id.tvGameOverTitle);
        TextView tvResult = findViewById(R.id.tvResult);
        TextView tvScoreA = findViewById(R.id.tvScoreA);
        TextView tvScoreB = findViewById(R.id.tvScoreB);
        TextView tvTeam0Players = findViewById(R.id.tvTeam0Players);
        TextView tvTeam1Players = findViewById(R.id.tvTeam1Players);
        TextView tvReason = findViewById(R.id.tvReason);
        btnRematch = findViewById(R.id.btnRematch);
        btnLeave = findViewById(R.id.btnLeave);

        boolean iAmInTeam0 = isInTeam(myUsername, team0);
        int myTeam = iAmInTeam0 ? 0 : 1;

        if (winner == -1) {
            tvTitle.setText("DRAW");
            tvResult.setText("No team won this round.");
        } else if (winner == myTeam) {
            tvTitle.setText("YOU WON");
            tvResult.setText("Your team won the match.");
        } else {
            tvTitle.setText("YOU LOST");
            tvResult.setText("The other team won the match.");
        }

        tvScoreA.setText(String.format(Locale.US, "Team A: %d points", scoreA));
        tvScoreB.setText(String.format(Locale.US, "Team B: %d points", scoreB));

        tvTeam0Players.setText("Team A: " + formatPlayers(team0, myUsername));
        tvTeam1Players.setText("Team B: " + formatPlayers(team1, myUsername));

        if ("PLAYER_LEFT".equals(reason)) {
            tvReason.setVisibility(android.view.View.VISIBLE);
            tvReason.setText("A player left the match.");
        } else {
            tvReason.setVisibility(android.view.View.GONE);
        }

        btnRematch.setOnClickListener(v -> {
            btnRematch.setEnabled(false);
            btnLeave.setEnabled(false);
            pendingDest = DEST_LOBBY;
            navigateToDest();
        });

        btnLeave.setOnClickListener(v -> {
            btnRematch.setEnabled(false);
            btnLeave.setEnabled(false);
            pendingDest = DEST_HOME;
            sendLeaveRoom();
        });
    }

    private void sendLeaveRoom() {
        String msg = "lvr~" + roomId;
        new Thread(new tcp_send_recv(netHandler, msg.getBytes(StandardCharsets.UTF_8))).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (pendingDest != DEST_NONE) navigateToDest();
        }, 3000);
    }

    private void navigateToDest() {
        if (pendingDest == DEST_NONE) return;
        int dest = pendingDest;
        pendingDest = DEST_NONE;

        if (dest == DEST_LOBBY) {
            Intent i = new Intent(this, LobbyActivity.class);
            i.putExtra("roomId", roomId);
            i.putExtra("username", myUsername);
            i.putExtra("isHost", false);
            i.putExtra("rematch", true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        finish();
    }

    private String formatPlayers(String[] players, String me) {
        if (players == null || players.length == 0) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(players[i]);
            if (players[i].equals(me)) sb.append(" (you)");
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