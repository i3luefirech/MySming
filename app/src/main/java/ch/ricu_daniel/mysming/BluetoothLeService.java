/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.ricu_daniel.mysming;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private Handler mHandler = null;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_INITMEASURE = 3;
    private static final int STATE_MEASURING = 4;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_MEASURE_MEASURING =
            "com.example.bluetooth.le.ACTION_MEASURE_MEASURING";
    public final static String ACTION_MEASURE_INITMEASURE =
            "com.example.bluetooth.le.ACTION_MEASURE_INITMEASURE";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITTEN =
            "com.example.bluetooth.le.ACTION_DATA_WRITTEN";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);
    public final static UUID LSM330_SERVICE           =
            UUID.fromString(SampleGattAttributes.LSM330_SERVICE);
    public final static UUID LSM330_CHAR_ACC_EN       =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_ACC_EN);
    public final static UUID LSM330_CHAR_GYRO_EN      =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_GYRO_EN);
    public final static UUID LSM330_CHAR_TEMP_SAMPLE  =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_TEMP_SAMPLE);
    public final static UUID LSM330_CHAR_ACC_FSCALE   =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_ACC_FSCALE);
    public final static UUID LSM330_CHAR_GYRO_FSCALE  =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_GYRO_FSCALE);
    public final static UUID LSM330_CHAR_ACC_ODR      =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_ACC_ODR);
    public final static UUID LSM330_CHAR_GYRO_ODR     =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_GYRO_ODR);
    public final static UUID LSM330_CHAR_TRIGGER_VAL  =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_TRIGGER_VAL);
    public final static UUID LSM330_CHAR_TRIGGER_AXIS =
            UUID.fromString(SampleGattAttributes.LSM330_CHAR_TRIGGER_AXIS);

    public final static UUID MEASURE_SERVICE         =
            UUID.fromString(SampleGattAttributes.MEASURE_SERVICE);
    public final static UUID MEASURE_CHAR_START      =
            UUID.fromString(SampleGattAttributes.MEASURE_CHAR_START);
    public final static UUID MEASURE_CHAR_STOP       =
            UUID.fromString(SampleGattAttributes.MEASURE_CHAR_STOP);
    public final static UUID MEASURE_CHAR_DURATION   =
            UUID.fromString(SampleGattAttributes.MEASURE_CHAR_DURATION);
    public final static UUID MEASURE_CHAR_DATASTREAM =
            UUID.fromString(SampleGattAttributes.MEASURE_CHAR_DATASTREAM);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_WRITTEN, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else if(LSM330_CHAR_TEMP_SAMPLE.equals(characteristic.getUuid())) {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(EXTRA_DATA,String.format("%d", data[0]));
            }
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }
    public void setCharacteristicNotification(UUID serviceUuid, UUID charUuid, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattCharacteristic chara = mBluetoothGatt
                .getService(serviceUuid)
                .getCharacteristic(charUuid);

        // Enable notification internally.
        if (!mBluetoothGatt.setCharacteristicNotification(chara, enabled)) {
            Log.w(TAG, "setCharacteristicNotification failed");
            return;
        }

        // Enable notification remotely.
        BluetoothGattDescriptor clientConfig = chara.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        if (clientConfig == null) {
            return;
        }

        if (enabled) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        mBluetoothGatt.writeDescriptor(clientConfig);

        return;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void startMeasurement()
    {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                BluetoothGattCharacteristic characteristic;
                switch (msg.what) {
                    case 0:
                        mConnectionState = STATE_MEASURING;
                        refreshTemp();
                        break;
                    case 1:
                        mConnectionState = STATE_INITMEASURE;
                        beginMeasurement();
                        break;
                    case 2:
                        mConnectionState = STATE_CONNECTED;
                        endMeasurement();
                        break;
                }
            }
        };
        Message mymsg = new Message();
        mymsg.obj = null;
        mymsg.what = 1;
        mHandler.sendMessageDelayed(mymsg, 100);
    }

    public void stopMeasurement()
    {
        Message mymsg = new Message();
        mymsg.obj = null;
        mymsg.what = 2;
        mHandler.sendMessageDelayed(mymsg, 100);
    }

    private int mState = 0;

    public void reset(){mState = 0;};
    public void advance(){
        mState++;
        if(mConnectionState==STATE_INITMEASURE)
        {
            Message mymsg = new Message();
            mymsg.obj = null;
            mymsg.what = 1;
            mHandler.sendMessageDelayed(mymsg, 500);
        }
        else if(mConnectionState==STATE_MEASURING)
        {
            Message mymsg = new Message();
            mymsg.obj = null;
            mymsg.what = 0;
            mHandler.sendMessageDelayed(mymsg, 500);
        }
        else if(mConnectionState==STATE_CONNECTED)
        {
            Message mymsg = new Message();
            mymsg.obj = null;
            mymsg.what = 2;
            mHandler.sendMessageDelayed(mymsg, 500);
        }
    };

    public void refreshTemp() {
        BluetoothGattCharacteristic characteristic = null;
        switch(mState) {
            case 0:
                String intentAction = ACTION_MEASURE_MEASURING;
                broadcastUpdate(intentAction);
                advance();
                break;
            case 1:
                characteristic = mBluetoothGatt.getService(LSM330_SERVICE)
                        .getCharacteristic(LSM330_CHAR_TEMP_SAMPLE);
                readCharacteristic(characteristic);
                break;
        }
    }

    public void beginMeasurement()
    {
        BluetoothGattCharacteristic characteristic = null;
        switch(mState) {
            case 0:
                String intentAction = ACTION_MEASURE_INITMEASURE;
                broadcastUpdate(intentAction);
                advance();
                break;
            case 1:
                characteristic = mBluetoothGatt.getService(LSM330_SERVICE)
                        .getCharacteristic(LSM330_CHAR_ACC_EN);
                characteristic.setValue(new byte[]{0x01});
                writeCharacteristic(characteristic);
                break;
            case 2:
                characteristic = mBluetoothGatt.getService(LSM330_SERVICE)
                        .getCharacteristic(LSM330_CHAR_GYRO_EN);
                characteristic.setValue(new byte[]{0x01});
                writeCharacteristic(characteristic);
                break;
            case 3:
                characteristic = mBluetoothGatt.getService(MEASURE_SERVICE)
                        .getCharacteristic(MEASURE_CHAR_START);
                characteristic.setValue(new byte[]{0x01});
                writeCharacteristic(characteristic);
                break;
            case 4:
                mState=0;
                Message mymsg = new Message();
                mymsg.obj = null;
                mymsg.what = 0;
                mHandler.sendMessageDelayed(mymsg, 500);
                break;
        }
    }

    public void endMeasurement()
    {
        BluetoothGattCharacteristic characteristic = null;
        switch(mState) {
            case 2:
                characteristic = mBluetoothGatt.getService(LSM330_SERVICE)
                        .getCharacteristic(LSM330_CHAR_ACC_EN);
                characteristic.setValue(new byte[]{0x00});
                writeCharacteristic(characteristic);
                break;
            case 1:
                characteristic = mBluetoothGatt.getService(LSM330_SERVICE)
                        .getCharacteristic(LSM330_CHAR_GYRO_EN);
                characteristic.setValue(new byte[]{0x00});
                writeCharacteristic(characteristic);
                break;
            case 0:
                characteristic = mBluetoothGatt.getService(MEASURE_SERVICE)
                        .getCharacteristic(MEASURE_CHAR_STOP);
                characteristic.setValue(new byte[]{0x01});
                writeCharacteristic(characteristic);
                break;
            case 3:
                String intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                mState=0;
                mHandler = null;
                break;
        }
    }
}
