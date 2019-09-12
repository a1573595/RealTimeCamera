package com.a1573595.realtimecamera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Size;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.a1573595.realtimecamera.tflite.Classifier;
import com.a1573595.realtimecamera.tflite.ClassificationModel;
import com.a1573595.realtimecamera.tflite.ImageUtils;

import java.io.IOException;

public class MainActivity extends CameraActivity {
    private Logger logger = new Logger(this.getClass());

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean MAINTAIN_ASPECT = false;

    private static final String MODEL_FILE = "converted_model4.tflite";
    private static final int MODEL_INPUT_SIZE = 224;

    private boolean HAS_FRONT_CAMERA = false;

    private Classifier detector;

    private long timestamp = 0;
    private boolean computingImage = false;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private TextView tv_debug;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv_debug = findViewById(R.id.tv_debug);
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        HAS_FRONT_CAMERA = hasFrontCamera();

        try {
            detector =
                    ClassificationModel.create(
                            getAssets(),
                            MODEL_FILE,
                            MODEL_INPUT_SIZE,
                            false
                    );
        } catch (final IOException e) {
            logger.e("Module could not be initialized");
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        int sensorOrientation = rotation - getScreenOrientation();

        logger.i(getString(R.string.camera_orientation_relative, sensorOrientation));

        logger.i(getString(R.string.initializing_size, previewWidth, previewHeight));
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        MODEL_INPUT_SIZE, MODEL_INPUT_SIZE,
                        sensorOrientation,
                        MAINTAIN_ASPECT
                );
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
    }

    private boolean hasFrontCamera() {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return true;
        }

        return false;
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;

        // No mutex needed as this method is not reentrant.
        if (computingImage) {
            readyForNextImage();
            return;
        }
        computingImage = true;
        logger.i("Preparing image " + currTimestamp + " for module in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                () -> {
                    final long startTime = SystemClock.uptimeMillis();
                    // 輸出結果
                    final boolean result = detector.recognizeImage(
                            !HAS_FRONT_CAMERA? croppedBitmap : flip(croppedBitmap));
                    long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    logger.i("Running detection on image " + lastProcessingTimeMs);

                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

                    computingImage = false;
                    runOnUiThread(
                            () -> {
                                tv_debug.setText(lastProcessingTimeMs +"ms\n"+ result);
                            });
                });
    }

    private Bitmap flip(Bitmap d) {
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        Bitmap dst = Bitmap.createBitmap(d, 0, 0, d.getWidth(), d.getHeight(), m, false);
        dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        return dst;
    }
}
