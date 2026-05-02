package com.alongiss.Alonias;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;

public class LobbyActivity extends AppCompatActivity {

    private String roomId;
    private String username;
    private boolean isHost;

    /*
     * Used to make sure the game screen opens only after the server confirms
     * that the game really started.
     */
    private boolean gameStartConfirmed = false;

    /*
     * Sometimes the role message can arrive before the start confirmation.
     * In that case, it is saved here and handled later.
     */
    private String pendingRolMessage = null;

    private TextView tvRoom, tvStatus, tvPlayers, tvError;
    private MaterialButton btnStart, btnLeave;

    /*
     * Handler that receives TCP messages on the main thread.
     * This is needed because UI elements can only be updated from the main thread.
     */
    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Object o = msg.obj;
            if (o == null) return;

            String text;
            if (o instanceof byte[]) text = new String((byte[]) o, StandardCharsets.UTF_8);
            else text = String.valueOf(o);

            handleServer(text.trim());
        }
    };

    /*
     * Runnable used for polling the server.
     * It repeatedly asks for the updated players list in the lobby.
     */
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            send("rpl~" + roomId);
            pollHandler.postDelayed(this, 1000);
        }
    };

    // Handler used to schedule repeated lobby updates on the main thread.
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

    /**
     * Initializes the lobby screen, reads room data from the Intent,
     * connects buttons, and starts polling the server for lobby updates.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);
        ServerDownUI.bind(this);
        /*
         * These values were sent from the previous Activity with Intent extras.
         * They tell this screen which room the user joined and whether he is the host.
         */
        roomId = getIntent().getStringExtra("roomId");
        username = getIntent().getStringExtra("username");
        isHost = getIntent().getBooleanExtra("isHost", false);
        boolean fromRematch = getIntent().getBooleanExtra("rematch", false);

        SocketHandler.setHandler(netHandler);

        tvRoom = findViewById(R.id.tvRoom);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayers = findViewById(R.id.tvPlayers);
        tvError = findViewById(R.id.tvError);
        btnStart = findViewById(R.id.btnStart);
        btnLeave = findViewById(R.id.btnLeave);

        tvRoom.setText("ROOM: " + roomId);
        tvStatus.setText("Status: WAITING");

        btnStart.setEnabled(false);
        btnStart.setAlpha(0.35f);

        btnStart.setOnClickListener(v -> {
            hideError();
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.35f);
            send("stg~" + roomId);
        });

        btnLeave.setOnClickListener(v -> showLeaveRoomDialog());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveRoomDialog();
            }
        });

        if (fromRematch) {
            tvStatus.setText("Status: WAITING FOR PLAYERS...");

            // Tell the server that this player returned from game_over
            // and is ready for rematch.
            send("rdy~" + roomId);

            // After a rematch, ask the server again who is the current host.
            send("rph~" + roomId);

            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.postDelayed(pollRunnable, 200);
        } else {
            btnStart.setEnabled(isHost);
            btnStart.setAlpha(isHost ? 1f : 0.35f);

            pollHandler.postDelayed(pollRunnable, 200);
        }
    }

    private void showLeaveRoomDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Leave room?")
                .setMessage("If you leave, you will be removed from the lobby.")
                .setPositiveButton("Leave", (dialog, which) -> leaveRoomAndGoHome())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void leaveRoomAndGoHome() {
        pollHandler.removeCallbacks(pollRunnable);

        if (roomId != null && !roomId.isEmpty()) {
            send("lvr~" + roomId);
        }

        Intent intent = new Intent(LobbyActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
        finish();
    }

    /**
     * Sends a plain text command to the server through the encrypted TCP system.
     */
    private void send(String plain) {
        new Thread(new tcp_send_recv(netHandler, plain.getBytes(StandardCharsets.UTF_8))).start();
    }

    /**
     * Handles all server messages that are relevant to the lobby.
     *
     * Every message is identified by its prefix, for example:
     * rpl = room players list
     * hst = host update
     * stg = start game response
     * rol = assigned role for the game
     */
    private void handleServer(String text) {
        if (text.startsWith("rdy~")) {
            String[] p = text.split("~");

            if (p.length >= 4 && "True".equalsIgnoreCase(p[1])) {
                tvStatus.setText("Status: WAITING FOR PLAYERS... (" + p[2] + "/" + p[3] + ")");
            } else if (p.length >= 2 && "True".equalsIgnoreCase(p[1])) {
                tvStatus.setText("Status: WAITING");
            }

            return;
        }

        if (text.startsWith("jnr~")) {
            String[] p = text.split("~", 4);

            if (p.length >= 2 && "True".equals(p[1])) {
                tvStatus.setText("Status: WAITING");

                // Refresh host and players after successfully joining the room.
                send("rph~" + roomId);
                pollHandler.postDelayed(pollRunnable, 300);
            } else {
                String reason = p.length >= 3 ? p[2] : "ERROR";
                showError(ClientMessageUtils.roomJoinMessage(reason));
            }
            return;
        }

        if (text.startsWith("hst~")) {
            String[] p = text.split("~", 3);

            if (p.length >= 3 && roomId.equals(p[1])) {
                boolean amHost = username != null && username.equals(p[2].trim());

                isHost = amHost;
                btnStart.setEnabled(amHost);
                btnStart.setAlpha(amHost ? 1f : 0.35f);

                if (amHost) {
                    Toast.makeText(this, "You are now the host.", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

        if (text.startsWith("lpl~")) {
            String[] p = text.split("~", 3);

            if (p.length >= 3 && roomId.equals(p[1])) {
                Toast.makeText(this, ClientMessageUtils.playerLeftLobbyMessage(p[2].trim()), Toast.LENGTH_SHORT).show();

                // Request a fresh player list after someone leaves the lobby.
                send("rpl~" + roomId);
            }
            return;
        }

        if (text.startsWith("rph~")) {
            String[] p = text.split("~", 3);
            if (p.length < 3) return;

            /*
             * rph returns the current room host.
             * The start button is enabled only if this client is the host.
             */
            boolean amHost = username != null && username.equals(p[2]);

            isHost = amHost;
            btnStart.setEnabled(amHost);
            btnStart.setAlpha(amHost ? 1f : 0.35f);
            return;
        }

        if (text.startsWith("rpl~")) {
            String[] p = text.split("~", 3);
            if (p.length < 3) return;
            if (!roomId.equals(p[1])) return;

            String csv = p[2].trim();

            if (csv.isEmpty()) {
                tvPlayers.setText("(empty)");
            } else {
                String[] users = csv.split(",");
                StringBuilder sb = new StringBuilder();

                // Build a clean multi-line players list for the TextView.
                for (String user : users) {
                    sb.append("• ").append(user.trim()).append("\n");
                }

                tvPlayers.setText(sb.toString().trim());
            }
            return;
        }

        if (text.startsWith("stg~")) {
            String[] p = text.split("~");
            boolean ok = p.length >= 2 && "True".equalsIgnoreCase(p[1]);

            if (ok) {
                tvStatus.setText("Status: STARTING...");
                pollHandler.removeCallbacks(pollRunnable);

                gameStartConfirmed = true;

                /*
                 * If the role message arrived before the start confirmation,
                 * process it now instead of losing it.
                 */
                if (pendingRolMessage != null) {
                    String rol = pendingRolMessage;
                    pendingRolMessage = null;
                    processRolMessage(rol);
                }
            } else {
                String reason = p.length >= 3 ? p[2] : "ERROR";

                if ("WAITING_REMATCH_PLAYERS".equals(reason)) {
                    String ready = p.length >= 4 ? p[3] : "?";
                    String total = p.length >= 5 ? p[4] : "?";
                    showError("Waiting for all players to return from game over (" + ready + "/" + total + ").");
                } else {
                    showError(ClientMessageUtils.lobbyStartMessage(reason));
                }

                btnStart.setEnabled(isHost);
                btnStart.setAlpha(isHost ? 1f : 0.35f);
            }
            return;
        }

        if (text.startsWith("rol~")) {
            /*
             * The server sends the player's role before opening the game screen.
             * If the game start was not confirmed yet, save the role message for later.
             */
            if (!gameStartConfirmed) {
                pendingRolMessage = text;
            } else {
                processRolMessage(text);
            }
            return;
        }

        if (text.startsWith("upd~")) {
            tvStatus.setText("Status: PLAYING");
            return;
        }

        if (text.startsWith("err~")) {
            String[] p = text.split("~", 2);
            String reason = p.length >= 2 ? p[1] : "";
            showError(ClientMessageUtils.genericServerError(reason));
        }
    }

    /**
     * Opens the correct game Activity according to the role received from the server.
     *
     * Expected format:
     * rol~roomId~role
     */
    private void processRolMessage(String text) {
        String[] p = text.split("~");
        if (p.length < 3) return;
        if (!roomId.equals(p[1])) return;

        String role = p[2].trim();

        Intent i;

        if ("EXPLAINER".equals(role)) {
            i = new Intent(this, activity_explainer.class);
        } else if ("GUESSER".equals(role)) {
            i = new Intent(this, activity_guesser.class);
        } else {
            i = new Intent(this, activity_spectator.class);
        }

        /*
         * Send room information to the game Activity.
         * The next screen needs this to communicate with the same room.
         */
        i.putExtra("roomId", roomId);
        i.putExtra("username", username);

        startActivity(i);
        finish();
    }

    /**
     * Shows an error message on the lobby screen.
     */
    private void showError(String s) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(s);
    }

    /**
     * Hides the current error message.
     */
    private void hideError() {
        tvError.setVisibility(View.GONE);
        tvError.setText("");
    }

    /**
     * Stops lobby polling when leaving this screen.
     * This prevents unnecessary server requests after the Activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollRunnable);
    }
}