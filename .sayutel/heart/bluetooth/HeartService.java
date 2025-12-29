package com.sayutel.heart.bluetooth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class HeartService extends Service {

    String[] address = {};

    public HeartService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void initBleClient() {
        
    }
}