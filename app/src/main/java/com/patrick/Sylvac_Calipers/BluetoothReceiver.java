package com.patrick.Sylvac_Calipers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Handles the data coming from the calipers, then passes it on to the DataReceiver
 */
public class BluetoothReceiver extends BroadcastReceiver {

    private static final String TAG = BluetoothReceiver.class.getSimpleName();
    private ConnectionManager mManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String address = (String)intent.getCharSequenceExtra("DEVICE_ADDRESS");
        Log.i(TAG, "Action received: " + action + " from: " + address);
        if(action.equals(CommunicationCharacteristics.ACTION_DATA_AVAILABLE)){
            String s = new String(intent.getByteArrayExtra(CommunicationCharacteristics.EXTRA_DATA));
            String canal = (String) intent.getCharSequenceExtra(CommunicationCharacteristics.NUM_CANAL);
            Log.i(TAG, "Data: " + s);
            Intent _i = new Intent(RecordFragment.MEASUREMENT_RECEIVED);
            _i.putExtra(CommunicationCharacteristics.DATA_VALUE, s);
            LocalBroadcastManager.getInstance(mManager.getMainActivity()).sendBroadcast(_i);
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

    public void setmManager(ConnectionManager m){
        this.mManager = m;
    }
}
