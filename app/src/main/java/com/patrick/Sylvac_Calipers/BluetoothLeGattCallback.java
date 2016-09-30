package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Manages the change in communication and state of bluetooth devices
 */
public class BluetoothLeGattCallback extends BluetoothGattCallback {

    private static final String TAG = BluetoothLeGattCallback.class.getSimpleName();
    private static ConnectionManager mConnectionManager;

    public BluetoothLeGattCallback(ConnectionManager pConMan){
        this.mConnectionManager = pConMan;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.i(TAG, "State change: " + newState + " Status: " + status);
        String intentAction;
        if(newState == BluetoothProfile.STATE_CONNECTED) {
            intentAction = CommunicationCharacteristics.ACTION_GATT_CONNECTED;

            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connection successful, finalize");
                mConnectionManager.broadcastUpdate(ConnectFragment.GATT_CONNECTED);
                gatt.discoverServices();

            }

        } else if(newState == BluetoothProfile.STATE_DISCONNECTED){
            mConnectionManager.broadcastUpdate(ConnectFragment.GATT_DISCONNECTED);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic) {
        mConnectionManager.broadcastUpdate("Donnees transmises", bluetoothCharacteristic);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic, int status) {
       if(status == BluetoothGatt.GATT_SUCCESS)
            mConnectionManager.broadcastUpdate("Donnees transmises", bluetoothCharacteristic);
        Log.i(TAG, "Characteristic read: " + bluetoothCharacteristic.getUuid());
    }



    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if(status == 0){
            Log.i(TAG, "Notification actually active!");
            Intent _i = new Intent(CommunicationCharacteristics.DEVICE_BONDED);
            _i.putExtra(CommunicationCharacteristics.BT_DEVICE, gatt.getDevice());
            LocalBroadcastManager.getInstance(mConnectionManager.getMainActivity()).sendBroadcast(_i);
            return;
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);

    }
}
