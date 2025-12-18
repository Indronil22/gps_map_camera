package com.example.gpscamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

public class LocationHelper {

    public interface Callback {
        void onLocation(String address, double lat, double lng);
    }

    private final Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    public void getLocation(Callback callback) {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationServices.getFusedLocationProviderClient(context)
                .getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location == null) return;

                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    StringBuilder sb = new StringBuilder();

                    try {
                        Geocoder geo =
                                new Geocoder(context, Locale.getDefault());

                        List<Address> list =
                                geo.getFromLocation(lat, lng, 1);

                        if (!list.isEmpty()) {
                            Address a = list.get(0);
                            sb.append(a.getAddressLine(0));
                        }
                    } catch (Exception ignored) {}

                    callback.onLocation(
                            sb.toString(), lat, lng);
                });
    }
}
