package com.alongiss.Alonias;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import java.lang.ref.WeakReference;

public final class ServerDownUI {

    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private static boolean dialogShowing = false;

    private ServerDownUI() {}

    public static void bind(Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
    }

    public static void unbind(Activity activity) {
        Activity current = currentActivityRef.get();
        if (current == activity) {
            currentActivityRef = new WeakReference<>(null);
        }
    }

    public static void showFromAnyThread() {
        Activity activity = currentActivityRef.get();

        if (activity == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> show(activity));
    }

    private static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (dialogShowing) {
            return;
        }

        dialogShowing = true;

        try {
            View root = activity.getWindow().getDecorView().getRootView();
            disableViewTree(root);
        } catch (Exception ignored) {
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Connection lost")
                .setMessage("The server connection was lost. You cannot continue using online features until you reconnect.")
                .setCancelable(false)
                .setPositiveButton("Back to login", (d, w) -> {
                    dialogShowing = false;

                    try {
                        SocketHandler.reset();
                    } catch (Exception ignored) {
                    }

                    tcp_send_recv.allowConnectionRetry();

                    Intent intent = new Intent(activity, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                    activity.startActivity(intent);
                    activity.finish();
                })
                .create();

        dialog.setOnDismissListener(d -> dialogShowing = false);
        dialog.show();
    }

    private static void disableViewTree(View view) {
        if (view == null) {
            return;
        }

        view.setEnabled(false);
        view.setClickable(false);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                disableViewTree(group.getChildAt(i));
            }
        }
    }
}