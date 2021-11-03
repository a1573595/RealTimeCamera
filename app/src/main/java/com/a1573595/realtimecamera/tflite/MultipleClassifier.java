package com.a1573595.realtimecamera.tflite;

import android.graphics.Bitmap;

public interface MultipleClassifier {
    float[] recognizeImage(Bitmap bitmap);
}
