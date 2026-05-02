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

    private LinearLayout loginCard;

    private EditText etUser, etPass, etConfirmPass;

    private Button btnLogin;

    private TextView tvSwitchMode;

    private TextView tvError;

    private boolean isSignupMode = false;

    private Animation dropAnim, bounceAnim, shakeAnim, buttonPressAnim;

    /*
     * When a user reconnects during a paused match, the server sends:
     * log~True
     * then rol~roomId~ROLE
     *
     * Normal login should go to HomeActivity.
     * Reconnect login should go directly back to the game Activity.
     *
     * So after log~True we wait shortly.
     * If rol~ arrives, we open the game.
     * If not, we open HomeActivity normally.
     */
    private final Handler navHandler = new Handler(Looper.getMainLooper());
    private Runnable goHomeRunnable;
    private boolean waitingForReconnectRole = false;
    private boolean alreadyNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ServerDownUI.bind(this);

        loginCard = findViewById(R.id.loginCard);
        etUser = findViewById(R.id.etUsername);
        etPass = findViewById(R.id.etPassword);
        etConfirmPass = findViewById(R.id.etConfirmPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSwitchMode = findViewById(R.id.tvSwitchMode);
        tvError = findViewById(R.id.tvError);

        animation();

        socketHandler();
        SocketHandler.setHandler(socketHandler);

        btnLogin.setOnClickListener(v -> {
            btnLogin.startAnimation(buttonPressAnim);
            handleAction();
        });

        tvSwitchMode.setOnClickListener(v -> switchMode());
    }

    private void handleAction() {
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        String confirm = etConfirmPass.getText().toString().trim();

        hideError();

        if (user.isEmpty() || pass.isEmpty()) {
            loginCard.startAnimation(shakeAnim);
            showError(getString(R.string.fields_cannot_be_empty));
            return;
        }

        if (isSignupMode) {
            if (!pass.equals(confirm)) {
                loginCard.startAnimation(shakeAnim);
                showError(getString(R.string.passwords_do_not_match));
                return;
            }

            sendToServer("reg", user, pass);
        } else {
            sendToServer("log", user, pass);
        }

        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.35f);
    }

    private void sendToServer(String type, String user, String pass) {
        /*
         * Allows reconnect after server-down state.
         * Safe for normal login too.
         */
        tcp_send_recv.allowConnectionRetry();

        byte[] data = (type + "~" + user + "~" + pass).getBytes(StandardCharsets.UTF_8);

        new Thread(new tcp_send_recv(socketHandler, data)).start();
    }

    private void switchMode() {
        isSignupMode = !isSignupMode;
        hideError();

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

    private void animation() {
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

    private void socketHandler() {
        socketHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.obj == null) return;

                String response = (msg.obj instanceof byte[])
                        ? new String((byte[]) msg.obj, StandardCharsets.UTF_8)
                        : String.valueOf(msg.obj);

                response = response.trim();

                if (response.startsWith("log~") || response.startsWith("reg~")) {
                    handleAuthResponse(response);
                    return;
                }

                /*
                 * Reconnect case:
                 * The player logged in while the match was paused waiting for him.
                 * The server sends rol~roomId~ROLE to put him back into the correct Activity.
                 */
                if (response.startsWith("rol~")) {
                    handleReconnectRole(response);
                    return;
                }

                /*
                 * These can arrive too early during reconnect.
                 * We ignore them here because GameManager will send the important state
                 * again after a short delay to the real game Activity.
                 */
                if (response.startsWith("upd~") || response.startsWith("wrd~") || response.startsWith("rsm~")) {
                    return;
                }

                if (response.startsWith("srv~down")) {
                    ServerDownUI.showFromAnyThread();
                    return;
                }

                loginCard.startAnimation(shakeAnim);
                showError(ClientMessageUtils.unexpectedResponseMessage());
            }
        };
    }

    private void handleAuthResponse(String response) {
        String[] p = response.split("~", 3);

        if (p.length < 2) {
            loginCard.startAnimation(shakeAnim);
            showError(ClientMessageUtils.unexpectedResponseMessage());
            return;
        }

        String code = p[0];
        boolean ok = "True".equalsIgnoreCase(p[1]);
        String reason = p.length >= 3 ? p[2] : "";

        if (ok) {
            String user = etUser.getText().toString().trim();
            SocketHandler.setUsername(user);

            Toast.makeText(
                    MainActivity.this,
                    ClientMessageUtils.authSuccessMessage(code),
                    Toast.LENGTH_SHORT
            ).show();

            /*
             * Do not jump instantly to HomeActivity.
             * Wait a little in case this login is actually a game reconnect
             * and a rol~ message is about to arrive.
             */
            waitingForReconnectRole = true;
            alreadyNavigated = false;

            if (goHomeRunnable != null) {
                navHandler.removeCallbacks(goHomeRunnable);
            }

            goHomeRunnable = () -> {
                if (!alreadyNavigated && waitingForReconnectRole && !isFinishing()) {
                    waitingForReconnectRole = false;
                    alreadyNavigated = true;

                    startActivity(new Intent(MainActivity.this, HomeActivity.class));
                    finish();
                }
            };

            //delay 0.9 before gohome
            navHandler.postDelayed(goHomeRunnable, 900);
            return;
        }

        loginCard.startAnimation(shakeAnim);
        btnLogin.setEnabled(true);
        btnLogin.setAlpha(1f);
        showError(ClientMessageUtils.authMessage(code, reason));
    }

    private void handleReconnectRole(String text) {
        if (alreadyNavigated) {
            return;
        }

        String[] p = text.split("~");

        if (p.length < 3) {
            return;
        }

        String roomId = p[1].trim();
        String role = p[2].trim();

        if (roomId.isEmpty() || role.isEmpty()) {
            return;
        }

        alreadyNavigated = true;
        waitingForReconnectRole = false;

        if (goHomeRunnable != null) {
            navHandler.removeCallbacks(goHomeRunnable);
            // stops the 0.9 sec delay navHandler.postDelayed(goHomeRunnable, 900);
        }

        String username = SocketHandler.getUsername();
        if (username == null || username.isEmpty()) {
            username = etUser.getText().toString().trim();
            SocketHandler.setUsername(username);
        }

        Class<?> activityClass;

        if ("EXPLAINER".equals(role)) {
            activityClass = activity_explainer.class;
        } else if ("GUESSER".equals(role)) {
            activityClass = activity_guesser.class;
        } else {
            activityClass = activity_spectator.class;
        }

        Toast.makeText(this, "Reconnected to the match.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, activityClass);
        intent.putExtra("roomId", roomId);
        intent.putExtra("username", username);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        //cannot come back to the before screen and make the new activity the main activity now

        startActivity(intent);
        finish();
    }

    private void showError(String text) {
        tvError.setText(text);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setText("");
        tvError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (goHomeRunnable != null) {
            navHandler.removeCallbacks(goHomeRunnable);
        }

        ServerDownUI.unbind(this);
    }
}