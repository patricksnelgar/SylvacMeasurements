package com.patrick.Sylvac_Calipers;


import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Patrick on 11/01/2016.
 */
public class ConnectFragment extends Fragment {

    public static final String GATT_CONNECTED = "GATT_CONNECTED";
    public static final String GATT_DISCONNECTED = "GATT_DISCONNECTED";
    public static final String GATT_SERVICES_DISCOVERED = "GATT_SERVICES_DISCOVERED";
    public static final String DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    public static final String DATA_AVAILABLE = "DATA_AVAILABLE";
    public static final String CHARACTERISTIC_ID = "CHARACTERISTIC_ID";
    public static final String CONNECTION_COMPLETE = "CONNECTION_COMPLETE";
    public static String TAG = ConnectFragment.class.getName();
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private ArrayList<DiscoveredDevice> mDiscoveredDevices = new ArrayList<>();
    private BluetoothLeScanner mBluetoothScanner;
    private DeviceAdapter mDeviceAdapter;
    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothReceiver mBtReciever;
    private BluetoothGatt mGattDevice;
    private MainActivity mParentActivity;
    private BluetoothLeGattCallback mCallback;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ConnectionManager mConnectionManager;
    private  ListView mListDevices;
    private boolean isConnecting = false;
    private boolean deviceConnected = false;
    private boolean locationPermRequested = false;
    private SharedPreferences prefs;
    private String mBluetoothDeviceAddress = null;
    private boolean isScanning = false;
    private BluetoothGatt mBluetoothGatt;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Clear all devices to avoid duplicate entries
        mDiscoveredDevices.clear();

