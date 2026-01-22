package com.inout.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Helper class to fetch current GPS location and calculate distances.
 * Strictly uses FusedLocationProvider for accuracy.
 * DOES NOT use Google Maps API.
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;

    public interface LocationResultCallback {
        void onLocationResult(Location location);
        void onError(String errorMsg);
    }

    public LocationHelper(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Fetches the single most recent accurate location.
     */
    @SuppressLint("MissingPermission") // Permissions are checked before calling this
    public void getCurrentLocation(final LocationResultCallback callback) {
        if (!hasPermissions()) {
            callback.onError("Location permissions not granted.");
            return;
        }

        // Try getting the last known location first for speed
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                // If the last location is fresh enough (e.g., < 10 seconds), use it.
                // For this app, to ensure they are actually at the office, we prefer a fresh update.
                // So we will request a fresh update regardless to prevent spoofing with old data.
                requestFreshLocation(callback);
            } else {
                requestFreshLocation(callback);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get last location", e);
            requestFreshLocation(callback);
        });
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation(final LocationResultCallback callback) {
        // High accuracy request to ensure they are within the 100m radius
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(true)
                .setMaxUpdates(1)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    callback.onLocationResult(location);
                } else {
                    callback.onError("Failed to fetch current location.");
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    /**
     * Calculates the distance in meters between two coordinates.
     * Uses the Android Location.distanceBetween method (Haversine formula internally).
     *
     * @return Distance in meters.
     */
    public static float calculateDistance(double startLat, double startLng, double endLat, double endLng) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLng, endLat, endLng, results);
        return results[0];
    }

    /**
     * Checks if the distance is within the allowed radius.
     */
    public static boolean isWithinRadius(double currentLat, double currentLng, double targetLat, double targetLng, float radiusMeters) {
        float distance = calculateDistance(currentLat, currentLng, targetLat, targetLng);
        Log.d(TAG, "Distance to target: " + distance + " meters. Allowed: " + radiusMeters);
        return distance <= radiusMeters;
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}