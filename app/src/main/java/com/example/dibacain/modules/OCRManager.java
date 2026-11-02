package com.example.dibacain.modules;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OCRManager {

    private static final String TAG = "OCRManager";

    private final com.google.mlkit.vision.text.TextRecognizer recognizer;

    public OCRManager() {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    public interface OCRCallback {
        void onSuccess(String text);
        void onFailure(Exception e);
    }

    public void recognizeText(
            @NonNull Bitmap bitmap,
            @NonNull OCRCallback callback
    ) {

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(result -> {

                    StringBuilder builder = new StringBuilder();

                    for (Text.TextBlock block : result.getTextBlocks()) {
                        builder.append(block.getText())
                                .append("\n");
                    }

                    String detectedText =
                            builder.toString().trim();

                    Log.d(TAG, "OCR Result: " + detectedText);

                    callback.onSuccess(detectedText);
                })
                .addOnFailureListener(e -> {

                    Log.e(TAG,
                            "OCR Error: " + e.getMessage(),
                            e);

                    callback.onFailure(e);
                });
    }

    public void close() {
        recognizer.close();
    }
}