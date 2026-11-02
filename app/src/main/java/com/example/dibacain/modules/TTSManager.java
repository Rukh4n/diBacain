package com.example.dibacain.modules;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TTSManager {

    private static final String TAG = "TTSManager";

    private TextToSpeech tts;
    private boolean isReady = false;

    public TTSManager(Context context) {
        tts = new TextToSpeech(
                context.getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(new Locale("id", "ID"));

                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Bahasa Indonesia tidak didukung, mencoba fallback Locale standar");
                            result = tts.setLanguage(Locale.getDefault());
                        }

                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Locale saat ini tidak didukung oleh mesin TTS");
                            isReady = false;
                        } else {
                            tts.setSpeechRate(1.0f);
                            tts.setPitch(1.0f);
                            isReady = true;
                            Log.d(TAG, "TTS siap digunakan");
                        }
                    } else {
                        Log.e(TAG, "Gagal inisialisasi TTS");
                        isReady = false;
                    }
                }
        );
    }

    public boolean isReady() {
        return isReady;
    }

    public void speak(String text) {
        if (!isReady || tts == null) {
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(System.currentTimeMillis()));

        tts.speak(text, TextToSpeech.QUEUE_ADD, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID));
    }

    public void speakNow(String text) {
        if (!isReady || tts == null) {
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(System.currentTimeMillis()));

        tts.speak(text, TextToSpeech.QUEUE_ADD, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID));
    }

    public void stop() {
        if (tts != null && isReady) {
            try {
                // Menghentikan ucapan yang sedang berjalan saat ini dan sekaligus membersihkan seluruh antrean suara penumpukan
                tts.stop();
                Log.d(TAG, "Antrean TTS berhasil dibersihkan.");
            } catch (Exception e) {
                Log.e(TAG, "Gagal menghentikan TTS", e);
            }
        }
    }

    public void setSpeechRate(float rate) {
        if (tts != null) {
            tts.setSpeechRate(rate);
        }
    }

    public void setPitch(float pitch) {
        if (tts != null) {
            tts.setPitch(pitch);
        }
    }

    public void shutdown() {
        isReady = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error saat shutdown TTS", e);
            } finally {
                tts = null;
            }
        }
    }
}
