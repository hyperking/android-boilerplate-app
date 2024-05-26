package com.example.droidapp;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

/*
 * GPS Location Tracking Services
 * */
public class ILocateService extends Service {
    public static interface Actions {
        String ON_LOCATION_REQUESTED = "ON_LOCATION_REQUESTED";
        String ON_MOTION_DETECTED = "ON_MOTION_DETECTED";
        String ON_MOTION_ENDED = "ON_MOTION_ENDED";
    }

    public static final String TAG = "ILocateService";
    public static final String MODEL = "[lat, lng, time]";
    public static ILocateService binder;
    private static final int MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // meters
    private static final long MIN_TIME_BW_UPDATES = 1000; // milliseconds
    public static final int LOCATION_PERMISSION_CODE = 888;
    public static int vectorMax = 20;
    public static boolean locationPermissionGranted;
    public static boolean trackMovement;
    public static Location lastKnownLocation;
    public static List<Double[]> lastKnowVectors = new ArrayList<>();
    private LocationCallback locationCallback;

    public static final String[] permissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };
    public static final String[] actionFilters = new String[]{
            Actions.ON_MOTION_DETECTED,
            Actions.ON_MOTION_ENDED,
            Actions.ON_LOCATION_REQUESTED
    };
    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    @SuppressLint("MissingPermission")
    public void onCreate() {
        if (!locationPermissionGranted) return;
        Log.i(TAG, "locations mounted " + locationPermissionGranted);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(MIN_TIME_BW_UPDATES)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE_FOR_UPDATES)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                List<Location> locations = locationResult.getLocations();
                Location curr = locations.get(0);
                if (lastKnownLocation == null) lastKnownLocation = curr;
                Double[] lkv = getLastKnownLocation(curr);
                boolean isMoving = curr.hasSpeed();
                if (isMoving) chunkVectors(lkv);
                String msg = !isMoving ? "Location is same" : curr.getSpeed() + "Location changed: " + curr.getLatitude() + ", " + curr.getLongitude();
                Intent locAction = new Intent(Actions.ON_MOTION_DETECTED);
                locAction.putExtra("data", msg);
                LocalBroadcastManager.getInstance(binder).sendBroadcast(locAction);
            }
        };
        if(trackMovement){
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        }
        binder = this;
    }

    @SuppressLint("MissingPermission")
    public void getLocation() {

        fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            lastKnownLocation = location;
                            Log.d("GPS", "Latitude: " + location.getLatitude() + ", Longitude: " + location.getLongitude());
                            LocalBroadcastManager.getInstance(binder).sendBroadcast(new Intent(Actions.ON_LOCATION_REQUESTED));
                            // Use the location object as needed
                        } else {
                            Log.w("GPS", "Failed to get location.");
                        }
                    }
                });
    }

    public static Double[] getLastKnownLocation(Location location){
      return new Double[]{lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), Double.valueOf(lastKnownLocation.getTime())};
    };
    public void chunkVectors(Double[] vector){
        if(lastKnowVectors.size()>vectorMax){
            lastKnowVectors.clear();
        }
        lastKnowVectors.add(vector);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "DESTROYING ILOCATE");
        super.onDestroy();
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        binder = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
