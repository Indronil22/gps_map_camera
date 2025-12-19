package com.example.gpscamera;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

public class ImageUtil {

    public static void shareImage(Context context, File imageFile) {

        if (imageFile == null || !imageFile.exists()) return;

        Uri uri = FileProvider.getUriForFile(
                context,
                "com.example.gpscamera.provider",
                imageFile
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(
                Intent.createChooser(shareIntent, "Share image via"));
    }
}
