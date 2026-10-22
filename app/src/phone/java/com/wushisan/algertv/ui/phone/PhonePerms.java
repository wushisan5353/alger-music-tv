package com.wushisan.algertv.ui.phone;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * POST_NOTIFICATIONS is a runtime permission on Android 13+ (API 33). It is
 * requested lazily — from the player screen / first playback — and a denial
 * must never block playback, so every call is best-effort and the result is
 * ignored.
 */
public final class PhonePerms {

    private static final int REQ_NOTIFICATIONS = 7001;

    private PhonePerms() {
    }

    public static void requestNotificationsIfNeeded(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATIONS);
            }
        } catch (Exception ignored) {
            // Never let a permission hiccup break playback.
        }
    }
}
