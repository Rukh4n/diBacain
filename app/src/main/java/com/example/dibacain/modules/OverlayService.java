package com.example.dibacain.modules;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "overlay_service_channel";
    private static final int NOTIFICATION_ID = 1002;

    private WindowManager windowManager;
    private FrameLayout overlayView;
    private WindowManager.LayoutParams params;
    private ScaleGestureDetector scaleGestureDetector;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private boolean isScaling = false;

    public static int currentX = 100;
    public static int currentY = 100;
    public static int currentWidth = 400;
    public static int currentHeight = 400;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("diBacain")
                .setContentText("Overlay Hack Aktif")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        overlayView = new FrameLayout(this);
        overlayView.setBackgroundColor(0x55FF0000);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        params = new WindowManager.LayoutParams(
                currentWidth,
                currentHeight,
                layoutType,
                flags,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = currentX;
        params.y = currentY;

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                int newWidth = (int) (params.width * factor);
                int newHeight = (int) (params.height * factor);

                if (newWidth < 150) newWidth = 150;
                if (newHeight < 150) newHeight = 150;
                if (newWidth > 2000) newWidth = 2000;

                params.width = newWidth;
                params.height = newHeight;
                currentWidth = newWidth;
                currentHeight = newHeight;

                try {
                    if (overlayView.getWindowToken() != null) {
                        windowManager.updateViewLayout(overlayView, params);
                    }
                } catch (Exception ignored) {}
                return true;
            }
        });

        overlayView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);

            if (event.getPointerCount() > 1) {
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!isScaling) {
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isScaling) return true;

                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);

                    if (params.x < 0) params.x = 0;
                    if (params.y < 0) params.y = 0;

                    currentX = params.x;
                    currentY = params.y;

                    try {
                        if (overlayView.getWindowToken() != null) {
                            windowManager.updateViewLayout(overlayView, params);
                        }
                    } catch (Exception ignored) {}
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isScaling = false;
                    return true;
            }
            return false;
        });

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception ignored) {}

        overlayView = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int getSelectedX() {
        return params != null ? params.x : 0;
    }

    public int getSelectedY() {
        return params != null ? params.y : 0;
    }

    public int getSelectedWidth() {
        return params != null ? params.width : 0;
    }

    public int getSelectedHeight() {
        return params != null ? params.height : 0;
    }
}