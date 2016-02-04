package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

/**
 * Created by Patrick on 29/01/2016.
 */
public class CommunicationManager implements CommunicationCharacteristics{

    private static final String TAG = CommunicationManager.class.getSimpleName();

    public static void enableIndication(BluetoothGatt mBluetoothGatt){
        BluetoothGattService mBluetoothService = mBluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic mBluetoothCharacteristic = mBluetoothService.getCharacteristic(TX_RECEIVED_DATA_UUID);
        mBluetoothGatt.setCharacteristicNotification(mBluetoothCharacteristic, true);

        BluetoothGattDescriptor mDescriptor = mBluetoothCharacteristic.getDescriptor(CCCD_UUID);
        mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);

        if(mBluetoothGatt.writeDescriptor(mDescriptor)) Log.i(TAG, "Indication set");
    }

    public static void enableNotification(BluetoothGatt mBtGatt){
        BluetoothGattService mBtService = mBtGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic mBluetoothCharacteristic = mBtService.getCharacteristic(TX_ANSWER_FROM_INSTRUMENT_UUID);
        mBtGatt.setCharacteristicNotification(mBluetoothCharacteristic, true);

        BluetoothGattDescriptor mBluetoothDescriptor = mBluetoothCharacteristic.getDescriptor(CCCD_UUID);
        mBluetoothDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        if(mBtGatt.writeDescriptor(mBluetoothDescriptor)) Log.i(TAG, "Notification set");
    }
}
