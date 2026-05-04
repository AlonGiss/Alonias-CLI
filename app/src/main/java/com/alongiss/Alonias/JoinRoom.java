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

    /*
     * Handler that receives messages from the TCP listener.
     * It runs on the main thread, so it is safe to update the UI from here.
     */
    private final Handler netHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.obj == null) return;
            byte[] data = (byte[]) msg.obj;
            String text = new String(data, StandardCharsets.UTF_8);
            onServerMessage(text);
        }
    };

    /**
     * Initializes the room list screen and starts requesting available rooms.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_room);
        ServerDownUI.bind(this);
        rvRooms = findViewById(R.id.rvRooms);

        adapter = new MyAdapter(roomsText);

        // RecyclerView needs a LayoutManager to know how to arrange the items on the screen.
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        rvRooms.setAdapter(adapter);

        attachRecyclerClick();

        /*
         * Timer repeatedly asks the server for the updated room list.
         * The first request runs immediately, then every 700 milliseconds.
         */
        lobbytimer = new Timer();
        lobbytimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                requestRoomsList();
            }
        }, 0, 700);
    }

    /**
     * Adds click support to the RecyclerView items.
     *
     * RecyclerView does not have a simple built-in OnItemClickListener like ListView,
     * so this uses GestureDetector together with OnItemTouchListener.
     */
    private void attachRecyclerClick() {
        /*
         * GestureDetector helps detect a clean single tap,
         * instead of reacting to every small touch/move event.
         */
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
                /*
                 * Finds which RecyclerView item is under the user's finger.
                 * If a valid item was tapped, its position is sent to onRoomClicked.
                 */
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null && gestureDetector.onTouchEvent(e)) {
                    int position = rv.getChildAdapterPosition(child);
                    onRoomClicked(position);
                    return true;
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    /**
     * Handles the user clicking on a room from the list.
     */
    private void onRoomClicked(int position) {
        if (position < 0 || position >= roomsData.size()) return;

        RoomInfo room = roomsData.get(position);

        if (!"WAITING".equalsIgnoreCase(room.status)) {
            Toast.makeText(this, "This room is not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (room.count >= room.max) {
            Toast.makeText(this, "This room is full.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!room.locked) {
            joinRoom(room.code, "");
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Password");

        /*
         * AlertDialog creates a small popup window.
         * Here it is used to ask for a password before joining a locked room.
         */
        new AlertDialog.Builder(this)
                .setTitle("Locked room")
                .setMessage("Enter the password for " + room.name)
                .setView(input)
                .setPositiveButton("Join", (d, w) -> {
                    String pass = input.getText().toString();
                    joinRoom(room.code, pass);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Asks the server for the list of available rooms.
     */
    private void requestRoomsList() {
        sendToServer("lst~");
    }

    /**
     * Sends a join-room request to the server.
     */
    private void joinRoom(String roomCode, String pass) {
        sendToServer("jnr~" + roomCode + "~" + pass);
    }

    /**
     * Sends a plain text command to the server using the TCP sender.
     */
    private void sendToServer(String plainText) {
        byte[] payload = plainText.getBytes(StandardCharsets.UTF_8);
        new Thread(new tcp_send_recv(netHandler, payload)).start();
    }

    /**
     * Routes each server message to the correct handler according to its prefix.
     */
    private void onServerMessage(String text) {
        if (text.startsWith("lst~")) {
            handleRoomsList(text);
            return;
        }

        if (text.startsWith("jnr~")) {
            handleJoinReply(text);
            return;
        }

        if (text.startsWith("err~")) {
            String[] p = text.split("~", 2);
            String reason = p.length >= 2 ? p[1] : "";
            Toast.makeText(this, ClientMessageUtils.genericServerError(reason), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Parses and displays the rooms list received from the server.
     *
     * Expected format:
     * lst~True~code|name|locked|count|max|status,code|name|locked|count|max|status
     */
    private void handleRoomsList(String msg) {
        String[] p = msg.split("~", 3);
        if (p.length < 2) return;

        if (!"True".equals(p[1])) {
            Toast.makeText(this, "Could not load rooms.", Toast.LENGTH_SHORT).show();
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

                        // This is the text that the user sees for each room inside the RecyclerView.
                        String line = "  " + info.name + " (" + info.count + "/" + info.max + ")  "
                                + info.code + (info.locked ? " 🔒" : "");

                        roomsText.add(line);
                    }
                }
            }
        }

        // Tells the RecyclerView that the list changed and it should refresh the screen.
        adapter.notifyDataSetChanged();
    }

    /**
     * Converts one room item from server text into a RoomInfo object.
     *
     * Expected item format:
     * code|name|locked|count|max|status
     */
    private RoomInfo parseRoomItem(String item) {
        String[] f = item.split("\\|");
        if (f.length < 6) return null;

        try {
            String code = f[0];
            String name = f[1];

            // The server sends locked as "1" or something else, so it is converted to boolean here.
            boolean locked = "1".equals(f[2]);

            int count = Integer.parseInt(f[3]);
            int max = Integer.parseInt(f[4]);
            String status = f[5];

            return new RoomInfo(code, name, locked, count, max, status);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Handles the server response after trying to join a room.
     *
     * Expected success format:
     * jnr~True~roomId
     */
    private void handleJoinReply(String msg) {
        String[] p = msg.split("~", 4);
        if (p.length < 2) return;

        if ("True".equals(p[1])) {
            if (p.length < 3) {
                Toast.makeText(this, ClientMessageUtils.unexpectedResponseMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            String roomId = p[2];

            Toast.makeText(this, ClientMessageUtils.roomJoinSuccessMessage(), Toast.LENGTH_SHORT).show();

            // Stop refreshing the room list because the user is leaving this screen.
            lobbytimer.cancel();

            Intent i = new Intent(this, LobbyActivity.class);

            /*
             * Extras pass room data to LobbyActivity.
             * LobbyActivity uses them to know which room was joined and who the user is.
             */
            i.putExtra("roomId", roomId);
            i.putExtra("username", SocketHandler.getUsername());
            i.putExtra("isHost", false);

            startActivity(i);
            finish();
        } else {
            String reason = p.length >= 3 ? p[2] : "UNKNOWN";
            Toast.makeText(this, ClientMessageUtils.roomJoinMessage(reason), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Stops the timer when the screen is destroyed.
     *
     * This prevents the app from continuing to request rooms after leaving the screen.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (lobbytimer != null) {
            lobbytimer.cancel();
        }
    }
}