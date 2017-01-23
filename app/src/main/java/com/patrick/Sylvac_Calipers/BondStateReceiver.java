package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Author:      Patrick Snelgar
 * Name:        BondStateReceiver
 * Description: A receiver that listens for the ACTION_BOND_STATE_CHANGED intent in order to establish when a successful
 *              bond has been made with a device.
 */

public class BondStateReceiver extends BroadcastReceiver {

    private static final String TAG = BondStateReceiver.class.getSimpleName();
    private ConnectionManager mConn;

    public BondStateReceiver(Context context, ConnectionManager mC){
        mConn = mC;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // BluetoothDevice that the bond state change occured on is included in the Intent
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
        Log.d(TAG, "Bond state change! device: " +
                device.getAddress() + "newstate: " +
                bondState + "prev: " +
                intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1));

        /**
         * Check to see if the bond state change occurs on the relevant device,
         * then check if the bond state is equal to BOND_BONDED (12)
         */
        if(device.getAddress().equals(mConn.getDeviceAddress()) && bondState == BluetoothDevice.BOND_BONDED) {
            // Don't need to listen to the bond state changes for this device anymore
            context.unregisterReceiver(this);
            // Connection is established, can now discover services.
            mConn.startServiceDiscovery();
        }
    }
}
