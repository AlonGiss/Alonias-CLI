package com.alongiss.Alonias;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Screen for creating a single-player match.
 * The user chooses difficulty and round time before starting the game.
 */
public class CreateSingleRoom extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room_single);
        ServerDownUI.bind(this);
        spinnerUpdate();
        findViewById(R.id.btnCreateRoom).setOnClickListener(v -> create());
    }

    /**
     * Fills the difficulty and round-time dropdowns with their options.
     */
    private void spinnerUpdate() {
        Spinner spDifficulty = findViewById(R.id.spDifficulty);

        // ArrayAdapter connects a String array to the Spinner, so Android can display it as a dropdown list.
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"Easy", "Medium", "Hard"}
        );

        // Sets the layout used for the items when the Spinner dropdown is opened.
        diffAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spDifficulty.setAdapter(diffAdapter);

        Spinner spRoundTime = findViewById(R.id.spRoundTime);

        // Same idea as above, but this Spinner contains the round duration options.
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item_white,
                new String[]{"30", "60", "90", "120"}
        );
        // Controls how each option looks inside the opened dropdown list.
        timeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spRoundTime.setAdapter(timeAdapter);
    }

    /**
     * Reads the selected settings and opens the single-player game screen.
     */
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

        // Intent is used to move from this Activity to ActivitySingleplayer.
        android.content.Intent i = new android.content.Intent(this, ActivitySingleplayer.class);

        // Extras are values sent to the next Activity, so it can use the selected settings.
        i.putExtra("difficulty", difficulty);
        i.putExtra("roundTime", time);

        startActivity(i);
    }

    /**
     * Shows a short temporary message on the screen.
     */
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}