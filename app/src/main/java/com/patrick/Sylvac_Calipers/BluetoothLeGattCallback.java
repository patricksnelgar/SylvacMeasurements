package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by Patrick on 27/01/2016.
 */
public class BluetoothLeGattCallback extends BluetoothGattCallback {

    private static final String TAG = BluetoothLeGattCallback.class.getSimpleName();
    private static ConnectionManager mConnectionManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public BluetoothLeGattCallback(ConnectionManager pConMan){
        this.mConnectionManager = pConMan;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic) {
        final byte[] data = bluetoothCharacteristic.getValue();
        Intent _i = new Intent(RecordFragment.MEASUREMENT_RECEIVED);
        _i.putExtra("NUM_VALUE", data);
        LocalBroadcastManager.getInstance(mConnectionManager.getMainActivity()).sendBroadcast(_i);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic, int status) {
       if(status == BluetoothGatt.GATT_SUCCESS)
            mConnectionManager.broadcastUpdate("Donnees transmises", bluetoothCharacteristic);
        Log.i(TAG, "Characteristic read: " + bluetoothCharacteristic.getUuid());
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.i(TAG,"status: "+status+" newState: "+newState );
        if(newState == BluetoothProfile.STATE_CONNECTED) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connection successful, finalize");
                mConnectionManager.broadcastUpdate(ConnectFragment.GATT_CONNECTED);
                gatt.discoverServices();
                Log.i(TAG, "Bond = " + gatt.getDevice().getBondState());
            }

        } else if(newState == BluetoothProfile.STATE_DISCONNECTED){
            mConnectionManager.broadcastUpdate(ConnectFragment.GATT_DISCONNECTED);
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if(status == 0){
            Log.i(TAG, "Notification actually active!");
            mConnectionManager.broadcastUpdate(ConnectFragment.CONNECTION_COMPLETE);
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);

    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        mConnectionManager.broadcastUpdate(ConnectFragment.GATT_SERVICES_DISCOVERED);
        mConnectionManager.enableNotification(gatt);
        mConnectionManager.enableIndication(gatt);
    }
}
