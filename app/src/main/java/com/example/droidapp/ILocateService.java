package com.example.droidapp;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.Manifest;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.List;

/*
 * GPS Location Tracking Services
 * */
public class ILocateService extends Service {
    public static interface Actions {
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
    private LocationRequest locationRequest;

    public static final String[] permissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };
    public static final String[] actionFilters = new String[]{
            Actions.ON_MOTION_DETECTED,
            Actions.ON_MOTION_ENDED
    };
    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    @SuppressLint("MissingPermission")
    public void onCreate() {
        if(!locationPermissionGranted) return;
        Log.i(TAG, "locations mounted " + locationPermissionGranted);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(MIN_TIME_BW_UPDATES)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE_FOR_UPDATES)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                List<Location> locations = locationResult.getLocations();
                Location curr = locations.get(0);
                if(lastKnownLocation==null) lastKnownLocation = curr;
                Double[] lkv = new Double[]{curr.getLatitude(), curr.getLongitude(), Double.valueOf(curr.getTime())};
                boolean isMoving = curr.hasSpeed();
                if(isMoving) chunkVectors(lkv);
                String msg = !isMoving ? "Location is same" : curr.getSpeed()+"Location changed: " + curr.getLatitude() + ", " + curr.getLongitude();
//                Log.i(TAG, msg);
                Intent locAction = new Intent(Actions.ON_MOTION_DETECTED);
                locAction.putExtra("data", msg);
                LocalBroadcastManager.getInstance(binder).sendBroadcast(locAction);
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        binder = this;
    }

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
