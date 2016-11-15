package com.patrick.Sylvac_Calipers;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
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

import static com.patrick.Sylvac_Calipers.CommunicationCharacteristics.ACTION_DATA_AVAILABLE;
import static com.patrick.Sylvac_Calipers.CommunicationCharacteristics.ACTION_GATT_CONNECTED;
import static com.patrick.Sylvac_Calipers.CommunicationCharacteristics.ACTION_GATT_DISCONNECTED;
import static com.patrick.Sylvac_Calipers.CommunicationCharacteristics.ACTION_GATT_SERVICES_DISCOVERED;
import static com.patrick.Sylvac_Calipers.CommunicationCharacteristics.EXTRA_DATA;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: UI for displaying and handling discovery of bluetooth mDiscoveredDevices
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
    public static final String RESCAN = "RESCAN";
    public static String TAG = ConnectFragment.class.getName();
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private ArrayList<DiscoveredDevice> mDiscoveredDevices = new ArrayList<>();
    private BluetoothLeScanner mBluetoothScanner;
    private DeviceAdapter mDeviceAdapter;
    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;
    //private BluetoothReceiver mBtReciever;
    private MainActivity mParentActivity;
    //private BluetoothLeGattCallback mCallback;
    private Handler mHandler;
    private ConnectionManager mConnectionManager;
    private  ListView mListDevices;
    private boolean isConnecting = false;
    private boolean deviceConnected = false;
    private boolean locationPermRequested = false;
    private SharedPreferences prefs;
    private String mBluetoothDeviceAddress = null;
    private boolean isScanning = false;
    private BluetoothGatt mBluetoothGatt;
    private boolean receiversRegistered = false;
    private boolean refreshAfterConnect = true;

    class Toaster implements Runnable{

        private String tMessage;
        private int length;

        public Toaster(String m, int l){
            tMessage = m;
            length = l;
        }

        @Override
        public void run() {
            Toast.makeText(mParentActivity, tMessage, length).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        mHandler = new Handler();
        mConnectionManager = new ConnectionManager(mParentActivity);
        // Set up the class to handle data from the calipers
        /*
        mBtReciever = new BluetoothReceiver();

        mBtReciever.setmManager(mConnectionManager);
        if(!receiversRegistered) {
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBtReciever, makeGattUpdateIntentFilter());
            /LocalBroadcastManager.getInstance(getActivity()).registerReceiver(btReceiver, makeBondFilter());
            receiversRegistered = true;
        }
        */

        // Set up the custom ArrayAdapter for the ListView
        mDeviceAdapter = new DeviceAdapter(getActivity(), mDiscoveredDevices);

        // Getting the Bluetooth Adapter
        mBtManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();

        if(mBtAdapter == null){
            mHandler.post(new Toaster("Bluetooth not supported, exiting", Toast.LENGTH_SHORT));
            mParentActivity.finish();
        }

        //mCallback = new BluetoothLeGattCallback(mConnectionManager);
        mBluetoothScanner = mBtAdapter.getBluetoothLeScanner();

        prefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        if (ContextCompat.checkSelfPermission(mParentActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= 23) {
            Log.e(TAG, "Permissions failed");
            ActivityCompat.requestPermissions(this.mParentActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        }
        getBondedDevices();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View connectFragView = inflater.inflate(R.layout.connect_fragment, container, false);
        Log.d(TAG, "onCreateView");
        mListDevices = (ListView) connectFragView.findViewById(R.id.listDicoveredDevices);

        mListDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "Device at index: " + position + " selected");
                final BluetoothDevice _device = mDiscoveredDevices.get(position).btDevice;
                mHandler.post(new Toaster("Connecting to: " + _device.getName(), Toast.LENGTH_SHORT));
                mConnectionManager.connect(_device.getAddress());

                // TODO: why does device get removed from list after pairing?
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
                            mHandler.post(new Toaster("Device unpaired", Toast.LENGTH_SHORT));
                            mDiscoveredDevices.get(position).bondedDevice = false;
                            mDeviceAdapter.notifyDataSetChanged();
                            mConnectionManager.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                optionsDialog.setNegativeButton("Cancel", null);
                optionsDialog.show();
                return true;
            }
        });

        return connectFragView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(!mBtAdapter.isEnabled()){
            if(!mBtAdapter.isEnabled()){
                Intent enableBt = new Intent(mBtAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBt, 1);
            }
        }
        mParentActivity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        mDeviceAdapter = new DeviceAdapter(getActivity(), mDiscoveredDevices);
        mListDevices.setAdapter(mDeviceAdapter);
        mBluetoothScanner = mBtAdapter.getBluetoothLeScanner();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        scanForDevices(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroy");
    }

    public void scanForDevices(final boolean scan){
        if(mBtAdapter.isEnabled()){
            if(scan && !isScanning){
                mDiscoveredDevices.clear();
                Log.d(TAG, "Clear mDiscoveredDevices: scanForDevices");
                mDeviceAdapter.notifyDataSetChanged();
                Log.i(TAG, "Scanning for mDiscoveredDevices");
                mHandler.post(new Toaster("Scanning...", Toast.LENGTH_SHORT));
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isScanning = false;
                        Log.i(TAG, "Scan timeout");
                        mHandler.post(new Toaster("Scanning stopped", Toast.LENGTH_SHORT));
                        mBluetoothScanner.stopScan(scanCallback);
                        mBluetoothScanner.flushPendingScanResults(scanCallback);
                    }
                }, 10000L);
                isScanning = true;
                mBluetoothScanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable BT
        if(requestCode == 1 && resultCode == Activity.RESULT_CANCELED){
            mParentActivity.finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void getBondedDevices(){
        for(BluetoothDevice bDevice : mBtAdapter.getBondedDevices()){
            if(prefs.getBoolean(MainActivity.PREFERENCE_ONLY_SYLVAC, false)){
                if(bDevice.getName().startsWith("SY")){
                    Log.d(TAG, "Adding bonded device: " + bDevice.getName());
                    mDiscoveredDevices.add(new DiscoveredDevice(bDevice.getName(), true, "", bDevice.getAddress(), bDevice));
                }
            } else {
                mDiscoveredDevices.add(new DiscoveredDevice(bDevice.getName(), true, "", bDevice.getAddress(), bDevice));
            }
        }
        mDeviceAdapter.notifyDataSetChanged();
    }


    private void updateDeviceToBonded(BluetoothDevice _device){
        Log.i(TAG, "Searching for device: " + _device.getName());
        if(!mDiscoveredDevices.isEmpty()){
            for(int i = 0; i < mDiscoveredDevices.size(); i++){
                if(mDiscoveredDevices.get(i).address == _device.getAddress()){
                    Log.i(TAG, "Found device: " + _device.getName() + " updating bond status");
                    mDiscoveredDevices.get(i).bondedDevice = true;
                    mDeviceAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private static  IntentFilter makeBondFilter(){
        IntentFilter _filter = new IntentFilter();
        _filter.addAction(CommunicationCharacteristics.DEVICE_BONDED);
        _filter.addAction(GATT_DISCONNECTED);
        return _filter;
    }

    public void setParent(MainActivity parent){
        this.mParentActivity = parent;
    }

    final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            mParentActivity.runOnUiThread(new Runnable(){
                @Override
                public void run() {
                    BluetoothDevice _device = result.getDevice();
                    if( _device != null) {
                        if (prefs.getBoolean("sylvac_devices", false)) {
                            if (_device.getName().startsWith("SY")) {
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
                }
            });
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

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CONNECTED);
        intentFilter.addAction(ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "Connection successful.");
            } else if (ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Connection dropped or lost.");
            } else if (ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //
                Log.d(TAG, "Service discovery complete.");
                Log.d(TAG, "Try enable indication.");
                mConnectionManager.enableIndication(mBluetoothGatt);
                Log.d(TAG, "Try enable notification.");
                mConnectionManager.enableNotification(mBluetoothGatt);
            } else if (ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                Log.d(TAG, "Data received: " + intent.getStringExtra(EXTRA_DATA));
            }
        }
    };
}