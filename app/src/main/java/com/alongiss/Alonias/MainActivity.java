package com.alongiss.Alonias;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {


    private Handler socketHandler;


    // UI
    private LinearLayout loginCard;
    private EditText etUser, etPass, etConfirmPass;
    private Button btnLogin;
    private TextView tvSwitchMode;


    // STATE
    private boolean isSignupMode = false;


    // ANIMATIONS
    private Animation dropAnim, bounceAnim, shakeAnim, buttonPressAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // FIND VIEWS
        loginCard = findViewById(R.id.loginCard);
        etUser = findViewById(R.id.etUsername);
        etPass = findViewById(R.id.etPassword);
        etConfirmPass = findViewById(R.id.etConfirmPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSwitchMode = findViewById(R.id.tvSwitchMode);

        animation();
        socketHandler();

        // LOGIN / SIGNUP BUTTON
        btnLogin.setOnClickListener(v -> {
            btnLogin.startAnimation(buttonPressAnim);
            handleAction();
        });

        // SWITCH MODE
        tvSwitchMode.setOnClickListener(v -> switchMode());

    }



    // HANDLE LOGIN OR SIGNUP
    private void handleAction() {
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        String confirm = etConfirmPass.getText().toString().trim();
        SocketHandler.setUsername(etUser.getText().toString().trim());

        if (user.isEmpty() || pass.isEmpty()) {
            loginCard.startAnimation(shakeAnim);
            Toast.makeText(this, R.string.fields_cannot_be_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSignupMode) {
            if (!pass.equals(confirm)) {
                loginCard.startAnimation(shakeAnim);
                Toast.makeText(this, R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show();
                return;
            }
            sendToServer("reg", user, pass);
        } else {
            sendToServer("log", user, pass);
        }
    }

    // SEND DATA
    private void sendToServer(String type, String user, String pass) {
        byte[] data = (type + "~" + user + "~" + pass).getBytes();
        new Thread(new tcp_send_recv(socketHandler, data)).start();
    }

    // SWITCH LOGIN <-> SIGNUP
    private void switchMode() {
        isSignupMode = !isSignupMode;

        if (isSignupMode) {
            etConfirmPass.setVisibility(View.VISIBLE);
            etConfirmPass.setAlpha(0f);
            etConfirmPass.animate().alpha(1f).setDuration(250).start();

            btnLogin.setText(R.string.sign_up);
            tvSwitchMode.setText(R.string.already_have_an_account_login);
        } else {
            etConfirmPass.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> etConfirmPass.setVisibility(View.GONE))
                    .start();

            btnLogin.setText(R.string.login);
            tvSwitchMode.setText(R.string.sign_up_select);
        }
    }


    // ANIMATION SETUP
    private void animation(){

        dropAnim = AnimationUtils.loadAnimation(this, R.anim.slide_fade_down);
        bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce_soft);
        shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake_error);
        buttonPressAnim = AnimationUtils.loadAnimation(this, R.anim.button_press);

        loginCard.startAnimation(dropAnim);
        dropAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) {
                loginCard.startAnimation(bounceAnim);
            }
            @Override public void onAnimationRepeat(Animation animation) {}
        });
    }

   // SOCKET HANDLER
    private void socketHandler(){
        socketHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.obj == null) return;
                String response = (msg.obj instanceof byte[])
                        ? new String((byte[]) msg.obj, StandardCharsets.UTF_8)
                        : String.valueOf(msg.obj);
                response = response.trim();

                if (response.equalsIgnoreCase("log~True")) {
                    Toast.makeText(
                            MainActivity.this,
                            isSignupMode ? "Account created!" : "Login successful",
                            Toast.LENGTH_SHORT
                    ).show();

                    startActivity(new Intent(MainActivity.this, HomeActivity.class));
                    finish();
                } else {
                    loginCard.startAnimation(shakeAnim);
                    Toast.makeText(MainActivity.this, "Operation failed", Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

}


