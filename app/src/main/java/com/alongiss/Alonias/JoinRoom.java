package com.alongiss.Alonias;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Timer;

public class JoinRoom extends AppCompatActivity {

    private RecyclerView rvRooms;

    private final ArrayList<String> roomsText = new ArrayList<>();
    private MyAdapter adapter;
    private Timer lobbytimer;
    private final ArrayList<RoomInfo> roomsData = new ArrayList<>();

    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.obj == null) return;
            byte[] data = (byte[]) msg.obj;
            String text = new String(data, StandardCharsets.UTF_8);
            onServerMessage(text);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_room);

        rvRooms = findViewById(R.id.rvRooms);

        adapter = new MyAdapter(roomsText);
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        rvRooms.setAdapter(adapter);

        attachRecyclerClick();
        lobbytimer = new Timer();
        lobbytimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                requestRoomsList();
            }
        }, 0, 700);
    }

    private void attachRecyclerClick() {
        GestureDetector gestureDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        return true;
                    }
                }
        );

        rvRooms.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null && gestureDetector.onTouchEvent(e)) {
                    int position = rv.getChildAdapterPosition(child);
                    onRoomClicked(position);
                    return true;
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) { }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) { }
        });
    }

    private void onRoomClicked(int position) {
        if (position < 0 || position >= roomsData.size()) return;

        RoomInfo room = roomsData.get(position);

        if (!"WAITING".equalsIgnoreCase(room.status)) {
            Toast.makeText(this, "Room not available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (room.count >= room.max) {
            Toast.makeText(this, "Room is full", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!room.locked) {
            joinRoom(room.code, "");
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Password");

        new AlertDialog.Builder(this)
                .setTitle("Room locked")
                .setMessage("Enter password for " + room.name)
                .setView(input)
                .setPositiveButton("Join", (d, w) -> {
                    String pass = input.getText().toString();
                    joinRoom(room.code, pass);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestRoomsList() {
        sendToServer("lst~");
    }

    private void joinRoom(String roomCode, String pass) {
        sendToServer("jnr~" + roomCode + "~" + pass);
    }

    private void sendToServer(String plainText) {
        byte[] payload = plainText.getBytes(StandardCharsets.UTF_8);
        new Thread(new tcp_send_recv(netHandler, payload)).start();
    }

    private void onServerMessage(String text) {
        if (text.startsWith("lst~")) {
            handleRoomsList(text);
            return;
        }

        if (text.startsWith("jnr~")) {
            handleJoinReply(text);
            return;
        }

        if (text.startsWith("err~NOT_LOGGED")) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRoomsList(String msg) {
        String[] p = msg.split("~", 3);
        if (p.length < 2) return;

        if (!"True".equals(p[1])) {
            Toast.makeText(this, "Failed to load rooms", Toast.LENGTH_SHORT).show();
            return;
        }

        roomsText.clear();
        roomsData.clear();

        if (p.length == 3) {
            String payload = p[2];
            if (!payload.trim().isEmpty()) {
                String[] items = payload.split(",");
                for (String item : items) {
                    RoomInfo info = parseRoomItem(item);
                    if (info != null) {
                        roomsData.add(info);
                        String line = info.name + " (" + info.count + "/" + info.max + ")  " +
                                info.code + (info.locked ? " 🔒" : "");
                        roomsText.add(line);
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private RoomInfo parseRoomItem(String item) {
        String[] f = item.split("\\|");
        if (f.length < 6) return null;

        try {
            String code = f[0];
            String name = f[1];
            boolean locked = "1".equals(f[2]);
            int count = Integer.parseInt(f[3]);
            int max = Integer.parseInt(f[4]);
            String status = f[5];
            return new RoomInfo(code, name, locked, count, max, status);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleJoinReply(String msg) {
        String[] p = msg.split("~", 4);
        if (p.length < 2) return;

        if ("True".equals(p[1])) {
            if (p.length < 3) {
                Toast.makeText(this, "Join OK (no roomId?)", Toast.LENGTH_SHORT).show();
                return;
            }

            String roomId = p[2];

            Toast.makeText(this, "Joined! roomId=" + roomId, Toast.LENGTH_SHORT).show();
            lobbytimer.cancel();

            Intent i = new Intent(this, LobbyActivity.class);
            i.putExtra("roomId", roomId);
            i.putExtra("username", SocketHandler.getUsername());
            i.putExtra("isHost", false);
            startActivity(i);
            finish();
        } else {
            String reason = (p.length >= 3) ? p[2] : "UNKNOWN";
            Toast.makeText(this, mapJoinReason(reason), Toast.LENGTH_LONG).show();
        }
    }

    private String mapJoinReason(String reason) {
        if ("NO_ROOM".equalsIgnoreCase(reason)) {
            return "La sala ya no existe.";
        }
        if ("ROOM_NOT_WAITING".equalsIgnoreCase(reason)) {
            return "La sala ya empezó.";
        }
        if ("BAD_PASSWORD".equalsIgnoreCase(reason)) {
            return "Contraseña incorrecta.";
        }
        if ("ROOM_FULL".equalsIgnoreCase(reason)) {
            return "La sala está llena.";
        }
        if ("USERNAME_ALREADY_IN_ROOM".equalsIgnoreCase(reason)) {
            return "Ese usuario ya está dentro de esa sala.";
        }
        if ("ALREADY_IN_OTHER_ROOM".equalsIgnoreCase(reason)) {
            return "Ese usuario ya está en otra sala.";
        }
        if ("NO_USER".equalsIgnoreCase(reason)) {
            return "Usuario inválido.";
        }
        return "No se pudo entrar: " + reason;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lobbytimer != null) {
            lobbytimer.cancel();
        }
    }
}