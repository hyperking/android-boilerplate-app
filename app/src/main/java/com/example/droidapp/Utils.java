package com.example.droidapp;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.gson.Gson;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Utils {
    public static Intent mainIntent = new Intent();
    public static final Gson gson = new Gson();

    public static IntentFilter genIntentFilter(String[][] actions) {
        IntentFilter intentFilter = new IntentFilter();
        for (String[] acts : actions) {
            for (String act : acts) {
                intentFilter.addAction(act);
            }
        }
        return intentFilter;
    }

    public static String[] genActions(Object oactions, String prefix) {
        String[] actions = oactions instanceof List ? ((List<?>) oactions).toArray(new String[0]) : (String[])oactions;
        prefix = prefix == null ? "" : prefix;
        List<String> res = new ArrayList<>();
        for (int i = 0; i < actions.length; i++) {
            String act = prefix + actions[i];
            res.add(act);
            if (!prefix.equals("")) {
                res.add(act + "_GRANTED");
                res.add(act + "_DENIED");
            }
        }
        return res.toArray(new String[0]);
    }

    public static void requestAppPermissions(Activity activity, String[] permissions, int code) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity, permissions, code);
        }
    }

    public static String[] checkPermissions(String[] permissions, Context ctx) {
        List<String> needPerms = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            String perm = permissions[i];
            if (checkPermission(perm, ctx)) continue;
            needPerms.add(perm);
        }
        return needPerms.toArray(new String[0]);
    }

    public static boolean checkPermission(String permission, Context ctx){
        return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static String formatDate(long postTime) {
        // Create a SimpleDateFormat object with the desired format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd@HH:mm:ss", Locale.getDefault());
        // Convert the postTime to Date object
        Date date = new Date(postTime);
        // Format the date to a string
        return dateFormat.format(date);
    }

    public static List<String> getFieldNames(Class<?> interfaceClass) {
        List<String> fieldList = new ArrayList<>();
        Field[] fields = interfaceClass.getDeclaredFields();
        for (Field field : fields) {
            fieldList.add(field.getName());
        }
        return fieldList;
    }

    public static void postDataToServer(Context context, HashMap<String, Object> deviceData, String url) {

        // Stringify ServerData as JSON
        String jsonData = gson.toJson(deviceData);

        // Create OkHttpClient instance
        OkHttpClient client = new OkHttpClient();

        // Create JSON request body
        RequestBody requestBody = RequestBody.create(jsonData,
                MediaType.parse("application/json; charset=utf-8"));

        // Create POST request
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                String msg = "Server Has Failed: " + url;
                mainIntent.setAction("ON_SERVER_FAIL");
                mainIntent.putExtra("data", msg);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String msg = "Failed to post data to server: " + response.message();
                    mainIntent.setAction("ON_SERVER_BAD");
                    mainIntent.putExtra("data", msg);
                } else {
                    String jsonString = response.body().string();
                    mainIntent.setAction("ON_SERVER_OK");
                    mainIntent.putExtra("data", jsonString);
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(mainIntent);
                response.close();
            }
        });
    }
}
