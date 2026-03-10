package com.sayutel.heart.bluetooth;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sayutel.heartpendant.R;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public class HeartService extends Service {

    String[] fixedAddress = {};
    String pendantAddress = null;

    Pendant pendantDevice = new Pendant();

    BluetoothManager manager;
    BluetoothAdapter adapter;

    BluetoothGatt gattClient = null;
    BluetoothGattCharacteristic mainCharac = null;
    BluetoothGattDescriptor mainDescr = null;

    NotificationManager notificationManager;

    public class MPendant {
        public static final String device_secret = "5V2LVUexNoh4d2t";
        public static final String device_id = "n7xqu7PT35";
        public static final byte[] device_addr = new byte[]{(byte) 0x9c, 0x13, (byte) 0x9e, (byte) 0xab, (byte) 0xde, 0x12};
        public static final String dev_addr = "9C:13:9E:AB:DE:12";
    }

    public class SPendant {
        public static final String device_secret = "gHSvtEV8keKTwHK";
        public static final String device_id = "EiRvXJHfKU";
        public static final byte[] device_addr = new byte[]{(byte) 0x9c, 0x13, (byte) 0x9e, (byte) 0xab, (byte) 0xde, (byte) 0xee};
        public static final String dev_addr = "9C:13:9E:AB:DE:EE";
    }

    public class Pendant {
        public boolean shaleen_p = false;
        public String device_id = "";
        public String device_secret = "";
        public byte[] address = new byte[6];
        public String firebase_token = "";
        public boolean isSyncing = false;
        public PendantUDPChannel udp = new PendantUDPChannel();
        public boolean connected = false;

        public class PendantUDPChannel {
            public DatagramChannel channel = null;
            public Selector selector = null;
            public LocalDateTime last_msg_time = null;
            public LocalDateTime last_sent_msg_time = null;
            public LocalDateTime last_ack_time = null;

            public LinkedList<String> sendQueue = new LinkedList<>();
            public String lastWrite = null;
            public LocalDateTime last_write_time = null;
        }
    }

    HeartServiceBinder serviceBinder = new HeartServiceBinder();

    public class HeartServiceBinder extends Binder {
        public HeartService getService() {
            return HeartService.this;
        }

        public void newFCMToken(String token) {
            syncFCMToken(token);
        }

        public void startSyncingHeart() {
            Log.i("sock sync", "sync heart");
            openUDPChannel();
        }
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(Objects.equals(intent.getAction(), BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if(adapter.isEnabled()) {
                    pendantConnect();
                }
            }
        }
    };

    public class TCPHeartClientTask extends Thread {
        @Override
        public void run() {
            try {
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(true);
                boolean connected = socketChannel.connect(new InetSocketAddress("209.74.79.245", 5660));
                if(connected) {
                    Log.i("socket", "conn");
                    int secret_len = pendantDevice.device_secret.length();
                    int fb_tok_len = pendantDevice.firebase_token.length();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 2 + 6 + 2 + secret_len);
                    byteBuffer.put(new byte[]{0, 1, 0});
                    byteBuffer.put(pendantDevice.address);
                    byteBuffer.put(new byte[]{(byte) (secret_len & 0xff), (byte) ((secret_len >> 8) & 0xff)});
                    for(byte ch : pendantDevice.device_secret.getBytes()) {
                        byteBuffer.put(ch);
                    };
                    byteBuffer.rewind();
                    int written = socketChannel.write(byteBuffer);
                    Log.i("socket", String.valueOf(written));
                    byteBuffer = ByteBuffer.allocate(1 + 2 + 2 + fb_tok_len);
                    byteBuffer.put(new byte[]{1, 1, 0});
                    byteBuffer.put(new byte[]{(byte) (fb_tok_len & 0xff), (byte) ((fb_tok_len >> 8) & 0xff)});
                    for(byte ch : pendantDevice.firebase_token.getBytes()) {
                        byteBuffer.put(ch);
                    };
                    byteBuffer.rewind();
                    written = socketChannel.write(byteBuffer);
                    Log.i("socket", String.valueOf(written));
                    socketChannel.close();
                }
            } catch (IOException e) {
                Log.i("socket err", e.getLocalizedMessage());
            }
        }
    }

    public class UDPHeartClientTask extends Thread {

        public void process_cmd(String cmd) {
            if(!cmd.equals("ack")) {
                Log.i("sock cmd", cmd);
                Log.i("sock cmd len", String.valueOf(cmd.length()));
                pendantDevice.udp.last_msg_time = LocalDateTime.now();
            }

            if(cmd.equals("hb")) {
                sendPendantCmd(".beat");
            } else if (cmd.equals("tap")) {
                sendPendantCmd(".tap");
            } else if (cmd.equals("bz")) {
                sendPendantCmd(".long");
            } else if (cmd.equals("bzs")) {
                sendPendantCmd(".long_stop");
            } else if (cmd.equals("ack")) {
                pendantDevice.udp.last_ack_time = LocalDateTime.now();
            }
        }

        @Override
        public void run() {
            try {
                while(true) {
                    if(!pendantDevice.isSyncing) {
                        break;
                    }
                    if(pendantDevice.udp.last_msg_time != null) {
                        if(pendantDevice.udp.last_msg_time.until(LocalDateTime.now(), ChronoUnit.SECONDS) > 900 && (pendantDevice.udp.last_sent_msg_time == null || pendantDevice.udp.last_sent_msg_time.until(LocalDateTime.now(), ChronoUnit.SECONDS) > 300)) {
                            pendantDevice.isSyncing = false;
                            closeUDPChannel();
                            break;
                        }
                    }
                    if(pendantDevice.udp.last_ack_time == null || pendantDevice.udp.last_ack_time.until(LocalDateTime.now(), ChronoUnit.SECONDS) > 20) {
                        sendUDPCmd("sync");
                    }
                    if(pendantDevice.udp.selector.select(7000) > 0) {
                        Log.i("sock", "selecting");
                        ByteBuffer buffer = ByteBuffer.allocate(512);
                        SocketAddress addr = pendantDevice.udp.channel.receive(buffer);
                        buffer.rewind();
                        String cmd = "";
                        while (buffer.hasRemaining()) {
                            byte chr = buffer.get();
                            if(chr == 0) break;
                            cmd += new String(new byte[]{chr}, StandardCharsets.UTF_8);
                        }
                        process_cmd(cmd);
                    }
                    pendantDevice.udp.selector.selectedKeys().clear();
                }
            } catch (IOException e) {

            }
        }
    }

    public void openUDPChannel() {
        try {
            if(pendantDevice.udp.channel == null) {
                pendantDevice.isSyncing = true;
                pendantDevice.udp.channel = DatagramChannel.open();
                pendantDevice.udp.channel.configureBlocking(false);
                pendantDevice.udp.selector = Selector.open();
                pendantDevice.udp.channel.register(pendantDevice.udp.selector, SelectionKey.OP_READ);
                sendUDPCmd("sync");
                sendUDPCmd("sync");
                (new UDPHeartClientTask()).start();
            } else {
                sendUDPCmd("sync");
                sendUDPCmd("sync");
            }
        } catch (IOException e) {

        }
    }

    public void closeUDPChannel() {
        try {
            if(pendantDevice.udp.channel != null) {
                pendantDevice.isSyncing = false;
                pendantDevice.udp.channel.close();
                pendantDevice.udp.selector.close();
                pendantDevice.udp.channel = null;
                pendantDevice.udp.selector = null;
            }
        } catch (IOException e) {

        }
    }

    public void sendUDPCmd(String cmd) {
        try {
            pendantDevice.udp.last_sent_msg_time = LocalDateTime.now();
            pendantDevice.udp.channel.send(getUDPCmd(cmd), new InetSocketAddress("209.74.79.245", 5600));
            pendantDevice.udp.last_write_time = LocalDateTime.now();
        } catch (IOException e) {

        }
    }

    public void sendUDPCmd(ByteBuffer cmd) {
        try {
            pendantDevice.udp.channel.send(cmd, new InetSocketAddress("209.74.79.245", 5600));
            pendantDevice.udp.last_write_time = LocalDateTime.now();
        } catch (IOException e) {

        }
    }

    public ByteBuffer getUDPCmd(String cmd) {
        return ByteBuffer.wrap((pendantDevice.device_id + ":" + cmd).getBytes());
    }

    public ByteBuffer getUDPCmd(String device_id, String cmd) {
        return ByteBuffer.wrap((device_id + ":" + cmd).getBytes());
    }

    public HeartService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = getSystemService(BluetoothManager.class);
        adapter = manager.getAdapter();
        notificationManager = getSystemService(NotificationManager.class);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification serviceNotif = new NotificationCompat.Builder(this, "heart_service")
                .setOngoing(true)
                .setContentTitle("Your hearts are synced")
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentText("Making sure your hearts are always connected")
                .build();
        startForeground(6, serviceNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        if(adapter.isEnabled()) initBleClient();
        //showFCMToken();
        return START_STICKY;
    }

    public void syncFCMToken(String token) {
        pendantDevice.firebase_token = token;
        (new TCPHeartClientTask()).start();
    }

    public void showFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if(task.isSuccessful()) {
                    String tok = task.getResult();
                    Log.i("FCM show token", tok);
                    syncFCMToken(tok);
                }
            }
        });
    }

    public void initBleClient() {
        CompanionDeviceManager companionDeviceManager = (CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE);
        for(AssociationInfo info : companionDeviceManager.getMyAssociations()) {
            pendantAddress = info.getDeviceMacAddress().toString().toUpperCase();
            int i = 0;
            for(byte b : info.getDeviceMacAddress().toByteArray()) {
                pendantDevice.address[i++] = b;
            }
            break;
        }

        if(pendantAddress != null) {
            pendantConnect();
        } else {
            AssociationRequest request = new AssociationRequest.Builder()
                    .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                    .addDeviceFilter(new BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile("^.+'s Heart$", Pattern.CASE_INSENSITIVE))
                            .build()
                    ).build();

            Executor executor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };

            companionDeviceManager.associate(request, executor, new CompanionDeviceManager.Callback() {

                @Override
                public void onAssociationPending(@NonNull IntentSender intentSender) {
                    super.onAssociationPending(intentSender);
                    try {
                        intentSender.sendIntent(HeartService.this, 1, null, new IntentSender.OnFinished() {
                            @Override
                            public void onSendFinished(IntentSender IntentSender, Intent intent, int resultCode, String resultData, Bundle resultExtras) {

                            }
                        }, null);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e("CDM error", e.toString());
                    }
                }

                @Override
                public void onAssociationCreated(@NonNull AssociationInfo associationInfo) {
                    int i = 0;
                    for(byte b : associationInfo.getDeviceMacAddress().toByteArray()) {
                        pendantDevice.address[i++] = b;
                    }
                    pendantAddress = associationInfo.getDeviceMacAddress().toString().toUpperCase();
                    pendantConnect();
                }

                @Override
                public void onFailure(@Nullable CharSequence charSequence) {
                    Log.i("assoc", "fail " + charSequence.toString());
                }
            });
        }

    }

    public BluetoothGattCharacteristic getMainCharac() {
        mainCharac = gattClient.getServices().get(2).getCharacteristics().get(0);
        return mainCharac;
    }

    public BluetoothGattDescriptor getMainDescr() {
        mainDescr = getMainCharac().getDescriptors().get(0);
        return mainDescr;
    }

    public void sendPendantCmd(String cmd) {
        if(pendantDevice.connected) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            int resp = gattClient.writeCharacteristic(getMainCharac(), cmd.getBytes(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            if(resp != 0) {

            }
        }
    }

    public void pendantConnect() {
        if(Objects.equals(pendantAddress, MPendant.dev_addr)) {
            pendantDevice.device_id = MPendant.device_id;
            pendantDevice.device_secret = MPendant.device_secret;
            pendantDevice.shaleen_p = false;
        } else if (Objects.equals(pendantAddress, SPendant.dev_addr)) {
            pendantDevice.device_id = SPendant.device_id;
            pendantDevice.device_secret = SPendant.device_secret;
            pendantDevice.shaleen_p = true;
        }

        showFCMToken();
        BluetoothDevice device = adapter.getRemoteDevice(pendantAddress);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        gattClient = device.connectGatt(this, false, new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if(newState == 2) {
                    gatt.requestMtu(200);
                } else {
                    pendantDevice.connected = false;
                    //closeUDPChannel();
                    if(adapter.isEnabled()) {
                        gatt.connect();
                    } else {

                    }
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                gatt.discoverServices();
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                /*for(BluetoothGattService service : gatt.getServices()) {
                    Log.i("ble service", service.getUuid().toString());
                    for(BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.i("ble charac", characteristic.getUuid().toString());
                        Log.i("ble charac perm", String.valueOf(characteristic.getPermissions()));
                        for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            Log.i("ble descript", descriptor.getUuid().toString());
                            Log.i("ble descript perm", String.valueOf(descriptor.getPermissions()));
                        }
                    }
                }*/
                gatt.setCharacteristicNotification(getMainCharac(), true);
                gatt.writeDescriptor(getMainDescr(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                pendantDevice.connected = true;
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
                super.onCharacteristicChanged(gatt, characteristic, value);
                openUDPChannel();
                String s_val = new String(value, StandardCharsets.UTF_8);
                if(s_val.equals(".beat")) {
                    sendUDPCmd("hb");
                } else if(s_val.equals(".tap")) {
                    sendUDPCmd("tap");
                } else if(s_val.equals(".long")) {
                    sendUDPCmd("bz");
                } else if(s_val.equals(".long_stop")) {
                    sendUDPCmd("bzs");
                }
            }
        });
    }
}