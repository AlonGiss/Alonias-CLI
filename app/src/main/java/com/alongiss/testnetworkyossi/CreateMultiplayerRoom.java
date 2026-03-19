package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class CreateMultiplayerRoom extends AppCompatActivity {

    private Handler socketHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_multiplayer_room);
        socketHandler();
        spinnerUpdate();

        findViewById(R.id.btnCreateRoom).setOnClickListener(v -> createRoom());
    }

    private void spinnerUpdate() {

        // ===== Difficulty =====
        Spinner spDifficulty = findViewById(R.id.spDifficulty);
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"Easy", "Medium", "Hard"}
        );
        diffAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_white
        );
        spDifficulty.setAdapter(diffAdapter);


        // ===== Round Time =====
        Spinner spRoundTime = findViewById(R.id.spRoundTime);
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"30", "60", "90", "120"}
        );
        timeAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_white
        );
        spRoundTime.setAdapter(timeAdapter);


        // ===== Players =====
        Spinner spPlayers = findViewById(R.id.spPlayers);
        ArrayAdapter<String> playersAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"2","  4"}
        );
        playersAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_white
        );
        spPlayers.setAdapter(playersAdapter);
    }

    private void createRoom() {
        EditText name = findViewById(R.id.etRoomName);
        EditText pwd = findViewById(R.id.etPassword);
        Spinner time = findViewById(R.id.spRoundTime);
        Spinner players = findViewById(R.id.spPlayers);
        Spinner diff = findViewById(R.id.spDifficulty);

        if (name.getText().toString().isEmpty() ||
                pwd.getText().toString().isEmpty())
                 {

            toast("Fill all fields");
            return;
        }

        String roomName = name.getText().toString().trim();
        String password = pwd.getText().toString().trim();
        if (!validateInputs(roomName, password)) {
            return;
        }

        String msg =
                        name.getText() + "~" +
                        players.getSelectedItem() + "~" +
                        time.getSelectedItem() + "~" +
                        diff.getSelectedItem() + "~" +
                        pwd.getText();

        Toast.makeText(this,
                "Room created:\n" + name.getText(),
                Toast.LENGTH_SHORT).show();

        sendToServer("crt", msg);



    }

    private void socketHandler(){
        socketHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                String response = new String((byte[]) msg.obj).trim();
                if (response.startsWith("crt~")) {
                    // crt~True~ROOMID  o  crt~False~REASON
                    String[] p = response.split("~");
                    if (p.length < 2) return;

                    if ("True".equals(p[1]) && p.length >= 3) {
                        String roomId = p[2];

                        Intent i = new Intent(CreateMultiplayerRoom.this, LobbyActivity.class);
                        i.putExtra("roomId", roomId);
                        i.putExtra("username", SocketHandler.getUsername());
                        i.putExtra("isHost", true);
                        startActivity(i);
                        finish();
                    } else {
                        String reason = (p.length >= 3) ? p[2] : "ERROR";
                        Toast.makeText(CreateMultiplayerRoom.this, "Create failed: " + reason, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
    }

    private void sendToServer(String type, String dataB) {
        byte[] data = (type + "~" + dataB).getBytes();
        new Thread(new tcp_send_recv(socketHandler, data)).start();
    }

    private boolean validateInputs(
            String roomName,
            String password

    ) {
        // Room name
        if (roomName.length() < 3 || roomName.length() > 10) {
            toast("Room name must be 3–10 characters");
            return false;
        }

        // Password
        if (password.length() < 3 || password.length() > 10) {
            toast("Password must be 3–10 characters");
            return false;
        }


        return true;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

}
