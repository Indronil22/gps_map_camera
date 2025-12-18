package com.example.gpscamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ = 100;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private LocationHelper locationHelper;

    private CameraSelector cameraSelector =
            CameraSelector.DEFAULT_BACK_CAMERA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        Button btnCapture = findViewById(R.id.btnCapture);
        Button btnSwitch = findViewById(R.id.btnSwitch);

        locationHelper = new LocationHelper(this);

        if (hasPermissions()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQ
            );
        }

        btnCapture.setOnClickListener(v -> takePhoto());

        btnSwitch.setOnClickListener(v -> {
            cameraSelector =
                    (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                            ? CameraSelector.DEFAULT_FRONT_CAMERA
                            : CameraSelector.DEFAULT_BACK_CAMERA;
            startCamera();
        });
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(
                                getWindowManager()
                                        .getDefaultDisplay()
                                        .getRotation())
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        locationHelper.getLocation((address, lat, lng) -> {

            String time =
                    new SimpleDateFormat(
                            "dd MMM yyyy | hh:mm a",
                            Locale.getDefault())
                            .format(new Date());

            String stampText =
                    address
                            + "\n\nLat: " + lat
                            + "\nLng: " + lng
                            + "\n" + time;

            ContentValues values = new ContentValues();
            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "GPS_" + System.currentTimeMillis() + ".jpg");
            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg");
            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/GPS Camera");

            ImageCapture.OutputFileOptions options =
                    new ImageCapture.OutputFileOptions.Builder(
                            getContentResolver(),
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                    ).build();

            imageCapture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {

                        @Override
                        public void onImageSaved(
                                @NonNull ImageCapture.OutputFileResults output) {

                            Uri uri = output.getSavedUri();

                            if (cameraSelector ==
                                    CameraSelector.DEFAULT_FRONT_CAMERA) {
                                ImageUtil.fixFrontMirror(
                                        MainActivity.this, uri);
                            }

                            ImageUtil.stampPhoto(
                                    MainActivity.this,
                                    uri,
                                    stampText,
                                    lat,
                                    lng
                            );

                            Toast.makeText(
                                    MainActivity.this,
                                    "Photo saved to Gallery",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        @Override
                        public void onError(
                                @NonNull ImageCaptureException e) {
                            e.printStackTrace();
                        }
                    }
            );
        });
    }
}
