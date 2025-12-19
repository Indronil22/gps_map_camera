package com.example.gpscamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

public class LocationHelper {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;

    public LocationHelper(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    // Callback interface
    public interface LocationResult {
        void onResult(String address, double latitude, double longitude);
    }

    // Fetch location safely
    public void fetchLocation(LocationResult callback) {

        // Permission check
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            callback.onResult(
                    "Location permission not granted",
                    0.0,
                    0.0
            );
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    // Location may be null
                    if (location == null) {
                        callback.onResult(
                                "Location unavailable",
                                0.0,
                                0.0
                        );
                        return;
                    }

                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    String addressText = "Address not found";

                    try {
                        Geocoder geocoder =
                                new Geocoder(context, Locale.getDefault());

                        List<Address> addresses =
                                geocoder.getFromLocation(lat, lng, 1);

                        if (addresses != null && !addresses.isEmpty()) {
                            addressText = addresses.get(0).getAddressLine(0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    callback.onResult(addressText, lat, lng);
                })
                .addOnFailureListener(e -> callback.onResult(
                        "Location error",
                        0.0,
                        0.0
                ));
    }
}
