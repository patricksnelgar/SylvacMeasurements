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
 * Author:      Patrick Snelgar
 * Name:        DeviceScanFragment
 * Description: Uses the BluetoothLeScanner to scan for any Bluetooth devices in proximity to the local device.
 */

public class DeviceScanFragment extends Fragment {

    private static final int REQUEST_ENABLE_BT = 15;
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
    public ArrayList<BluetoothDevice> mDiscoveredDevices;

    /**
     * A runnable that stops the scan when called.
     */
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

    /**
     * Creates the view for the fragment.
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Log.d("Scan Fragment", "SavedBundle: " + (savedInstanceState==null));

        View createView = inflater.inflate(R.layout.devicescan_fragment, container, false);;
        mTextViewStatus = (TextView) createView.findViewById(R.id.textViewStatus);
        mListViewDiscoveredDevices = (ListView) createView.findViewById(R.id.listViewDiscoveredDevices);
        mListViewDiscoveredDevices.setOnItemClickListener(mOnItemClickListener);
        return  createView;
    }

    /**
     * Called once the parent activity is created, this is important for the use of some variables and devices.
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mHandler = new Handler();
        mDiscoveredDevices = new ArrayList<>();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mMainActivity);

        initializeBluetooth();

        // Create and set the custom adapter for displaying the discovered devices.
        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        mListViewDiscoveredDevices.setAdapter(mDeviceListAdapter);

        mScanTimeoutRunnable = new ScanTimeout();

        mConn = new ConnectionManager(mMainActivity, null);
    }

    /**
     * Called when the fragment enters the foreground again, checks the BluetoothAdapter before
     * starting to scan again.
     */
    @Override
    public void onResume() {
        super.onResume();
        if(!mBluetoothAdapter.isEnabled()){
            Intent iEnableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(iEnableBluetooth, REQUEST_ENABLE_BT);
        }

        if(mBluetoothAdapter == null || mBluetoothLeScanner == null)
            initializeBluetooth();

        mDeviceListAdapter = new DeviceListAdapter(getActivity(), mDiscoveredDevices);
        mListViewDiscoveredDevices.setAdapter(mDeviceListAdapter);
        scanForDevices(true);
    }

    /**
     * Called when the fragment is removed from the stack, stops the scan and clears the list of discoverd devices.
     */
    @Override
    public void onPause() {
        super.onPause();
        scanForDevices(false);
        mDeviceListAdapter.clear();

    }

    /**
     * Sets up the required objects to perform a BluetoothLeScan
     */
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

    /**
     * Starts a BluetoothLeScan for nearby Bluetooth devices.
     * Also starts the scan timeout runnable.
     * @param scan
     */
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
                mHandler.removeCallbacks(mScanTimeoutRunnable);
                setStatus("Status: Scanning stopped.");
                Log.d(TAG, "Scan stopped.");
            }
        }
    }

    /**
     * User may be asked to turn bluetooth on, if they choose not to the
     * application will exit.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            mMainActivity.finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void setParent(MainActivity m) { mMainActivity = m; }

    public void setStatus(String mStatus) {
        mTextViewStatus.setText(mStatus);
    }

    /**
     * Custom List Adapter to display the discovered devices in a list view using
     * custom xml layout.
     */
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

        /**
         * Adds a device to the list if it has not been seen before
         * @param d
         */
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

        /**
         * Function to handle setting the fields in the XML layout for the list view.
         * @param position
         * @param convertView
         * @param parent
         * @return
         */
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

    /**
     * Placeholder class to represent a single discovered bluetooth device.
     */
    static class ViewHolder {
        TextView textName;
        TextView textAddress;
    }

    /**
     * Device scan callback.
     * Triggered whenever a device is discovered by the BLE Scanner
     */
    private ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            mMainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDeviceListAdapter.addDevice(device);
                    mDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    /**
     * Initiates a connection with the selected device
     */
    final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice mTargetDevice = mDeviceListAdapter.getDevice(position);
            final String mTargetDeviceAddress = mTargetDevice.getAddress();
            setStatus("Connecting to: " + mTargetDevice.getName());
            mHandler.removeCallbacks(mScanTimeoutRunnable);
            mScanTimeoutRunnable = new ScanTimeout();
            mConn.setDeviceAddress(mTargetDeviceAddress);
            mConn.registerBondStateReceiver();
            boolean connectResult = mConn.connect();
        }
    };
}