        // Set up the class to handle data from the calipers
        mBtReciever = new BluetoothReceiver();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBtReciever, makeGattUpdateIntentFilter());
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // Set up the custom ArrayAdapter for the ListView
        mDeviceAdapter = new DeviceAdapter(getActivity(), mDiscoveredDevices);

        // Getting the Bluetooth Adapter
        mBtManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        mConnectionManager = new ConnectionManager(mParentActivity, null);
        mCallback = new BluetoothLeGattCallback(mConnectionManager);

        prefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        if (ContextCompat.checkSelfPermission(mParentActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissions failed");
            if(!locationPermRequested) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please enable location access in order to receive Bluetooth scan results.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
                locationPermRequested = true;
            }
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View connectFragView = inflater.inflate(R.layout.connect_fragment, container, false);
        mListDevices = (ListView) connectFragView.findViewById(R.id.listDicoveredDevices);

        mListDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "Device at index: " + position + " selected");
                BluetoothDevice _device = mDiscoveredDevices.get(position).btDevice;
                connectToDevice(_device.getAddress());
            }
        });

        mListDevices.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                android.support.v7.app.AlertDialog.Builder optionsDialog = new android.support.v7.app.AlertDialog.Builder(getContext());
                optionsDialog.setMessage("Would you like to unpair with " + mDiscoveredDevices.get(position).name + "?");
                optionsDialog.setTitle("Unpair");
                optionsDialog.setPositiveButton("Unpair", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            BluetoothDevice mDevice = mDiscoveredDevices.get(position).btDevice;
                            Method unpairMethod = mDevice.getClass().getMethod("removeBond", (Class[]) null);
                            unpairMethod.invoke(mDevice, (Object[]) null);
                            new Handler().post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mParentActivity, "Device unpaired", Toast.LENGTH_SHORT).show();
                                }
                            });
                            mDiscoveredDevices.remove(position);
                            mDeviceAdapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                optionsDialog.setNegativeButton("Cancel", null);
                optionsDialog.show();
                return false;
            }
        });

        return connectFragView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mDiscoveredDevices.clear();

        mListDevices.setAdapter(mDeviceAdapter);


        if (mBtAdapter != null) {
            // Get all the paired bluetooth devices
            for (BluetoothDevice mBtDevice : mBtAdapter.getBondedDevices()) {
                // TODO:
                // get the major class id (mBtDevice.getBluetoothClass().getMajorDeviceClass())
                // in order to use custom icons.
                // Change the String icon to int deviceType
                if(prefs.getBoolean("sylvac_devices", false)) {
                    if (mBtDevice.getName().matches("^(SY|IBRBLE|MTY).*$"))
                        mDiscoveredDevices.add(new DiscoveredDevice(mBtDevice.getName() + " ", true, "", mBtDevice.getAddress(), mBtDevice));
                } else {
                    mDiscoveredDevices.add(new DiscoveredDevice(mBtDevice.getName() + " ", true, "", mBtDevice.getAddress(), mBtDevice));
                }
            }
            mDeviceAdapter.notifyDataSetChanged();
        }

    }

    @Override
    public void onResume() {

        super.onResume();
        if(mBtAdapter == null || !mBtAdapter.isEnabled()){
            return;
        } else {
            mBluetoothScanner = mBtAdapter.getBluetoothLeScanner();
            scanForDevices(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mBluetoothScanner != null){
            scanForDevices(false);
        }
    }

    @Override
    public void onDestroy() {
        if(mConnectionManager.getBluetoothGatt() == null) {
            super.onDestroy();
            return;
        }
        mConnectionManager.removeBluetoothGatt();
        super.onDestroy();
    }

    public void scanForDevices(boolean scan){

        if(mBtAdapter.isEnabled()){
            if(scan){
                Log.i(TAG, "Scanning for devices");
                mBluetoothScanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            } else {
                Log.i(TAG, "Stopping scan");
                mBluetoothScanner.stopScan(scanCallback);
            }
        }
    }

    public boolean connectToDevice(String mAddress){
        scanForDevices(false);
        if(mBtAdapter == null || mAddress == null){
            Log.i(TAG, "BluetoothAdapter not initialized or invalid address");
            return false;
        }
        if (mBluetoothDeviceAddress != null && mAddress.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null){
            Log.i(TAG, "Trying to use an existing connection");
            if(mBluetoothGatt.connect()){
                // We are connected
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice _device = mBtAdapter.getRemoteDevice(mAddress);
        if(_device == null){
            Log.w(TAG, "Device not found");
            return false;
        }

        mBluetoothGatt = _device.connectGatt(mParentActivity, false, mCallback);
        Log.i(TAG, "Trying to create a new connection");
        mBluetoothDeviceAddress = mAddress;
        return true;
    }

    public void disconnect(){
        if(mBtAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.disconnect();
    }

    public void close(){
        if(mBluetoothGatt == null) return;

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(DATA_AVAILABLE);
        localIntentFilter.addAction(GATT_CONNECTED);
        localIntentFilter.addAction(GATT_DISCONNECTED);
        localIntentFilter.addAction(GATT_SERVICES_DISCOVERED);
        localIntentFilter.addAction(CONNECTION_COMPLETE);
        return localIntentFilter;
    }

    public void setParent(MainActivity parent){
        this.mParentActivity = parent;
    }

    final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "ScanResult: " + result.toString());
            BluetoothDevice _device = result.getDevice();
            if(prefs.getBoolean("sylvac_devices", false)) {
                if (_device.getName().matches("^(SY|IBRBLE|MTY).*$")) {
                    DiscoveredDevice _mDevice = new DiscoveredDevice(_device.getName(), false, "", _device.getAddress(), _device);
                    if (!mDiscoveredDevices.contains(_mDevice) || mDiscoveredDevices.size() == 0) {
                        mDiscoveredDevices.add(_mDevice);
                        Log.i(TAG, "New device discovered! " + _device.getName());
                        mDeviceAdapter.notifyDataSetChanged();
                    }
                }
            } else {
                DiscoveredDevice _mDevice = new DiscoveredDevice(_device.getName(), false, "", _device.getAddress(), _device);
                if (!mDiscoveredDevices.contains(_mDevice) || mDiscoveredDevices.size() == 0) {
                    mDiscoveredDevices.add(_mDevice);
                    Log.i(TAG, "New device discovered! " + _device.getName());
                    mDeviceAdapter.notifyDataSetChanged();
                }
            }

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult singleResult : results)
                Log.i(TAG, "ScanResult - Results: " + singleResult.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state){
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG, "BLUETOOTH ON");
                        scanForDevices(true);
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(TAG, "BLUETOOTH OFF");
                        scanForDevices(false);
                        break;
                    default:
                        break;
                }
            }

        }
    };
}
