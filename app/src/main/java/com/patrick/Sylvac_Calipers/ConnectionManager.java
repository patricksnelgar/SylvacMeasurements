package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Manages connections between bluetooth mDiscoveredDevices. Also handles custom broadcast events
 */
public class ConnectionManager implements CommunicationCharacteristics{

    // TAG for logging purposes only
    private static final String TAG = ConnectionManager.class.getSimpleName();

    // Bluetooth objects used for connection
    private BluetoothAdapter mBluetoothAdpater;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;

    private int mConnectionState = STATE_DISCONNECTED;
    private boolean mConnected = false;

    private MainActivity parent;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


    private static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt mBtGatt, UUID charID){
        if(mBtGatt == null) {
            Log.w(TAG, "Bluetooth is null");
            return null;
        }
        BluetoothGattService mService = mBtGatt.getService(CommunicationCharacteristics.SERVICE_UUID);
        if(mService == null) {
            Log.w(TAG, "Service is null");
            return null;
        }
        BluetoothGattCharacteristic rxChar = mService.getCharacteristic(charID);

        if(rxChar != null)
            return rxChar;

        Log.w(TAG, "Characteristic not found: " + charID);
        return null;

    }


    public ConnectionManager(MainActivity pParent){
        this.parent = pParent;
        if(mBluetoothAdpater == null || mBluetoothManager == null)
            initializeBluetooth();
    }

    public boolean initializeBluetooth(){
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)this.parent.getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null){
                Log.e(TAG, "COULD NOT INITIALIZE BLUETOOTH MANAGER");
                return false;
            }
        }
        mBluetoothAdpater = mBluetoothManager.getAdapter();
        if(mBluetoothAdpater == null){
            Log.e(TAG, "COULD NOT GET BLUETOOTH ADAPTER");
            return false;
        }
        return true;
    }

    public boolean connect(final String mDeviceAddress){
        // Catch Bluetooth not init or invalid address
        if(mBluetoothAdpater == null ||  mDeviceAddress == null){
            Log.w(TAG, "Adapter or device address not set");
            return false;
        }

        // The requested connection has been seen before
        if(mBluetoothDeviceAddress != null && mDeviceAddress.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null){
            Log.d(TAG, "Trying existing connection");
            if(mBluetoothGatt.connect()){
                mConnectionState = STATE_CONNECTING;
                return true;
            } else return false;
        }

        // New connection requested
        final BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(mDeviceAddress);
        if(device == null){
            Log.d(TAG, "Device not found.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this.parent, false, mGattCallback);
        Log.d(TAG, "Trying to create new connection.");
        mBluetoothDeviceAddress = mDeviceAddress;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void disconnect() {
        if(mBluetoothAdpater == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter has not been initialized.");
            return;
        }

        mBluetoothGatt.disconnect();
    }

    public void close(){
        if(this.mBluetoothGatt == null) return;

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void broadcastUpdate(String intentAction){
        Log.i(TAG, "Broadcasting update: " + intentAction + " - " + mBluetoothDeviceAddress);
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(new Intent(intentAction).putExtra(ConnectFragment.DEVICE_ADDRESS, mBluetoothDeviceAddress));
    }

    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic characteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra(CommunicationCharacteristics.DEVICE_ADDRESS, mBluetoothDeviceAddress);
        if (TX_ANSWER_FROM_INSTRUMENT_UUID.equals(characteristic.getUuid()) || TX_RECEIVED_DATA_UUID.equals(characteristic.getUuid())) {
            //Have to do this as there is an issue with converting byte value 13 to a string
            final byte[] data = characteristic.getValue();
            final StringBuilder stBuilder = new StringBuilder(data.length);
            for (byte byteChar : data){
                stBuilder.append(String.format("%02X ", byteChar));
            }
            _intent.putExtra(ConnectFragment.EXTRA_DATA, new String(data) + " - " + stBuilder.toString());
        }
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(_intent);
    }

    public MainActivity getMainActivity() { return parent; }

    public void enableIndication(BluetoothGatt mBluetoothGatt){
        BluetoothGattCharacteristic rxChar = getCharacteristic(mBluetoothGatt, CommunicationCharacteristics.TX_RECEIVED_DATA_UUID);
        if(rxChar != null){
            mBluetoothGatt.setCharacteristicNotification(rxChar, true);
            BluetoothGattDescriptor mDescrip = rxChar.getDescriptor(CommunicationCharacteristics.CCCD_UUID);
            mDescrip.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            if(mBluetoothGatt.writeDescriptor(mDescrip)){
                Log.d(TAG, "Indication set!");
            } else {
                Log.d(TAG, "Indication NOT set");
            }
        }
    }

    public void enableNotification(BluetoothGatt mBluetoothGatt){
        BluetoothGattCharacteristic rxChar = getCharacteristic(mBluetoothGatt, CommunicationCharacteristics.TX_ANSWER_FROM_INSTRUMENT_UUID);
        if(rxChar != null){
            mBluetoothGatt.setCharacteristicNotification(rxChar, true);
            BluetoothGattDescriptor mDescrip = rxChar.getDescriptor(CommunicationCharacteristics.CCCD_UUID);
            mDescrip.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if(mBluetoothGatt.writeDescriptor(mDescrip)){
                Log.d(TAG, "Notification set");
            } else {
                Log.d(TAG, "Notification not set");
            }
        }
    }

    /**
     * BluetoothGattCallback
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        private String TAG = "BluetoothGattCallback";

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if(newState == BluetoothProfile.STATE_CONNECTED){
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:"+mBluetoothGatt.discoverServices());
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT Server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };
}
