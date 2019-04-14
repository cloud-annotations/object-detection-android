package com.example.android.tflitecamerademo;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
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
import com.xlythe.view.camera.PermissionChecker;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

public class CustomCameraFragment extends Fragment {
    private static final String[] REQUIRED_PERMISSIONS;
    private static final String[] OPTIONAL_PERMISSIONS;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    static {
        // In KitKat+, WRITE_EXTERNAL_STORAGE is optional
        if (Build.VERSION.SDK_INT >= 19) {
            REQUIRED_PERMISSIONS = new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };
            OPTIONAL_PERMISSIONS = new String[] {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.VIBRATE
            };
        } else {
            REQUIRED_PERMISSIONS = new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            OPTIONAL_PERMISSIONS = new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.VIBRATE
            };
        }
    }

    private ImageClassifier mClassifier;

    private CameraView mCamera;
    private ImageView mPreview;
    private ViewGroup mCameraLayout;
    private ViewGroup mPreviewLayout;
    private View mPermissionPrompt;
    private View mPermissionRequest;
    private Button mCapture;
    private Button mClose;

    private boolean mShowCamera;

    private View.OnClickListener mBitmapCaptured = (view) -> {
        try {
            // Reflection.
            Field field = mCamera.getClass().getDeclaredField("mCameraView");
            field.setAccessible(true);
            TextureView textureView = (TextureView) field.get(mCamera);

            Matrix matrix = textureView.getTransform(null);
            float[] values = new float[9];
            matrix.getValues(values);
            int newWidth =  textureView.getWidth() - (int)values[2] * 2;
            int newHeight =  textureView.getHeight() - (int)values[5] * 2;

            // Get the image and classify it.
            Bitmap bitmap = textureView.getBitmap(newWidth, newHeight);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, ImageClassifier.DIM_IMG_SIZE_X,  ImageClassifier.DIM_IMG_SIZE_Y, false);
            List<Prediction> predictions = mClassifier.classifyFrame(scaled);

            float dpi = getResources().getDisplayMetrics().density;

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColor(Color.rgb(36,101,255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            paint.setStrokeWidth(dpi * 3);

            Display display = view.getDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;

            for (Prediction prediction : predictions) {
                Log.d("CameraFrag", prediction.label + ": " + prediction.score);
                canvas.drawRoundRect(prediction.bbox.x * canvas.getWidth(),
                        prediction.bbox.y * canvas.getHeight(),
                        (prediction.bbox.x + prediction.bbox.width) * canvas.getWidth(),
                        (prediction.bbox.y + prediction.bbox.height) * canvas.getHeight(),
                        dpi * 6, dpi * 6,
                        paint);
            }
            mPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mPreview.setImageBitmap(bitmap);
            showPreview();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    };

    private View.OnClickListener mClosePreview = (view) -> showCamera();

    private View.OnClickListener mPermissionListener = (view) -> requestPermissions(
            concat(REQUIRED_PERMISSIONS, OPTIONAL_PERMISSIONS), REQUEST_CODE_PERMISSIONS
    );

    private void showPreview() {
        mShowCamera = false;
        mCameraLayout.setVisibility(View.GONE);
        mPreviewLayout.setVisibility(View.VISIBLE);
    }

    private void showCamera() {
        mShowCamera = true;
        mCameraLayout.setVisibility(View.VISIBLE);
        mPreviewLayout.setVisibility(View.GONE);
        mPermissionPrompt.setVisibility(View.GONE);
    }

    private void showPermissionPrompt() {
        mCameraLayout.setVisibility(View.GONE);
        mPreviewLayout.setVisibility(View.GONE);
        mPermissionPrompt.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (PermissionChecker.hasPermissions(getContext(), REQUIRED_PERMISSIONS)) {
                showCamera();
            } else {
                showPermissionPrompt();
            }
        }
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
        mPermissionPrompt = view.findViewById(R.id.layout_permissions);
        mPermissionRequest = view.findViewById(R.id.request_permissions);

        mCamera.open();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCapture.setOnClickListener(mBitmapCaptured);
        mClose.setOnClickListener(mClosePreview);
        mPermissionRequest.setOnClickListener(mPermissionListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (PermissionChecker.hasPermissions(getContext(), REQUIRED_PERMISSIONS)) {
            if (mShowCamera) {
                showCamera();
            } else {
                showPreview();
            }
        } else {
            showPermissionPrompt();
        }
    }

    @Override
    public void onStop() {
        mCamera.close();
        mClassifier.close();
        super.onStop();
    }

    private static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}