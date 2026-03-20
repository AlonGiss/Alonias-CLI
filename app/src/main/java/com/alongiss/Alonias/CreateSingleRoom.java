package com.alongiss.Alonias;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

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
        Spinner spDifficulty = findViewById(R.id.spDifficulty);
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"Easy", "Medium", "Hard"}
        );
        diffAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spDifficulty.setAdapter(diffAdapter);

        Spinner spRoundTime = findViewById(R.id.spRoundTime);
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"30", "60", "90", "120"}
        );
        timeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spRoundTime.setAdapter(timeAdapter);
    }

    private void create() {
        Spinner spTime = findViewById(R.id.spRoundTime);
        Spinner sp = findViewById(R.id.spDifficulty);

        if (spTime.getSelectedItem().toString().isEmpty()) {
            toast("Please fill in all fields.");
            return;
        }

        int time = Integer.parseInt(spTime.getSelectedItem().toString());
        String difficulty = sp.getSelectedItem().toString();

        toast("Single-player match started (" + difficulty + ").");

        android.content.Intent i = new android.content.Intent(this, ActivitySingleplayer.class);
        i.putExtra("difficulty", difficulty);
        i.putExtra("roundTime", time);
        startActivity(i);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}