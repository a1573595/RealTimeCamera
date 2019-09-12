package com.a1573595.realtimecamera.tflite;

import android.graphics.Bitmap;

public interface Classifier {
    boolean recognizeImage(Bitmap bitmap);

    void setNumThreads(int num_threads);
}