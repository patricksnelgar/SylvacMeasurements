package com.patrick.Sylvac_Calipers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created on 13/10/2016.
 */

public class DeviceScanFragment extends Fragment {

    private static final int REQUEST_ENABLE_BT = 15;
    private static final int REQUEST_LOCATION = 16;
    final String TAG = DeviceScanFragment.class.getSimpleName();

    private long SCAN_TIMEOUT = 10000;
    private Handler mHandler;
    private MainActivity mMainActivity;
    private SharedPreferences mSharedPreferences;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private DeviceListAdapter mDeviceListAdapter;
    private ConnectionManager mConn;

    private TextView mTextViewStatus;
    private ListView mListViewDiscoveredDevices;

    private ScanTimeout mScanTimeoutRunnable;

    private boolean mScanning = false;
    private boolean mLocationSettings = false;
    public ArrayList<BluetoothDevice> mDiscoveredDevices;

    class ScanTimeout implements Runnable {
        @Override
        public void run() {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            mBluetoothLeScanner.flushPendingScanResults(mLeScanCallback);
            setStatus("Status: Scanning timed out.");
            Log.d(TAG, "Scan timeout");
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Log.d("Scan Fragment", "SavedBundle: " + (savedInstanceState==null));

        View createView = inflater.inflate(R.layout.devicescan_fragment, container, false);;
        mTextViewStatus = (TextView) createView.findViewById(R.id.textViewStatus);
        mListViewDiscoveredDevices = (ListView) createView.findViewById(R.id.listViewDiscoveredDevices);
        mListViewDiscoveredDevices.setOnItemClickListener(mOnItemClickListener);
        return  createView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mHandler = new Handler();
        mDiscoveredDevices = new ArrayList<>();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mMainActivity);

        initializeBluetooth();

        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        mListViewDiscoveredDevices.setAdapter(mDeviceListAdapter);
        //setListAdapter(mDeviceListAdapter);

        mScanTimeoutRunnable = new ScanTimeout();

        mConn = new ConnectionManager(mMainActivity, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!mBluetoothAdapter.isEnabled()){
            Intent iEnableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(iEnableBluetooth, REQUEST_ENABLE_BT);
        }

        /*
        if( ((LocationManager) mMainActivity.getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)){
            mLocationSettings = true;
        } else {
            mLocationSettings = false;
        }
        */

        if(mBluetoothAdapter == null || mBluetoothLeScanner == null)
            initializeBluetooth();

        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        mListViewDiscoveredDevices.setAdapter(mDeviceListAdapter);
        scanForDevices(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        scanForDevices(false);
        mDeviceListAdapter.clear();
    }


    private void initializeBluetooth(){
        final BluetoothManager mManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if(mBluetoothAdapter == null) {
            Toast.makeText(mMainActivity, "Bluetooth not initialized", Toast.LENGTH_SHORT).show();
            mMainActivity.finish();
            return;
        }
    }

    public void scanForDevices(boolean scan) {
        mDeviceListAdapter.clear();

        if(mBluetoothLeScanner != null) {
            if (scan) {
                mHandler.postDelayed(mScanTimeoutRunnable, SCAN_TIMEOUT);
                setStatus("Status: Scanning...");
                Log.d(TAG, "Scanning...");
                mScanning = true;
                mBluetoothLeScanner.startScan(
                        new ArrayList(),
                        new ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build(),
                        mLeScanCallback);
            } else {
                mScanning = false;
                mBluetoothLeScanner.stopScan(mLeScanCallback);
                setStatus("Status: Scanning stopped.");
                Log.d(TAG, "Scan stopped.");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            mMainActivity.finish();
            return;
        }
        /*
        if (requestCode == REQUEST_LOCATION && resultCode == Activity.RESULT_CANCELED){
            Toast.makeText(mMainActivity, "App requires Location for scan results.", Toast.LENGTH_SHORT).show();
            mLocationSettings = false;
            mMainActivity.finish();
            return;
        } else if(requestCode == REQUEST_LOCATION && resultCode == Activity.RESULT_OK) {
            if( ((LocationManager)mMainActivity.getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationSettings = true;
            }
        }*/

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void setParent(MainActivity m) { mMainActivity = m; }

    public void setStatus(String mStatus) {
        mTextViewStatus.setText(mStatus);
    }


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
            if (d.getName() == null) return;
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
    // Triggered whenever a device is discovered by the BLE Scanner
    private ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            //Log.d(TAG, "Device found!");
            mMainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDeviceListAdapter.addDevice(device);
                    mDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice mTargetDevice = mDeviceListAdapter.getDevice(position);
            final String mTargetDeviceAddress = mTargetDevice.getAddress();
            setStatus("Connecting to: " + mTargetDevice.getName());
            mHandler.removeCallbacks(mScanTimeoutRunnable);
            mConn.setDeviceAddress(mTargetDeviceAddress);
            boolean connectResult = mConn.connect();
            //mConn = new ConnectionManager(mMainActivity, mTargetDeviceAddress);
            //Log.d(TAG, "ConnectionManager:: " + mConn.hashCode());
        }
    };
}
