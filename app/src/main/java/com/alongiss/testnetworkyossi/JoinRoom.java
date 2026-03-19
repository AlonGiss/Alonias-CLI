package com.alongiss.testnetworkyossi;

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

    // TU ADAPTER QUEDA IGUAL: ArrayList<String>
    private final ArrayList<String> roomsText = new ArrayList<>();
    private MyAdapter adapter;
    private Timer lobbytimer;
    // Data real de cada room (para join)
    private final ArrayList<RoomInfo> roomsData = new ArrayList<>();

    // Handler que recibe los bytes desencriptados desde tcp_send_recv.Listener
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

        // checks opcionales
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

        // pedir password
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
        // Protocolo: lst~
        sendToServer("lst~");
    }

    private void joinRoom(String roomCode, String pass) {
        // Protocolo: jnr~roomCode~passwordOrEmpty
        sendToServer("jnr~" + roomCode + "~" + pass);
    }

    private void sendToServer(String plainText) {
        byte[] payload = plainText.getBytes(StandardCharsets.UTF_8);
        new Thread(new tcp_send_recv(netHandler, payload)).start();
    }

    private void onServerMessage(String text) {
        // lst~True~CODE|name|locked|count|max|status,...
        // jnr~True~roomId~players...
        // jnr~False~REASON

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
            return;
        }
    }

    private void handleRoomsList(String msg) {
        // lst~True~payload
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

                        // Texto para el Recycler (tu adapter solo muestra un string)
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
        // roomCode|name|locked|count|max|status
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
        // jnr~True~roomId~players...
        // jnr~False~REASON
        String[] p = msg.split("~", 4);
        if (p.length < 2) return;

        if ("True".equals(p[1])) {
            if (p.length < 3) {
                Toast.makeText(this, "Join OK (no roomId?)", Toast.LENGTH_SHORT).show();
                return;
            }

            String roomId = p[2];
            String players = (p.length >= 4) ? p[3] : "";

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
            Toast.makeText(this, "Join failed: " + reason, Toast.LENGTH_SHORT).show();
        }
    }
}