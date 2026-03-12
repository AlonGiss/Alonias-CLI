package com.alongiss.testnetworkyossi;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LinearLayout card = findViewById(R.id.homeCard);
        Animation drop = AnimationUtils.loadAnimation(this, R.anim.slide_fade_down);
        card.startAnimation(drop);


        findViewById(R.id.btnSingle).setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, CreateSingleRoom.class);
            i.putExtra("type", "single");
            startActivity(i);

        });

        findViewById(R.id.btnMulti).setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, CreateMultiplayerRoom.class);
            startActivity(i);
            i.putExtra("type", "multi");
        });

        findViewById(R.id.btnJoin).setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, JoinRoom.class);
            startActivity(i);
        });
    }



}



