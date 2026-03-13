package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;

public class LobbyActivity extends AppCompatActivity {

    private String roomId;
    private String username;
    private boolean isHost;

    private TextView tvRoom, tvStatus, tvPlayers, tvError;
    private MaterialButton btnStart, btnLeave;

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            Object o = msg.obj;
            if (o == null) return;

            String text;
            if (o instanceof byte[]) text = new String((byte[]) o, StandardCharsets.UTF_8);
            else text = String.valueOf(o);

            handleServer(text);
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            // pide lista de players del room cada 1s
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

        // Deshabilitado hasta confirmar rol
        btnStart.setEnabled(false);
        btnStart.setAlpha(0.35f);

        btnStart.setOnClickListener(v -> send("stg~" + roomId));

        btnLeave.setOnClickListener(v -> {
            send("lvr~" + roomId);
            finish();
        });

        if (fromRematch) {
            // Salimos de la sala en game_over; volvemos a entrar con jnr~
            tvStatus.setText("Reconectando a la sala...");
            send("jnr~" + roomId + "~");
        } else {
            // Flujo normal: habilitar boton si host y arrancar polling
            btnStart.setEnabled(isHost);
            btnStart.setAlpha(isHost ? 1f : 0.35f);
            pollHandler.postDelayed(pollRunnable, 200);
        }
    }

    private void send(String plain) {
        new Thread(new tcp_send_recv(netHandler, plain.getBytes(StandardCharsets.UTF_8))).start();
    }

    private void handleServer(String text) {

        // jnr~ respuesta al re-join desde rematch
        // jnr~True~roomId~players  o  jnr~False~REASON
        if (text.startsWith("jnr~")) {
            String[] p = text.split("~", 4);
            if (p.length >= 2 && "True".equals(p[1])) {
                tvStatus.setText("Estado: WAITING");
                // Consultar al servidor si somos el host de esta sala
                send("rph~" + roomId);
                pollHandler.postDelayed(pollRunnable, 300);
            } else {
                String reason = (p.length >= 3) ? p[2] : "ERROR";
                showError("No se pudo reconectar: " + reason);
            }
            return;
        }

        // rph~ respuesta: rph~roomId~hostUsername — para saber si somos host
        if (text.startsWith("rph~")) {
            String[] p = text.split("~", 3);
            if (p.length < 3) return;
            boolean amHost = username != null && username.equals(p[2]);
            isHost = amHost;
            btnStart.setEnabled(amHost);
            btnStart.setAlpha(amHost ? 1f : 0.35f);
            return;
        }

        // players list:
        // rpl~roomId~user1,user2,user3
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

        // start result
        if (text.startsWith("stg~")) {
            // stg~True OR stg~False~reason
            String[] p = text.split("~");
            boolean ok = (p.length >= 2) && "True".equalsIgnoreCase(p[1]);
            if (!ok) {
                String reason = (p.length >= 3) ? p[2] : "ERROR";
                showError("No se pudo iniciar: " + reason);
            }
            return;
        }

        // role assignment: rol~roomId~ROLE  (llega async)
        if (text.startsWith("rol~")) {
            String[] p = text.split("~");
            if (p.length < 3) return;
            if (!roomId.equals(p[1])) return;

            String role = p[2];

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

        // update state: upd~roomId~turnTeam~explainer~endEpoch~score0~score1
        if (text.startsWith("upd~")) {
            tvStatus.setText("Estado: PLAYING");
        }
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