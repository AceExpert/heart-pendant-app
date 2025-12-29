package com.sayutel.heart.bluetooth;

import com.facebook.react.bridge.ReactApplicationContext;

public class NativeBluetoothModule extends NativeBluetoothSpec {

    public static final String NAME = "NativeBluetooth";

    ReactApplicationContext context;

    @Override
    public String getName() {
        return "NativeBluetooth";
    }

    public NativeBluetoothModule(ReactApplicationContext rCtx) {
        super(rCtx);
        context = rCtx;
    }

    @Override
    public void start() {

    }
}
