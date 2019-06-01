package com.example.android.tflitecamerademo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BBox {
    float x;
    float width;
    float y;
    float height;

    BBox(float x, float width, float y, float height) {
        this.x = x;
        this.width = width;
        this.y = y;
        this.height = height;
    }
}

class Prediction implements Comparable<Prediction> {
    String label;
    BBox bbox;
    Float score;

    Prediction(String label, float score, BBox bbox) {
        this.label = label;
        this.score = score;
        this.bbox = bbox;
    }

    @Override
    public int compareTo(Prediction o) {
        return o.score.compareTo(score);
    }
}

class ImageClassifier {
    private static final String TAG = "TfLiteCameraDemo";

    private static final String MODEL_PATH = "model_android/model.tflite";
    private static final String LABEL_PATH = "model_android/labels.json";
    private static final String ANCHORS_PATH = "model_android/anchors.json";

    private static final int NUM_ANCHORS = 1917;
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    static final int DIM_IMG_SIZE_X = 300;
    static final int DIM_IMG_SIZE_Y = 300;

    private static final float IOU_THRESHOLD = 0.5f;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private static final int NUM_THREADS = 4;

    private Interpreter mTensorFlowLite;
    private List<String> mLabelList;
    private double[][] mAnchors;

    ImageClassifier(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(NUM_THREADS);
        mTensorFlowLite = new Interpreter(loadModelFile(context), options);
        mLabelList = loadLabelList(context);
        mAnchors = loadAnchors(context);
    }

    /** Classifies a frame from the preview stream. */
    List<Prediction> classifyFrame(Bitmap bitmap) {
        if (mTensorFlowLite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return new ArrayList<>();
        }
        ByteBuffer buffer = convertBitmapToByteBuffer(bitmap);

        // The models output expects a shape of [1 x 1917 x 1 x 4]
        float[][][][] _boxPredictionsPointer_ = new float[1][NUM_ANCHORS][1][4];
        // The models output expects a shape of [1 x 1917 x labels_size + 1]
        float[][][] _classPredictionsPointer_ = new float[1][NUM_ANCHORS][mLabelList.size() + 1];

        Object[] inputArray = {buffer};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, _boxPredictionsPointer_);
        outputMap.put(1, _classPredictionsPointer_);
        mTensorFlowLite.runForMultipleInputsOutputs(inputArray, outputMap);

        List<Prediction> predictions = buildPredictions(_boxPredictionsPointer_, _classPredictionsPointer_);
        predictions = nms(predictions);
        return predictions;
    }

    void close() {
        mTensorFlowLite.close();
        mTensorFlowLite = null;
    }

    /** Reads label list from Assets. */
    private List<String> loadLabelList(Context context) throws IOException {
        List<String> labelList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(LABEL_PATH)))) {
            StringBuilder json = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) {
                json.append(line);
            }
            JSONArray jsonarray = new JSONArray(json.toString());
            for (int i = 0; i < jsonarray.length(); i++) {
                labelList.add(jsonarray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return labelList;
    }

    /** Reads anchors from Assets. */
    private double[][] loadAnchors(Context context) throws IOException {
        double[][] anchors = new double[NUM_ANCHORS][4];
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(ANCHORS_PATH)))) {
            StringBuilder json = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) {
                json.append(line);
            }
            JSONArray jsonarray = new JSONArray(json.toString());
            for (int i = 0; i < jsonarray.length(); i++) {
                JSONArray anchor = jsonarray.getJSONArray(i);
                for (int j = 0; j < anchor.length(); j++) {
                    anchors[i][j] = anchor.getDouble(j);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return anchors;
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /** Writes Image data into a {@code ByteBuffer}. */
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        buffer.order(ByteOrder.nativeOrder());
        buffer.rewind();

        int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        for (final int val : intValues) {
            buffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            buffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            buffer.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
        return buffer;
    }

    private List<Prediction> buildPredictions(float[][][][] boxes, float[][][] classes) {
        List<Prediction> predictions = new ArrayList<>();
        for (int c = 1; c <= mLabelList.size(); c++) {
            for (int b = 0; b <= NUM_ANCHORS - 1; b++) {
                float score = classes[0][b][c];
                if (score > CONFIDENCE_THRESHOLD) {
                    String label = mLabelList.get(c - 1);

                    float ty = boxes[0][b][0][0] / 10.0f;
                    float tx = boxes[0][b][0][1] / 10.0f;
                    float th = boxes[0][b][0][2] / 5.0f;
                    float tw = boxes[0][b][0][3] / 5.0f;

                    float yACtr = (float) mAnchors[b][0];
                    float xACtr = (float) mAnchors[b][1];
                    float ha = (float) mAnchors[b][2];
                    float wa = (float) mAnchors[b][3];

                    float w = (float) Math.exp(tw) * wa;
                    float h = (float) Math.exp(th) * ha;

                    float yCtr = ty * ha + yACtr;
                    float xCtr = tx * wa + xACtr;

                    float yMin = yCtr - h / 2.0f;
                    float xMin = xCtr - w / 2.0f;
                    float yMax = yCtr + h / 2.0f;
                    float xMax = xCtr + w / 2.0f;

                    BBox bbox = new BBox(xMin, xMax - xMin, yMin, yMax - yMin);
                    predictions.add(new Prediction(label, score, bbox));
                }
            }
        }

        return predictions;
    }

    private List<Prediction> nms(List<Prediction> predictions) {
        Collections.sort(predictions);

        List<Prediction> selected = new ArrayList<>();

        for (Prediction predictionA : predictions) {

            boolean shouldSelect = true;
            BBox boxA = predictionA.bbox;

            // Does the current box overlap one of the selected boxes more than the
            // given threshold amount? Then it's too similar, so don't keep it.
            for (Prediction predictionB : selected) {
                BBox boxB = predictionB.bbox;
                if (IOU(boxA, boxB) > IOU_THRESHOLD) {
                    shouldSelect = false;
                    break;
                }
            }

            // This bounding box did not overlap too much with any previously selected
            // bounding box, so we'll keep it.
            if (shouldSelect) {
                selected.add(predictionA);
            }
        }

        return selected;
    }

    private float IOU(BBox a, BBox b ) {
        float areaA = a.width * a.height;
        if (areaA <= 0) {
            return 0;
        }

        float areaB = b.width * b.height;
        if (areaB <= 0) {
            return 0;
        }

        float intersectionMinX = Math.max(a.x, b.x);
        float intersectionMinY = Math.max(a.y, b.y);
        float intersectionMaxX = Math.min(a.width + a.x, b.width + b.x);
        float intersectionMaxY = Math.min(a.height + a.y, b.height + a.y);
        float intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0) * Math.max(intersectionMaxX - intersectionMinX, 0);
        return intersectionArea / (areaA + areaB - intersectionArea);
    }
}
