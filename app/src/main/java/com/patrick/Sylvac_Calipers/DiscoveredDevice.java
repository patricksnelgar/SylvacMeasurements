package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

/**
 * Created by Patrick on 27/01/2016.
 */
public class DiscoveredDevice {

    private static final String TAG = DiscoveredDevice.class.getSimpleName();

    public String name;
    public boolean bondedDevice;
    public String icon;
    public String address;
    public BluetoothDevice btDevice;

    public DiscoveredDevice(String name, boolean bonded, String icon, String address, BluetoothDevice device){
        this.name = name;
        this.bondedDevice = bonded;
        this.icon = icon;
        this.address = address;
        this.btDevice = device;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof DiscoveredDevice){
            DiscoveredDevice a = (DiscoveredDevice) o;
            if(this.address.equals(a.address)) {
                Log.i(TAG, "Address match");
                return true;
            }
        }

        return false;
    }
}
