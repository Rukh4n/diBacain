package com.example.dibacain;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.dibacain.modules.OverlayService;
import com.example.dibacain.modules.ScreenCaptureService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String ACTION_SERVICE_STATE_CHANGED = "com.example.dibacain.ACTION_SERVICE_STATE_CHANGED";
    public static final String EXTRA_IS_RUNNING = "extra_is_running";

    private Button btnMulai;
    private boolean isRunning = false;
    private MediaProjectionManager mediaProjectionManager;
    private ActivityResultLauncher<Intent> screenCaptureLauncher;

    // BroadcastReceiver untuk menerima update status langsung dari ScreenCaptureService
    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                boolean isServiceRunning = intent.getBooleanExtra(EXTRA_IS_RUNNING, false);
                if (isServiceRunning) {
                    setUiToStopState();
                } else {
                    setUiToStartState();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );
                    return insets;
                }
        );

        btnMulai = findViewById(R.id.btnMulai);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "ResultCode = " + result.getResultCode());
                    Log.d(TAG, "Data = " + result.getData());

                    if (result.getResultCode() != Activity.RESULT_OK) {
                        Toast.makeText(this, "Izin screen capture ditolak", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent data = result.getData();
                    if (data == null) {
                        Toast.makeText(this, "Data MediaProjection null", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "MediaProjection data null");
                        return;
                    }

                    Intent screenIntent = new Intent(this, ScreenCaptureService.class);
                    screenIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                    screenIntent.putExtra(ScreenCaptureService.EXTRA_DATA, data);

                    ContextCompat.startForegroundService(this, screenIntent);

                    Intent overlayIntent = new Intent(this, OverlayService.class);
                    startService(overlayIntent);

                    setUiToStopState();
                }
        );

        btnMulai.setOnClickListener(v -> {
            if (!isRunning) {
                startReader();
            } else {
                stopReader();
            }
        });

        checkServiceStatus(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Registrasi receiver untuk mendengarkan perubahan status service secara real-time
        IntentFilter filter = new IntentFilter(ACTION_SERVICE_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceStateReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister receiver saat activity tidak terlihat untuk menghindari memory leak
        try {
            unregisterReceiver(serviceStateReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkServiceStatus(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cek status variabel statis dari ScreenCaptureService untuk validasi UI
        if (com.example.dibacain.modules.ScreenCaptureService.isServiceActive) {
            setUiToStopState();
        } else {
            setUiToStartState();
        }
    }

    private void checkServiceStatus(Intent intent) {
        if (intent != null && intent.getBooleanExtra("is_service_running", false)) {
            setUiToStopState();
        } else if (com.example.dibacain.modules.ScreenCaptureService.isServiceActive) {
            setUiToStopState();
        } else {
            setUiToStartState();
        }
    }

    private void setUiToStopState() {
        isRunning = true;
        btnMulai.setText("STOP");
        btnMulai.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
    }

    private void setUiToStartState() {
        isRunning = false;
        btnMulai.setText("MULAI");
        btnMulai.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2196F3")));
    }

    private void startReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            Toast.makeText(this, "Izinkan overlay terlebih dahulu", Toast.LENGTH_LONG).show();
            return;
        }

        if (mediaProjectionManager == null) {
            Toast.makeText(this, "MediaProjectionManager null", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            screenCaptureLauncher.launch(captureIntent);
        } catch (Exception e) {
            Log.e(TAG, "Gagal meluncurkan intent screen capture", e);
            Toast.makeText(this, "Gagal memulai perekaman layar", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopReader() {
        setUiToStartState();

        stopService(new Intent(this, OverlayService.class));
        stopService(new Intent(this, ScreenCaptureService.class));

        Toast.makeText(this, "OCR + TTS Dihentikan", Toast.LENGTH_SHORT).show();
    }
}