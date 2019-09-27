package com.a1573595.realtimecamera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.a1573595.realtimecamera.customView.OverlayView;
import com.a1573595.realtimecamera.tflite.ImageUtils;
import com.a1573595.realtimecamera.tflite.MultipleClassificationModel;
import com.a1573595.realtimecamera.tflite.MultipleClassifier;
import com.a1573595.realtimecamera.tool.Logger;
import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.RobotCmdState;
import com.asus.robotframework.API.RobotCommand;
import com.asus.robotframework.API.RobotErrorCode;
import com.asus.robotframework.API.RobotFace;
import com.asus.robotframework.API.WheelLights;

import java.io.IOException;
import java.util.Arrays;

public class LogoClassificationActivity extends CameraActivity {
    private Logger logger = new Logger(this.getClass());

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean MAINTAIN_ASPECT = false;

    private static final String MODEL_FILE = "logoclassify2.tflite";
    private static final int MODEL_INPUT_SIZE = 224;
    private static final int NUM_ITEMS = 7;

    private boolean HAS_FRONT_CAMERA = false;

    private MultipleClassifier detector;

    private long timestamp = 0;
    private boolean computingImage = false;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private Matrix frameToCropTransform;
    private int scale_width = 0;

    private OverlayView trackingOverlay;
    private Paint paint;

    private TextView tv_debug;
    private String[] logos = new String[]{"Carleton","McMaster","Trent","Uoit","UoT","Waterloo", "Background"};

    private RobotAPI robotAPI;
    private RobotCallback robotCallback;

    private int serialStatus = -1;
    private int[] frameArray = new int[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv_debug = findViewById(R.id.tv_debug);

        initZenbo();
    }

    private void initZenbo() {
        initRobotCallback();
        robotAPI = new RobotAPI(getApplicationContext(), robotCallback);
    }

