package com.patrick.Sylvac_Calipers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Patrick on 27/01/2016.
 */
public class BluetoothReceiver extends BroadcastReceiver {

    private static final String TAG = BluetoothReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String extra = (String)intent.getCharSequenceExtra("DEVICE_ADDRESS");
        Log.i(TAG, "Action received: " + action + " from: " + extra);
        switch (action){
            case "Donnees transmises":
                String extraData = new String(intent.getByteArrayExtra("EXTRA_DATA"));
                String canal = (String)intent.getCharSequenceExtra("NUM_CANAL");
                Log.i(TAG, "Extra data: " + extraData + " Canal: " + canal);
                break;
            default:
                break;
        }

        /*
        switch (action){
            case ConnectFragment.DATA_AVAILABLE:
                String extraData = new String(intent.getByteArrayExtra("EXTRA_DATA"));
                String canal = (String)intent.getCharSequenceExtra("NUM_CANAL");
                Log.i(TAG, "Extra data: " + extraData + " Canal: " + canal);
                break;
            case ConnectFragment.GATT_CONNECTED:
                Log.i(TAG, "Device Connected!");
                break;
            case ConnectFragment.GATT_DISCONNECTED:
                Log.i(TAG, "Instrument disconnected");
                break;
            case ConnectFragment.GATT_SERVICES_DISCOVERED:
                Log.i(TAG, "Services discovered!");
                break;
            case ConnectFragment.CONNECTION_COMPLETE:
                Log.i(TAG, "CONNECTION_COMPLETE");
                break;
            default:
                Log.w(TAG, "Action not handled: " + action);
                break;
        }
        */
    }
}
