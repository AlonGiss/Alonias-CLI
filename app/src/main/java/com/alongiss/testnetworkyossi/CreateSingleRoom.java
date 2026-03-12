package com.alongiss.testnetworkyossi;

import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class CreateSingleRoom extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room_single);
        spinnerUpdate();
        findViewById(R.id.btnCreateRoom).setOnClickListener(v -> create());
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
                new String[]{"4", "6", "8"}
        );
        playersAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_white
        );
        spPlayers.setAdapter(playersAdapter);
    }


    private void create() {
        Spinner spTime = findViewById(R.id.spRoundTime);
        Spinner spPlayers = findViewById(R.id.spPlayers);
        Spinner sp = findViewById(R.id.spDifficulty);

        if (spTime.getSelectedItem().toString().isEmpty() ||
                spPlayers.getSelectedItem().toString().isEmpty()) {

            toast("Fill all fields");
            return;
        }

        int time = Integer.parseInt(spTime.getSelectedItem().toString());
        int players = Integer.parseInt(spPlayers.getSelectedItem().toString());
        String difficulty = sp.getSelectedItem().toString();

        toast("Single game started (" + difficulty + ")");
        // start GameActivity
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

}
