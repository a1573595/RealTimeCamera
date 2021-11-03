package com.a1573595.realtimecamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.a1573595.realtimecamera.tflite.ImageUtils;
import com.a1573595.realtimecamera.tool.Logger;

public abstract class CameraActivity extends BaseActivity implements Camera.PreviewCallback {
    private Logger logger = new Logger(this.getClass());

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final int REQUEST_CAMERA = 1;

    protected int previewWidth;
    protected int previewHeight;

    private Handler handler;
    private HandlerThread handlerThread;

    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    private boolean isProcessingFrame = false;
    private int[] rgbBytes;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    requestPermission(REQUEST_CAMERA, PERMISSION_CAMERA);
                    return;
                }
            }

            initCamera();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);

        if (!hasPermissions(PERMISSION_CAMERA)) {
            requestPermission(REQUEST_CAMERA, PERMISSION_CAMERA);
        } else {
            initCamera();
        }
    }

    private void initCamera() {
        Fragment fragment =
                new LegacyCameraConnectionFragment(
                        this,
                        R.layout.camera_connection_fragment_tracking,
                        getDesiredPreviewFrameSize()
                );
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (isProcessingFrame) {
            logger.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), getScreenOrientation());//3 調整補償角度
            }
        } catch (final Exception e) {
            logger.e(e.toString());
            return;
        }
        isProcessingFrame = true;

        imageConverter =
                () -> ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);

        postInferenceCallback =
                () -> {
                    camera.addCallbackBuffer(bytes);
                    isProcessingFrame = false;
                };
        processImage();
    }

    @Override
    public synchronized void onStart() {
        logger.d("onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        logger.d("onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        logger.d("onPause " + this);

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            logger.e(e.toString());
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        logger.d("onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        logger.d("onDestroy " + this);
        super.onDestroy();
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract void processImage();
}
