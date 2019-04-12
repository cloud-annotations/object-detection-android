package com.example.android.tflitecamerademo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.xlythe.fragment.camera.CameraFragment;
import com.xlythe.view.camera.CameraView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

public class CustomCameraFragment extends CameraFragment {
    private ImageClassifier mClassifier;

    private CameraView mCamera;
    private ImageView mPreview;
    private ViewGroup mCameraLayout;
    private ViewGroup mPreviewLayout;
    private Button mCapture;
    private Button mClose;

    private boolean mShowCamera;

    private View.OnClickListener mBitmapCaptured = (view) -> {
        try {
            // Reflection.
            Field field = mCamera.getClass().getDeclaredField("mCameraView");
            field.setAccessible(true);
            TextureView textureView = (TextureView) field.get(mCamera);

            // Get the image and classify it.
            Bitmap bitmap = textureView.getBitmap();
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, ImageClassifier.DIM_IMG_SIZE_X,  ImageClassifier.DIM_IMG_SIZE_Y, false);
            List<Prediction> predictions = mClassifier.classifyFrame(scaled);

            Log.d("CameraFrag", "PREDICTING");
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);

            for (Prediction prediction : predictions) {
                Log.d("CameraFrag", prediction.label + ": " + prediction.score);
                Log.d("CameraFrag", "[ " + prediction.bbox.x + ", " + prediction.bbox.width + ", " + prediction.bbox.y + ", " + prediction.bbox.height + " ]");
                canvas.drawRect(prediction.bbox.x * canvas.getWidth(),
                        prediction.bbox.y * canvas.getHeight(),
                        (prediction.bbox.x + prediction.bbox.width) * canvas.getWidth(),
                        (prediction.bbox.y + prediction.bbox.height) * canvas.getHeight(),
                        paint);
            }

            mPreview.setImageBitmap(bitmap);
            mPreview.draw(canvas);
            showPreview();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    };

    private View.OnClickListener mClosePreview = (view) -> showCamera();

    private void showPreview() {
        mShowCamera = false;
        mCameraLayout.setVisibility(View.GONE);
        mPreviewLayout.setVisibility(View.VISIBLE);
    }

    private void showCamera() {
        mShowCamera = true;
        mCameraLayout.setVisibility(View.VISIBLE);
        mPreviewLayout.setVisibility(View.GONE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mShowCamera = true;
        try {
            mClassifier = new ImageClassifier(getContext());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return View.inflate(getContext(), R.layout.fragment_custom_camera, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCamera = view.findViewById(R.id.camera);
        mPreview = view.findViewById(R.id.image_result);
        mCameraLayout = view.findViewById(R.id.layout_camera);
        mPreviewLayout = view.findViewById(R.id.layout_preview);
        mCapture = view.findViewById(R.id.capture_bitmap);
        mClose = view.findViewById(R.id.close_preview);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mCapture.setOnClickListener(mBitmapCaptured);
        mClose.setOnClickListener(mClosePreview);
    }

    @Override
    public void onImageCaptured(File file) {}

    @Override
    public void onVideoCaptured(File file) {}

    @Override
    public void onStart() {
        super.onStart();
        if (mShowCamera) {
            showCamera();
        } else {
            showPreview();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mClassifier.close();
    }
}