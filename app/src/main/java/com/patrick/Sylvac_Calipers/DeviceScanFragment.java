package com.patrick.Sylvac_Calipers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by hrtwps on 13/10/2016.
 */

public class DeviceScanFragment extends ListFragment {

    private static final int REQUEST_ENABLE_BT = 1;
    final String TAG = DeviceScanFragment.class.getSimpleName();

    private long SCAN_TIMEOUT = 10000;
    private Handler mHandler;
    private MainActivity mParent;
    private SharedPreferences mSharedPreferences;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private DeviceListAdapter mDeviceListAdapter;

    private ConnectionManager mConn;

    private boolean mScanning = false;
    public ArrayList<BluetoothDevice> mDiscoveredDevices;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return  inflater.inflate(R.layout.devicescan_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mHandler = new Handler();
        mDiscoveredDevices = new ArrayList<>();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mParent);

        final BluetoothManager mManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if(mBluetoothAdapter == null) {
            Toast.makeText(mParent, "Bluetooth not initialized", Toast.LENGTH_SHORT).show();
            mParent.finish();
            return;
        }

        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        setListAdapter(mDeviceListAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!mBluetoothAdapter.isEnabled()){
            Intent iEnableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(iEnableBluetooth, REQUEST_ENABLE_BT);
        }

        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        setListAdapter(mDeviceListAdapter);
        checkBondedDevices();
        scanForDevices(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        scanForDevices(false);
        mDeviceListAdapter.clear();
    }

    private void checkBondedDevices(){
        for(BluetoothDevice mBondedDevice : mBluetoothAdapter.getBondedDevices()){
            if(mSharedPreferences.getBoolean(MainActivity.PREFERENCE_ONLY_SYLVAC, false)) {
                if(mBondedDevice.getName().startsWith("SY")){
                    mDeviceListAdapter.addDevice(mBondedDevice);
                }
            } else {
                mDeviceListAdapter.addDevice(mBondedDevice);
            }
        }

        mDeviceListAdapter.notifyDataSetChanged();
    }

    public void scanForDevices(boolean scan) {
        mDeviceListAdapter.clear();
        checkBondedDevices();
        if(mBluetoothLeScanner != null) {
            if (scan) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        mBluetoothLeScanner.stopScan(mLeScanCallback);
                        mBluetoothLeScanner.flushPendingScanResults(mLeScanCallback);
                        Log.d(TAG, "Scan timeout");
                    }
                }, SCAN_TIMEOUT);
                Log.d(TAG, "Scanning...");
                mScanning = true;
                mBluetoothLeScanner.startScan(mLeScanCallback);
            } else {
                mScanning = false;
                mBluetoothLeScanner.stopScan(mLeScanCallback);
                Log.d(TAG, "Scan stopped.");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            mParent.finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        BluetoothDevice mTargetDevice = mDeviceListAdapter.getDevice(position);
        String mTargetDeviceAddress = mTargetDevice.getAddress();
        mConn.connect(mTargetDeviceAddress);
        //Log.d(TAG, "Clicked device: " + d.getName());
    }

    public void setParent(MainActivity m) { mParent = m; }

    public void setConnectionMan(ConnectionManager man) { mConn = man; }

    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mDevices;
        private Context mContext;

        public DeviceListAdapter(Context context, ArrayList<BluetoothDevice> devices) {
            mDevices = devices;
            mContext = context;
        }

        public void clear(){
            mDevices.clear();
        }

        public void addDevice(BluetoothDevice d){
            boolean deviceExists = false;
            for (BluetoothDevice mDevice : mDevices){
                if(mDevice.getAddress().equals(d.getAddress()))
                    deviceExists = true;
            }
            if(!deviceExists ) {
                if (mSharedPreferences.getBoolean(MainActivity.PREFERENCE_ONLY_SYLVAC, false)) {
                    if (d.getName().startsWith("SY")) {
                        mDevices.add(d);
                    }
                } else {
                    mDevices.add(d);
                }
            }
        }

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public Object getItem(int position) {
            return mDevices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public BluetoothDevice getDevice(int position) {
            return mDevices.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vHolder;

            if(convertView == null) {
                LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(R.layout.bluetooth_device, null);
                vHolder = new ViewHolder();
                vHolder.textAddress = (TextView) convertView.findViewById(R.id.textDeviceAddress);
                vHolder.textName = (TextView) convertView.findViewById(R.id.textDeviceName);
                convertView.setTag(vHolder);
            } else {
                vHolder = (ViewHolder) convertView.getTag();
            }

            BluetoothDevice device = mDevices.get(position);
            final String deviceName = device.getName();
            TextView tName = (TextView) convertView.findViewById(R.id.textDeviceName);
            TextView tAddress = (TextView) convertView.findViewById(R.id.textDeviceAddress);
            if(deviceName != null && deviceName.length() > 0){
                tName.setText(deviceName);
            } else {
                tName.setText("Unknown device");
            }
            tAddress.setText(device.getAddress());

            return convertView;
        }
    }

    static class ViewHolder {
        TextView textName;
        TextView textAddress;
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            //Log.d(TAG, "Device found!");
            mParent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDeviceListAdapter.addDevice(device);
                    mDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };
}
