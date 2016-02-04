package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Log;

/**
 * Created by Patrick on 27/01/2016.
 */
public class BluetoothLeGattCallback extends BluetoothGattCallback {

    private static final String TAG = BluetoothLeGattCallback.class.getSimpleName();
    private static ConnectionManager mConnectionManager;

    public BluetoothLeGattCallback(ConnectionManager pConMan){
        this.mConnectionManager = pConMan;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic) {
        Log.i(TAG, "Characteristic changed");
        mConnectionManager.broadcastUpdate("Donnees transmises", bluetoothCharacteristic);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic bluetoothCharacteristic, int status) {
        Log.i(TAG, "Reading data");
        if(status == 0)
            mConnectionManager.broadcastUpdate("Donnees transmises", bluetoothCharacteristic);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        ConnectionState lConnectionState = mConnectionManager.getConnectionState();
        if(newState == 2) {
            mConnectionManager.removeTimeout();
            if (status == 0) {
                Log.i(TAG, "Connection successful, finalize");
                mConnectionManager.startServiceDiscovery();

            }
        }

        //state disconnected
        if(newState == 0) mConnectionManager.setConnectionState(ConnectionState.NOT_CONNECTED);
        /*
        do{
            if(lConnectionState == ConnectionState.CONNECTING){
                Log.i(TAG, "Trying to connect... status: " + status);
                mConnectionManager.redoConnection(ConnectionState.NOT_CONNECTED);
                return;
            }
            Log.i(TAG, "Trying to connect... status: " + status);
            mConnectionManager.redoConnection(ConnectionState.UNDESIRED_CONNECTION);

            if(newState != 0){
                break;
            }

            Log.i(TAG, "Disconnection successful or unexpected");
            mConnectionManager.broadcastUpdate("Disconnection successful or unexpected");
        } while (lConnectionState == ConnectionState.DESIRED_DISCONNECTION);

        Log.i(TAG, "Undesired disconnection from: " + gatt.getDevice().getAddress());
        mConnectionManager.redoConnection(ConnectionState.UNDESIRED_DISCONNECTION);
        return;
        */
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if(status == 0){
            Log.i(TAG, "Notification active!");
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
        mConnectionManager.setConnectionState(ConnectionState.CONNECTED);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        // status  = 0 when the device has been explored successfully
        //if(status != 0)
        //    mConnectionManager.serviceDiscovered(true);
        if(status == 0){
            mConnectionManager.registerToServices();
        }
    }
}
