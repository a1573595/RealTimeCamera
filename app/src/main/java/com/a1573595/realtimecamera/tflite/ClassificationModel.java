package com.a1573595.realtimecamera.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ClassificationModel implements Classifier {
    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;

    private Interpreter tfLite;
    private int inputSize;
    private boolean isModelQuantized;

    private int[] intValues;
    private ByteBuffer imgData;
    private float[][] outResult = new float[1][1];

    private void ImageClassificationModel() {}

    public static Classifier create(
            final AssetManager assetManager,
            final String modelFileName,
            final int inputSize,
            final boolean isQuantized)
            throws IOException {
        final ClassificationModel d = new ClassificationModel();

        d.inputSize = inputSize;
        d.isModelQuantized = isQuantized;

        try {
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFileName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (d.isModelQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }

        d.imgData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

        d.tfLite.setNumThreads(2);

        return d;
    }

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public boolean recognizeImage(Bitmap bitmap) {
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        tfLite.run(imgData, outResult);

        return outResult[0][0]>0.5f;
    }

    @Override
    public void setNumThreads(int threads) {
        tfLite.setNumThreads(threads);
    }
}
