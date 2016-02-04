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
            case "Connexion reussi":
                Log.i(TAG, "Device Connected!");
                break;
            case "Deconnexion reussi ou inattendue":
                Log.i(TAG, "Instrument disconnected");
                break;
            case "Services decouverts":
                Log.i(TAG, "Services discovered!");
            default:
                break;
        }
    }
}
