package com.a1573595.realtimecamera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Size;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.a1573595.realtimecamera.customView.OverlayView;
import com.a1573595.realtimecamera.tflite.ImageUtils;
import com.a1573595.realtimecamera.tflite.MultiBoxTracker;
import com.a1573595.realtimecamera.tflite.ObjectionDetector;
import com.a1573595.realtimecamera.tflite.ObjectionDetectionModel;
import com.a1573595.realtimecamera.tool.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class ObjectDetectionActivity extends CameraActivity {
    private Logger logger = new Logger(this.getClass());

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean MAINTAIN_ASPECT = false;

    private static final String MODEL_FILE = "objectdetect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/objectlabelmap.txt";
    private static final int MODEL_INPUT_SIZE = 300;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.5f;

    private boolean HAS_FRONT_CAMERA = false;

    private ObjectionDetector detector;

    private long timestamp = 0;
    private boolean computingImage = false;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private OverlayView trackingOverlay;
    private MultiBoxTracker tracker;
    private TextView tv_debug;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv_debug = findViewById(R.id.tv_debug);
    }

    @Override
    public void onResume() {
        super.onResume();

        computingImage = false;
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
                    ObjectionDetectionModel.create(
                            getAssets(),
                            MODEL_FILE,
                            LABELS_FILE,
                            MODEL_INPUT_SIZE,
                            true
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

        tracker = new MultiBoxTracker(this);
        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> {
                    tracker.draw(canvas);
//                    if (BuildConfig.DEBUG) {
//                        tracker.drawDebug(canvas);
//                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
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
        trackingOverlay.postInvalidate();

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
                    final List<ObjectionDetector.Recognition> results = detector.recognizeImage(
                            HAS_FRONT_CAMERA ? croppedBitmap : flip(croppedBitmap));
                    long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    logger.i("Running detection on image " + lastProcessingTimeMs);

                    final List<ObjectionDetector.Recognition> mappedRecognitions = new LinkedList<>();

                    for (final ObjectionDetector.Recognition result : results) {
                        final RectF location = result.getLocation();
                        if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {

                            cropToFrameTransform.mapRect(location);

                            result.setLocation(location);
                            mappedRecognitions.add(result);
                        }
                    }

                    tracker.trackResults(mappedRecognitions, currTimestamp);
                    trackingOverlay.postInvalidate();

                    computingImage = false;

                    runOnUiThread(
                            () -> {
                                tv_debug.setText(lastProcessingTimeMs + "ms");
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
