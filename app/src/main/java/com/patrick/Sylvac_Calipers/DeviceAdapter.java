package com.patrick.Sylvac_Calipers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Adapter for each bluetooth device to be shown in the ConnectFragment
 */
public class DeviceAdapter extends ArrayAdapter<DiscoveredDevice> {

    public DeviceAdapter(Context context, ArrayList<DiscoveredDevice> devices){
        super(context, 0, devices);
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DiscoveredDevice mDevice = getItem(position);

        if(convertView == null){
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.bluetooth_device, parent, false);
        }

        TextView mName = (TextView) convertView.findViewById(R.id.textDeviceName);
        TextView mAddress = (TextView) convertView.findViewById(R.id.textDeviceAddress);
        CheckBox mBonded = (CheckBox) convertView.findViewById(R.id.bondedDevice);

        mName.setText(mDevice.name);
        mAddress.setText(mDevice.address);
        mBonded.setChecked(mDevice.bondedDevice);

        // TODO: add in configuration for using custom icons in list display based off device id

        return convertView;
    }
}
