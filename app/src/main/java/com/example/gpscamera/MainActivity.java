package com.example.gpscamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
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

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView imagePreview;
    private MaterialButton btnCapture, btnSwitch, btnShare, btnRetake;

    private ImageCapture imageCapture;
    private boolean isBackCamera = true;

    private LocationHelper locationHelper;
    private File capturedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        imagePreview = findViewById(R.id.imagePreview);

        btnCapture = findViewById(R.id.btnCapture);
        btnSwitch = findViewById(R.id.btnSwitch);
        btnShare = findViewById(R.id.btnShare);
        btnRetake = findViewById(R.id.btnRetake);

        locationHelper = new LocationHelper(this);

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    101
            );
        } else {
            startCamera();
        }

        btnCapture.setOnClickListener(v -> takePhoto());

        btnSwitch.setOnClickListener(v -> {
            isBackCamera = !isBackCamera;
            startCamera();
        });

        btnShare.setOnClickListener(v -> {
            if (capturedFile != null) {
                ImageUtil.shareImage(this, capturedFile);
            }
        });

        btnRetake.setOnClickListener(v -> resetToCamera());
    }

    // ---------------- CAMERA ----------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                CameraSelector selector = isBackCamera
                        ? CameraSelector.DEFAULT_BACK_CAMERA
                        : CameraSelector.DEFAULT_FRONT_CAMERA;

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                provider.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        imageCapture
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ---------------- CAPTURE (FINAL IMAGE FIRST) ----------------

    private void takePhoto() {
        if (imageCapture == null) return;

        File dir = new File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "GPSCamera"
        );
        if (!dir.exists()) dir.mkdirs();

        String fileName = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
        ).format(new Date()) + ".jpg";

        capturedFile = new File(dir, fileName);

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(capturedFile).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults output) {

                        // 🔥 ALL PROCESSING FIRST
                        new Thread(() -> {

                            // 1️⃣ Fix mirror BEFORE preview
                            if (!isBackCamera) {
                                fixFrontCameraMirror(capturedFile);
                            }

                            // 2️⃣ Location + watermark + map
                            locationHelper.fetchLocation((address, lat, lng) -> {

                                drawTextAndMapOnImage(
                                        capturedFile,
                                        address,
                                        lat,
                                        lng
                                );

                                // 3️⃣ Save final image to gallery
                                saveImageToGallery(capturedFile);

                                // 4️⃣ NOW show preview (FINAL IMAGE)
                                runOnUiThread(() -> {
                                    imagePreview.setImageURI(
                                            Uri.fromFile(capturedFile)
                                    );
                                    previewView.setVisibility(View.GONE);
                                    imagePreview.setVisibility(View.VISIBLE);

                                    btnShare.setVisibility(View.VISIBLE);
                                    btnRetake.setVisibility(View.VISIBLE);
                                });
                            });

                        }).start();
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException e) {
                        Toast.makeText(
                                MainActivity.this,
                                "Capture failed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    // ---------------- WATERMARK + MAP ----------------

    private void drawTextAndMapOnImage(
            File imageFile,
            String address,
            double lat,
            double lng
    ) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath())
                    .copy(Bitmap.Config.ARGB_8888, true);

            Canvas canvas = new Canvas(bitmap);

            float textSize = bitmap.getWidth() * 0.035f;
            float lineHeight = textSize + 12f;
            int padding = 40;

            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(textSize);
            textPaint.setAntiAlias(true);

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.argb(170, 0, 0, 0));

            String time = new SimpleDateFormat(
                    "dd MMM yyyy | hh:mm a",
                    Locale.getDefault()
            ).format(new Date());

            List<String> lines = new ArrayList<>();
            lines.addAll(breakTextIntoLines(
                    address,
                    textPaint,
                    bitmap.getWidth() - padding * 2
            ));
            lines.add("Lat: " + lat + "  Lng: " + lng);
            lines.add(time);

            float boxHeight = lines.size() * lineHeight + padding;
            float startY = bitmap.getHeight() - boxHeight;

            canvas.drawRect(
                    0,
                    startY,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    bgPaint
            );

            float y = startY + padding + textSize;
            for (String line : lines) {
                canvas.drawText(line, padding, y, textPaint);
                y += lineHeight;
            }

            Bitmap mapBitmap = downloadStaticMap(lat, lng);
            if (mapBitmap != null) {
                int mapSize = (int) (bitmap.getWidth() * 0.28f);
                Bitmap scaledMap = Bitmap.createScaledBitmap(
                        mapBitmap,
                        mapSize,
                        mapSize,
                        true
                );

                float left = bitmap.getWidth() - mapSize - padding;
                float top = startY - mapSize - 20;

                Paint mapBg = new Paint();
                mapBg.setColor(Color.WHITE);
                canvas.drawRoundRect(
                        new RectF(
                                left - 6,
                                top - 6,
                                left + mapSize + 6,
                                top + mapSize + 6
                        ),
                        16,
                        16,
                        mapBg
                );

                canvas.drawBitmap(scaledMap, left, top, null);
            }

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();

        } catch (Exception ignored) {}
    }

    // ---------------- HELPERS ----------------

    private Bitmap downloadStaticMap(double lat, double lng) {
        try {
            String url =
                    "https://maps.googleapis.com/maps/api/staticmap" +
                            "?center=" + lat + "," + lng +
                            "&zoom=16&size=400x400" +
                            "&markers=color:red|" + lat + "," + lng +
                            "&key=YOUR_GOOGLE_MAPS_API_KEY";

            InputStream is = new URL(url).openStream();
            return BitmapFactory.decodeStream(is);

        } catch (Exception e) {
            return null;
        }
    }

    private List<String> breakTextIntoLines(
            String text,
            Paint paint,
            int maxWidth
    ) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String test = line + word + " ";
            if (paint.measureText(test) <= maxWidth) {
                line.append(word).append(" ");
            } else {
                lines.add(line.toString().trim());
                line = new StringBuilder(word + " ");
            }
        }
        if (line.length() > 0) lines.add(line.toString().trim());
        return lines;
    }

    private void saveImageToGallery(File file) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + "/GPSCamera"
            );

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) return;

            OutputStream out = getContentResolver().openOutputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();

        } catch (Exception ignored) {}
    }

    private void fixFrontCameraMirror(File file) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) return;

            Matrix matrix = new Matrix();
            matrix.preScale(-1, 1);

            Bitmap flipped = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

            FileOutputStream fos = new FileOutputStream(file);
            flipped.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();

        } catch (Exception ignored) {}
    }

    private void resetToCamera() {
        imagePreview.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.GONE);
        btnRetake.setVisibility(View.GONE);
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
