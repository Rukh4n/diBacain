package com.example.dibacain.modules;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE";
    private static final String ACTION_SERVICE_STATE_CHANGED = "com.example.dibacain.ACTION_SERVICE_STATE_CHANGED";
    private static final String EXTRA_IS_RUNNING = "extra_is_running";

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_DATA = "extra_data";
    private static final String CHANNEL_ID = "screen_capture_channel";

    public static boolean isServiceActive = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private WindowManager windowManager;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private OCRManager ocrManager;
    private TTSManager ttsManager;

    private List<String> previousLines = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceActive = true;

        handler = new Handler(Looper.getMainLooper());
        ocrManager = new OCRManager();
        ttsManager = new TTSManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        updateScreenDimensions();
        createNotificationChannel();
        sendStateBroadcast(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            Log.d(TAG, "Menghentikan service dari notifikasi");

            try {
                Intent overlayIntent = new Intent(this, Class.forName("com.example.dibacain.modules.OverlayService"));
                stopService(overlayIntent);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Gagal menemukan kelas OverlayService saat menghentikan layanan", e);
            }

            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent == null) {
            Log.e(TAG, "Intent null");
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_DATA);
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "ResultCode atau data tidak valid");
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = buildForegroundNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1001, notification);
        }

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Log.e(TAG, "MediaProjectionManager null");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            mediaProjection = manager.getMediaProjection(resultCode, data);
        } catch (Exception e) {
            Log.e(TAG, "Gagal membuat MediaProjection", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection null");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "MediaProjection berhasil dibuat");

        initializeCapture();
        startCaptureLoop();

        return START_STICKY;
    }

    private void updateScreenDimensions() {
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                screenWidth = windowManager.getCurrentWindowMetrics().getBounds().width();
                screenHeight = windowManager.getCurrentWindowMetrics().getBounds().height();
                screenDensity = getResources().getConfiguration().densityDpi;
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                screenWidth = metrics.widthPixels;
                screenHeight = metrics.heightPixels;
                screenDensity = metrics.densityDpi;
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent;
        try {
            notificationIntent = new Intent(this, Class.forName("com.example.dibacain.MainActivity"));
        } catch (ClassNotFoundException e) {
            notificationIntent = new Intent();
        }

        notificationIntent.putExtra("is_service_running", true);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, pendingIntentFlags
        );

        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent, pendingIntentFlags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Dibacain")
                .setContentText("OCR Screen Reader Aktif")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void initializeCapture() {
        if (mediaProjection == null) return;

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "MediaProjection dihentikan oleh sistem.");
                mainHandler.post(() -> {
                    try {
                        Intent overlayIntent = new Intent(ScreenCaptureService.this, Class.forName("com.example.dibacain.modules.OverlayService"));
                        stopService(overlayIntent);
                    } catch (Exception ignored) {}
                    stopSelf();
                });
            }
        }, mainHandler);

        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    null
            );
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Akses dibatasi (Pengguna memilih mode Single App pada Android 14/15)", e);
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Gagal menginisialisasi VirtualDisplay", e);
            stopSelf();
        }
    }

    private void startCaptureLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                captureFrame();
                handler.postDelayed(this, 1000);
            }
        }, 1500);
    }

    private void captureFrame() {
        if (imageReader == null || mediaProjection == null) return;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();

            int currentBitmapWidth = rowStride / pixelStride;
            int currentBitmapHeight = image.getHeight();

            if (currentBitmapWidth <= 0 || currentBitmapHeight <= 0) return;

            Bitmap bitmap = Bitmap.createBitmap(
                    currentBitmapWidth,
                    currentBitmapHeight,
                    Bitmap.Config.ARGB_8888
            );

            bitmap.copyPixelsFromBuffer(buffer);

            updateScreenDimensions();

            int cropX = OverlayService.currentX;
            int cropY = OverlayService.currentY;
            int cropWidth = OverlayService.currentWidth;
            int cropHeight = OverlayService.currentHeight;

            if (cropY < 100) {
                int diff = 100 - cropY;
                cropY = 100;
                cropHeight -= diff;
            }

            if (cropX >= currentBitmapWidth) cropX = 0;
            if (cropY >= currentBitmapHeight) cropY = 100;

            if (cropX + cropWidth > currentBitmapWidth) {
                cropWidth = currentBitmapWidth - cropX;
            }
            if (cropY + cropHeight > currentBitmapHeight) {
                cropHeight = currentBitmapHeight - cropY;
            }

            if (cropWidth > 0 && cropHeight > 0) {
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);
                processOCR(croppedBitmap);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Perekaman diblokir oleh sistem atau karena beralih ke Single App mode.", e);
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Capture Error (Terproteksi dari rotasi crash)", e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void processOCR(Bitmap bitmap) {
        ocrManager.recognizeText(bitmap, new OCRManager.OCRCallback() {
            @Override
            public void onSuccess(String text) {
                if (text == null || text.trim().isEmpty()) {
                    if (ttsManager != null) {
                        ttsManager.stop();
                    }
                    previousLines.clear();
                    return;
                }

                String[] currentLines = text.split("\n");
                List<String> newLines = new ArrayList<>();
                List<String> currentLinesList = new ArrayList<>();

                for (String line : currentLines) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.isEmpty()) {
                        currentLinesList.add(trimmedLine);
                        if (!previousLines.contains(trimmedLine)) {
                            newLines.add(trimmedLine);
                        }
                    }
                }

                if (!currentLinesList.isEmpty() && !previousLines.isEmpty()) {
                    int matchCount = 0;
                    for (String line : currentLinesList) {
                        if (previousLines.contains(line)) {
                            matchCount++;
                        }
                    }
                    if (((float) matchCount / currentLinesList.size()) < 0.2f) {
                        if (ttsManager != null) {
                            ttsManager.stop();
                        }
                    }
                }

                previousLines = currentLinesList;

                if (!newLines.isEmpty()) {
                    StringBuilder speechBuilder = new StringBuilder();
                    for (String newLine : newLines) {
                        speechBuilder.append(newLine).append(" ");
                    }

                    String textToSpeak = speechBuilder.toString().trim();
                    Log.d(TAG, "Teks baru dideteksi: " + textToSpeak);

                    if (ttsManager != null && ttsManager.isReady()) {
                        ttsManager.speak(textToSpeak);
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "OCR Error", e);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendStateBroadcast(boolean isRunning) {
        Intent intent = new Intent(ACTION_SERVICE_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_RUNNING, isRunning);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        isServiceActive = false;
        sendStateBroadcast(false);

        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (imageReader != null) {
            imageReader.close();
        }
        if (ocrManager != null) {
            ocrManager.close();
        }
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}