package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created on 13/01/2017.
 */

public class BondStateReceiver extends BroadcastReceiver {

    private static final String TAG = BondStateReceiver.class.getSimpleName();
    private ConnectionManager mConn;

    public BondStateReceiver(Context context, ConnectionManager mC){
        mConn = mC;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
        Log.d(TAG, "Bond state change! device: " + device.getAddress() + "newstate: " + bondState + "prev: " + intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1));
        if(device.getAddress().equals(mConn.getDeviceAddress()) && bondState == 12) {
            context.unregisterReceiver(this);
            mConn.startServiceDiscovery();
        }
    }
}
