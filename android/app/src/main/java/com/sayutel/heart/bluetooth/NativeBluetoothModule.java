package com.sayutel.heart.bluetooth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.facebook.react.bridge.ReactApplicationContext;

public class NativeBluetoothModule extends NativeBluetoothSpec {

    public static final String NAME = "NativeBluetooth";

    ReactApplicationContext context;

    HeartService heartService = null;
    HeartService.HeartServiceBinder heartServiceBinder = null;

    @Override
    public String getName() {
        return "NativeBluetooth";
    }

    public interface HeartEventListener {

    }

    public NativeBluetoothModule(ReactApplicationContext rCtx) {
        super(rCtx);
        context = rCtx;
        setNotificationChannels();
        context.startService(new Intent(context, HeartService.class));
        context.bindService(new Intent(context, HeartService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                heartServiceBinder = (HeartService.HeartServiceBinder) iBinder;
                heartService = heartServiceBinder.getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                heartServiceBinder = null;
                heartService = null;
            }
        }, ReactApplicationContext.BIND_AUTO_CREATE);
    }

    public void setNotificationChannels() {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("heart_service", "Heart Service", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setAllowBubbles(false);
        channel.setShowBadge(false);
        channel.setDescription("Indicates that your hearts are synced through the pendant.");
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void start() {

    }
}
