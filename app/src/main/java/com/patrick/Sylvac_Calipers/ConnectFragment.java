package com.patrick.Sylvac_Calipers;


import android.Manifest;
import android.app.Activity;
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
import java.util.regex.Pattern;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: UI for displaying and handling discovery of bluetooth devices
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
    private BluetoothReceiver mBtReciever;
    private BluetoothGatt mGattDevice;
    private MainActivity mParentActivity;
    private BluetoothLeGattCallback mCallback;
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
        mHandler = new Handler();
        mConnectionManager = new ConnectionManager(mParentActivity, null);
        // Set up the class to handle data from the calipers
        mBtReciever = new BluetoothReceiver();
        mBtReciever.setmManager(mConnectionManager);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBtReciever, makeGattUpdateIntentFilter());
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(btReceiver, new IntentFilter(CommunicationCharacteristics.DEVICE_BONDED));

        // Set up the custom ArrayAdapter for the ListView
        mDeviceAdapter = new DeviceAdapter(getActivity(), mDiscoveredDevices);

        // Getting the Bluetooth Adapter
        mBtManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();

        if(mBtAdapter == null){
            mHandler.post(new Toaster("Bluetooth not supported, exiting", Toast.LENGTH_SHORT));
            mParentActivity.finish();
        }

        mCallback = new BluetoothLeGattCallback(mConnectionManager);
        mBluetoothScanner = mBtAdapter.getBluetoothLeScanner();

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
                final BluetoothDevice _device = mDiscoveredDevices.get(position).btDevice;
                mHandler.post(new Toaster("Connecting to: " + _device.getName(), Toast.LENGTH_SHORT));
                connectToDevice(_device);

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
    public void onResume() {
        super.onResume();
        if(!mBtAdapter.isEnabled()){
            if(!mBtAdapter.isEnabled()){
                Intent enableBt = new Intent(mBtAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBt, 1);
            }
        }
        mDiscoveredDevices.clear();
        getBondedDevices();
        mDeviceAdapter = new DeviceAdapter(getActivity(), mDiscoveredDevices);
        mListDevices.setAdapter(mDeviceAdapter);
        mBluetoothScanner = mBtAdapter.getBluetoothLeScanner();
        scanForDevices(true);

    }

    @Override
    public void onPause() {
        super.onPause();
        scanForDevices(false);
    }

    public void scanForDevices(final boolean scan){

        if(mBtAdapter.isEnabled()){
            if(scan && !isScanning){
                Log.i(TAG, "Scanning for devices");
                mHandler.post(new Toaster("Scanning...", Toast.LENGTH_SHORT));
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isScanning = false;
                        Log.i(TAG, "Scan timeout");
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
                    mDiscoveredDevices.add(new DiscoveredDevice(bDevice.getName(), true, "", bDevice.getAddress(), bDevice));
                }
            } else {
                mDiscoveredDevices.add(new DiscoveredDevice(bDevice.getName(), true, "", bDevice.getAddress(), bDevice));
            }
        }
        mDeviceAdapter.notifyDataSetChanged();
    }

    public boolean connectToDevice(BluetoothDevice mDevice){
        scanForDevices(false);
        isConnecting = true;

        if(this.mBluetoothDeviceAddress == null || this.mBluetoothGatt == null){
            Log.i(TAG, "New connection");
            mBluetoothDeviceAddress = mDevice.getAddress();
            mConnectionManager.setDeviceAddress(mBluetoothDeviceAddress);
            mCallback = new BluetoothLeGattCallback(mConnectionManager);
            mBluetoothGatt = mDevice.connectGatt(mParentActivity, false, mCallback);

            if(mBluetoothGatt == null) return false;
            return true;
        }
        Log.i(TAG, "Old connection: " + mBluetoothDeviceAddress);
        return mBluetoothGatt.connect();
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
        localIntentFilter.addAction(CommunicationCharacteristics.ACTION_DATA_AVAILABLE);
        localIntentFilter.addAction(CommunicationCharacteristics.ACTION_DEVICE_NOT_FOUND);
        localIntentFilter.addAction(CommunicationCharacteristics.ACTION_GATT_CONNECTED);
        localIntentFilter.addAction(CommunicationCharacteristics.ACTION_GATT_DISCONNECTED);
        localIntentFilter.addAction(CommunicationCharacteristics.ACTION_GATT_SERVICES_DISCOVERED);
        return localIntentFilter;
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
                    Log.i(TAG, "ScanResult: " + result.toString());
                    BluetoothDevice _device = result.getDevice();
                    if(prefs.getBoolean("sylvac_devices", false)) {
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

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(CommunicationCharacteristics.DEVICE_BONDED)){
                BluetoothDevice bDevice = intent.getExtras().getParcelable(CommunicationCharacteristics.BT_DEVICE);
                Log.i(TAG, "Bonding complete: " + bDevice.getName());
                mHandler.post(new Toaster("Bonded with: " + bDevice.getName(), Toast.LENGTH_SHORT));
                //mDiscoveredDevices.add(new DiscoveredDevice(bDevice.getName(), true, "", bDevice.getAddress(), bDevice));
                mDeviceAdapter.clear();
                getBondedDevices();
                mDeviceAdapter.notifyDataSetChanged();
            }

        }
    };
}
