package com.a1573595.realtimecamera;

import android.util.Size;

public class MainActivity extends CameraActivity {
    private final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {

    }

    @Override
    protected void processImage() {

    }
}
