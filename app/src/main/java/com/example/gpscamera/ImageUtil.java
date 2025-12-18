package com.example.gpscamera;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ImageUtil {

    // ================= FRONT CAMERA MIRROR FIX =================
    public static void fixFrontMirror(Context context, Uri uri) {
        try {
            Bitmap bmp = MediaStore.Images.Media
                    .getBitmap(context.getContentResolver(), uri);

            Matrix matrix = new Matrix();
            matrix.preScale(-1, 1);

            Bitmap fixed = Bitmap.createBitmap(
                    bmp,
                    0,
                    0,
                    bmp.getWidth(),
                    bmp.getHeight(),
                    matrix,
                    true
            );

            OutputStream out =
                    context.getContentResolver()
                            .openOutputStream(uri);

            fixed.compress(Bitmap.CompressFormat.JPEG, 95, out);
            out.close();

        } catch (Exception ignored) {}
    }

    // ================= MAIN STAMP METHOD =================
    public static void stampPhoto(
            Context context,
            Uri uri,
            String text,
            double lat,
            double lng
    ) {
        try {
            Bitmap bmp = MediaStore.Images.Media
                    .getBitmap(context.getContentResolver(), uri);

            Bitmap mutable =
                    bmp.copy(Bitmap.Config.ARGB_8888, true);

            Canvas canvas = new Canvas(mutable);

            // ---------- Dynamic sizing ----------
            int padding = mutable.getWidth() / 60;
            int textSize = mutable.getWidth() / 28;
            int lineGap = textSize + (textSize / 4);

            // ---------- Paints ----------
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(textSize);

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.argb(128, 0, 0, 0)); // 50% opacity

            // ---------- Text wrapping ----------
            int maxTextWidth =
                    mutable.getWidth() - (mutable.getWidth() / 3);

            List<String> lines =
                    wrapText(text, textPaint, maxTextWidth);

            int boxHeight =
                    (lines.size() * lineGap) + padding * 2;

            int startY =
                    mutable.getHeight() - boxHeight - padding;

            // ---------- Background box ----------
            canvas.drawRect(
                    padding,
                    startY,
                    mutable.getWidth() - padding,
                    mutable.getHeight() - padding,
                    bgPaint
            );

            // ---------- Draw text ----------
            int y = startY + padding + textSize;
            for (String line : lines) {
                canvas.drawText(
                        line,
                        padding * 2,
                        y,
                        textPaint
                );
                y += lineGap;
            }

            // ---------- Google Map ----------
            Bitmap map = getMap(lat, lng);
            if (map != null) {
                int mapSize = mutable.getWidth() / 4;
                Bitmap smallMap =
                        Bitmap.createScaledBitmap(
                                map,
                                mapSize,
                                mapSize,
                                true
                        );

                canvas.drawBitmap(
                        smallMap,
                        mutable.getWidth() - mapSize - padding,
                        mutable.getHeight() - mapSize - padding,
                        null
                );
            }

            OutputStream out =
                    context.getContentResolver()
                            .openOutputStream(uri);

            mutable.compress(Bitmap.CompressFormat.JPEG, 95, out);
            out.close();

        } catch (Exception ignored) {}
    }

    // ================= TEXT WRAPPING =================
    private static List<String> wrapText(
            String text,
            Paint paint,
            int maxWidth
    ) {
        List<String> lines = new ArrayList<>();

        for (String paragraph : text.split("\n")) {

            StringBuilder line = new StringBuilder();
            String[] words = paragraph.split(" ");

            for (String word : words) {

                if (paint.measureText(
                        line + word + " ") <= maxWidth) {
                    line.append(word).append(" ");
                } else {
                    lines.add(line.toString().trim());
                    line = new StringBuilder(word + " ");
                }
            }

            if (line.length() > 0) {
                lines.add(line.toString().trim());
            }
        }

        return lines;
    }

    // ================= STATIC MAP =================
    private static Bitmap getMap(double lat, double lng) {
        try {
            String url =
                    "https://maps.googleapis.com/maps/api/staticmap"
                            + "?center=" + lat + "," + lng
                            + "&zoom=17"
                            + "&size=400x400"
                            + "&markers=color:red|" + lat + "," + lng;

            InputStream is = new URL(url).openStream();
            return BitmapFactory.decodeStream(is);

        } catch (Exception e) {
            return null;
        }
    }
}
