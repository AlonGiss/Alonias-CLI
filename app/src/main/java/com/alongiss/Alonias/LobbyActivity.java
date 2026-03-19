package com.alongiss.Alonias;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;

public class LobbyActivity extends AppCompatActivity {

    private String roomId;
    private String username;
    private boolean isHost;

    /**
     * FIX SINCRONIZACIÓN: guardamos el mensaje rol~ que puede llegar
     * antes de que procesemos stg~True. Una vez que confirmamos stg~True
     * lo procesamos de inmediato.
     */
    private boolean gameStartConfirmed = false;
    private String  pendingRolMessage  = null;

    private TextView tvRoom, tvStatus, tvPlayers, tvError;
    private MaterialButton btnStart, btnLeave;

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            Object o = msg.obj;
            if (o == null) return;

            String text;
            if (o instanceof byte[]) text = new String((byte[]) o, StandardCharsets.UTF_8);
            else text = String.valueOf(o);

            handleServer(text.trim());
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            send("rpl~" + roomId);
            pollHandler.postDelayed(this, 1000);
        }
    };
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        roomId          = getIntent().getStringExtra("roomId");
        username        = getIntent().getStringExtra("username");
        isHost          = getIntent().getBooleanExtra("isHost", false);
        boolean fromRematch = getIntent().getBooleanExtra("rematch", false);

        SocketHandler.setHandler(netHandler);

        tvRoom    = findViewById(R.id.tvRoom);
        tvStatus  = findViewById(R.id.tvStatus);
        tvPlayers = findViewById(R.id.tvPlayers);
        tvError   = findViewById(R.id.tvError);
        btnStart  = findViewById(R.id.btnStart);
        btnLeave  = findViewById(R.id.btnLeave);

        tvRoom.setText("ROOM: " + roomId);
        tvStatus.setText("Estado: WAITING");

        btnStart.setEnabled(false);
        btnStart.setAlpha(0.35f);

        btnStart.setOnClickListener(v -> {
            // Deshabilitar para evitar doble tap
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.35f);
            send("stg~" + roomId);
        });

        btnLeave.setOnClickListener(v -> {
            send("lvr~" + roomId);
            finish();
        });

        if (fromRematch) {
            tvStatus.setText("Estado: WAITING");
            send("rph~" + roomId);
            pollHandler.postDelayed(pollRunnable, 200);
        } else {
            btnStart.setEnabled(isHost);
            btnStart.setAlpha(isHost ? 1f : 0.35f);
            pollHandler.postDelayed(pollRunnable, 200);
        }
    }

    private void send(String plain) {
        new Thread(new tcp_send_recv(netHandler, plain.getBytes(StandardCharsets.UTF_8))).start();
    }

    private void handleServer(String text) {

        // ── jnr~ ──────────────────────────────────────────────────────
        if (text.startsWith("jnr~")) {
            String[] p = text.split("~", 4);
            if (p.length >= 2 && "True".equals(p[1])) {
                tvStatus.setText("Estado: WAITING");
                send("rph~" + roomId);
                pollHandler.postDelayed(pollRunnable, 300);
            } else {
                String reason = (p.length >= 3) ? p[2] : "ERROR";
                showError("No se pudo reconectar: " + reason);
            }
            return;
        }

        // ── hst~ ──────────────────────────────────────────────────────
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

        // ── lpl~ ──────────────────────────────────────────────────────
        if (text.startsWith("lpl~")) {
            String[] p = text.split("~", 3);
            if (p.length >= 3 && roomId.equals(p[1])) {
                Toast.makeText(this, p[2].trim() + " left the lobby.", Toast.LENGTH_SHORT).show();
                send("rpl~" + roomId);
            }
            return;
        }

        // ── rph~ ──────────────────────────────────────────────────────
        if (text.startsWith("rph~")) {
            String[] p = text.split("~", 3);
            if (p.length < 3) return;
            boolean amHost = username != null && username.equals(p[2]);
            isHost = amHost;
            btnStart.setEnabled(amHost);
            btnStart.setAlpha(amHost ? 1f : 0.35f);
            return;
        }

        // ── rpl~ (lista de jugadores) ─────────────────────────────────
        if (text.startsWith("rpl~")) {
            String[] p = text.split("~", 3);
            if (p.length < 3) return;
            if (!roomId.equals(p[1])) return;

            String csv = p[2].trim();
            if (csv.isEmpty()) {
                tvPlayers.setText("(vacío)");
            } else {
                String[] users = csv.split(",");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < users.length; i++) {
                    sb.append("• ").append(users[i].trim()).append("\n");
                }
                tvPlayers.setText(sb.toString().trim());
            }
            return;
        }

        // ── stg~ ──────────────────────────────────────────────────────
        // FIX: stg~True puede llegar como broadcast (a todos) o como
        // respuesta directa (solo al host). En ambos casos marcamos el
        // flag y procesamos el rol~ pendiente si ya lo teníamos guardado.
        if (text.startsWith("stg~")) {
            String[] p = text.split("~");
            boolean ok = (p.length >= 2) && "True".equalsIgnoreCase(p[1]);
            if (ok) {
                tvStatus.setText("Estado: STARTING…");
                pollHandler.removeCallbacks(pollRunnable);
                gameStartConfirmed = true;

                // Si ya llegó rol~ antes que stg~True, procesarlo ahora
                if (pendingRolMessage != null) {
                    String rol = pendingRolMessage;
                    pendingRolMessage = null;
                    processRolMessage(rol);
                }
            } else {
                String reason = (p.length >= 3) ? p[2] : "ERROR";
                showError("No se pudo iniciar: " + reason);
                // Re-habilitar botón si somos host
                btnStart.setEnabled(isHost);
                btnStart.setAlpha(isHost ? 1f : 0.35f);
            }
            return;
        }

        // ── rol~ ──────────────────────────────────────────────────────
        // FIX: Si todavía no confirmamos stg~True, guardamos el mensaje
        // para procesarlo en cuanto llegue stg~True. Esto evita la race
        // condition donde rol~ llega antes de que el lobby sepa que arrancó.
        if (text.startsWith("rol~")) {
            if (!gameStartConfirmed) {
                // Guardar para procesar cuando llegue stg~True
                pendingRolMessage = text;
            } else {
                processRolMessage(text);
            }
            return;
        }

        // ── upd~ ──────────────────────────────────────────────────────
        if (text.startsWith("upd~")) {
            tvStatus.setText("Estado: PLAYING");
        }
    }

    /**
     * Navega a la Activity de juego según el rol recibido.
     * Solo se llama cuando gameStartConfirmed == true.
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

        i.putExtra("roomId", roomId);
        i.putExtra("username", username);
        startActivity(i);
        finish();
    }

    private void showError(String s) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(s);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollRunnable);
    }
}
