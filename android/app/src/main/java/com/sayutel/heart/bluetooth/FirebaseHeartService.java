package com.sayutel.heart.bluetooth;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Objects;

public class FirebaseHeartService extends FirebaseMessagingService {

    HeartService.HeartServiceBinder heartBinder = null;
    HeartService heartService = null;

    public FirebaseHeartService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.bindService(new Intent(this, HeartService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                heartBinder = (HeartService.HeartServiceBinder) iBinder;
                heartService = heartBinder.getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {

            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        if(!message.getData().isEmpty()) {
            String action = message.getData().get("action");
            if(Objects.equals(action, "1")) {
                heartBinder.startSyncingHeart();
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i("FCM New Token", token);
        heartBinder.newFCMToken(token);
    }
}