    private void initRobotCallback() {
        robotCallback = new RobotCallback() {
            @Override
            public void onResult(int cmd, int serial, RobotErrorCode err_code, Bundle result) {
                super.onResult(cmd, serial, err_code, result);
            }

            @Override
            public void onStateChange(int cmd, int serial, RobotErrorCode err_code, RobotCmdState state) {
                super.onStateChange(cmd, serial, err_code, state);

                logger.i(RobotCommand.getRobotCommand(cmd).name()
                        + ", serial:" + serial + ", state:" + state.ordinal());

                switch (RobotCommand.getRobotCommand(cmd).name()) {
                    case "SPEAK":
                        if(state.ordinal()==3); // Start
                        break;
                    case "PLAY_EMOTIONAL_ACTION":
                        break;
                    case "SET_EXPRESSION":
                        if(state.ordinal()==5 && serial == serialStatus){   //End
                            robotAPI.robot.setExpression(RobotFace.HIDEFACE);

                            Arrays.fill(frameArray, -1);
                            serialStatus = -1;
                        }
                        break;
                }
            }

            @Override
            public void initComplete() {
                super.initComplete();

            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        computingImage = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(robotAPI!=null)
            robotAPI.release();
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        HAS_FRONT_CAMERA = hasFrontCamera();
        // Initialize TensorFlow Lite module
        try {
            detector =
                    MultipleClassificationModel.create(
                            getAssets(),
                            MODEL_FILE,
                            MODEL_INPUT_SIZE,
                            false,
                            NUM_ITEMS
                    );
        } catch (final IOException e) {
            logger.e("Module could not be initialized");
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
        logger.i(getString(R.string.initializing_size, previewWidth, previewHeight));

        int sensorOrientation = rotation - getScreenOrientation();
        logger.i(getString(R.string.camera_orientation_relative, sensorOrientation));

        trackingOverlay = findViewById(R.id.tracking_overlay);

        int h = trackingOverlay.getMeasuredHeight();
        int w = trackingOverlay.getMeasuredWidth();
        int preview_scale = h > w ? w / DESIRED_PREVIEW_SIZE.getWidth() :
                h / DESIRED_PREVIEW_SIZE.getHeight();  //預覽畫面放大比例
        int crop_scale = DESIRED_PREVIEW_SIZE.getWidth()/MODEL_INPUT_SIZE;   //預覽畫面與擷取影像比例
        float scale = preview_scale * crop_scale / 2;

        RectF rectF = new RectF(
                (w / 2) - (MODEL_INPUT_SIZE * scale),
                (h / 2) - (MODEL_INPUT_SIZE * scale),
                (w / 2) + (MODEL_INPUT_SIZE * scale),
                (h / 2) + (MODEL_INPUT_SIZE * scale)
        );
        scale_width = (int)(rectF.width() / scale);

        paint = new Paint();
        paint.setAntiAlias(true);//消除鋸齒
        paint.setStrokeWidth(25);//設置畫筆的寬度
        paint.setStyle(Paint.Style.STROKE);//設置繪製輪廓
        paint.setColor(Color.RED);//設置顏色

        trackingOverlay.addCallback(
                canvas -> canvas.drawRect(rectF, paint));

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        scale_width, scale_width,
                        MODEL_INPUT_SIZE, MODEL_INPUT_SIZE,
                        sensorOrientation,
                        MAINTAIN_ASPECT
                );
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
        canvas.drawBitmap(cropBitmap(rgbFrameBitmap, scale_width), frameToCropTransform, null);

        runInBackground(
                () -> {
                    final long startTime = SystemClock.uptimeMillis();
                    // Module output
                    final float[] results = detector.recognizeImage(
                            HAS_FRONT_CAMERA? croppedBitmap : flip(croppedBitmap));
                    long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    logger.i("Running detection on image " + lastProcessingTimeMs);

                    final int index_logo = findMaxIndex(results);
                    if(index_logo==NUM_ITEMS-1) {
                        paint.setColor(Color.RED);
                        robotAPI.wheelLights.setColor(WheelLights.Lights.SYNC_BOTH, 0xff, 0xFF0000);
                    }else {
                        paint.setColor(Color.GREEN);
                        robotAPI.wheelLights.setColor(WheelLights.Lights.SYNC_BOTH, 0xff, 0x00FF00);
                    }
                    trackingOverlay.postInvalidate();

                    computingImage = false;

                    runOnUiThread(
                            () -> {
                                tv_debug.setText(String.format("%d ms\n%s: %f\n%s: %f\n" +
                                                "%s: %f\n%s: %f\n%s: %f\n%s: %f\n%s: %f\n\n%s",
                                        lastProcessingTimeMs, logos[0], results[0], logos[1], results[1]
                                        , logos[2], results[2], logos[3], results[3],
                                        logos[4], results[4], logos[5], results[5],
                                        logos[6], results[6], logos[index_logo]));

                                if(serialStatus!=-1) return;
                                for(int i=frameArray.length-1;i>=0;i--){
                                    frameArray[i] = ((i>0)?frameArray[i-1]:index_logo);
                                }

                                if(index_logo!=NUM_ITEMS-1 && isSame(frameArray)) {
                                    zenboTalking(index_logo);
                                }
                            });
                });
    }

    private Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
    //從中間截取一個正方形
    public Bitmap cropBitmap(Bitmap bitmap, int cropWidth) {
        int w = bitmap.getWidth(); // 得到圖片的寬，高
        int h = bitmap.getHeight();

        return Bitmap.createBitmap(bitmap, (w - cropWidth) / 2,
                (h - cropWidth) / 2, cropWidth, cropWidth);
    }

    private Bitmap flip(Bitmap d) {
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        Bitmap dst = Bitmap.createBitmap(d, 0, 0, d.getWidth(), d.getHeight(), m, false);
        dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        return dst;
    }

    private int findMaxIndex(float[] array) {
        int index = 0;
        float max = 0;

        for (int i=0;i<array.length;i++){
            if(array[i]>max){
                max = array[i];
                index = i;
            }
        }
        return index;
    }

    private boolean isSame(int[] array) {
        boolean isSame = true;
        for (int i=0;i<array.length-1;i++){
            if(array[i]!=array[i+1]){
                isSame = false;
                break;
            }
        }
        return isSame;
    }

    private void zenboTalking(int index) {
        String sentence;
        // ACTIVE、CONFIDENT、EXPECTING、HAPPY、INTERESTED、PLEASED、PROUD、SINGING
        RobotFace face = RobotFace.HAPPY;

        switch (index) {
            case 0:
                sentence = "Carleton University, it is a public comprehensive university in Ottawa. We are good friends.";
                break;
            case 1:
                sentence = "McMaster University, it is seen as a science-oriented university, and is known for it's medical school. I  have a lot of friends on campus over there.";
                break;
            case 2:
                sentence = "Trent University, it is a public university in Peterborough. We are partners for a long time.";
                break;
            case 3:
                sentence = "Ontario Tech University, I work on there, it is a great place. You should study there. I look forward to seeing you there at Ontario Tech University on campus.";
                face = RobotFace.SHY;
                break;
            case 4:
                sentence = "University of Toronto, We Ontario Tech University has a lot of faculty members graduated from there.";
                face = RobotFace.EXPECTING;
                break;
            default:
                sentence = "University of Waterloo, it is most famous for its co-operative education programs. We are good research partners.";
                face = RobotFace.WORRIED;
                break;
        }

        serialStatus = robotAPI.robot.setExpression(face, sentence);
    }
}
