package com.example.dibacain.modules;

import android.accessibilityservice.AccessibilityService;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public class ScreanReaderService extends AccessibilityService {

    private TextToSpeech tts;
    private String lastText = "";

    @Override
    public void onCreate() {
        super.onCreate();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("id", "ID"));
                tts.setSpeechRate(1.0f);
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null || event.getText() == null) {
            return;
        }

        String text = event.getText().toString().trim();

        if (text.isEmpty()) {
            return;
        }

        if (text.equals(lastText)) {
            return;
        }

        lastText = text;

        Log.d("SCREEN_READER", text);

        if (tts != null) {
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "screen_reader"
            );
        }
    }

    @Override
    public void onInterrupt() {

        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    public void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
