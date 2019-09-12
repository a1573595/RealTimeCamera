package com.a1573595.realtimecamera;

import android.Manifest;
import android.os.Bundle;

public class MainActivity extends BaseActivity {
    private final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private final int REQUEST_CAMERA = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(!hasPermissions(PERMISSION_CAMERA))
            requestPermission(REQUEST_CAMERA, PERMISSION_CAMERA);
    }
}
