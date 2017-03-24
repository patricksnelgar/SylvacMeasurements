package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.List;
import java.util.Set;

import static com.patrick.Sylvac_Calipers.RecordFragment.MEASUREMENT_RECEIVED;

/**
 * Author:      Patrick Snelgar
 * Name:        ConnectionManager
 * Description: Handles the creation of the connection between Sylvac calipers and a phone, includes service discovery and registration to
 *              specific characteristics for data transmission.
 */
public class ConnectionManager implements CommunicationCharacteristics{

    // TAG for logging purposes only
    private static final String TAG = ConnectionManager.class.getSimpleName();

    // Bluetooth objects used for connection
    private BluetoothAdapter mBluetoothAdpater;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;
    private Handler mHandler;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;
    private boolean bonded = false;

    private MainActivity mMainActivity;

    /**
     * Constructor for ConnectionManager class, initializes the Bluetooth adapter,
     * configures the Receivers and starts the connection process.
     */
    public ConnectionManager(MainActivity pParent, String mTargetDeviceAddress) {
        mMainActivity = pParent;
        mBluetoothDeviceAddress = mTargetDeviceAddress;
        if (mBluetoothAdpater == null || mBluetoothManager == null)
            Log.d(TAG, "init:" + initializeBluetooth());

        mHandler = new Handler();
    }

    public void connect(String address){
        mMainActivity.stopScan();
        if(mBluetoothAdpater == null || address == null){
            Log.e(TAG, "Adapter not set or invalid address");
            mMainActivity.setConnectionStatus("Error connecting.");
            return;
        }
        if(mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(address) && mBluetoothGatt != null){
            Log.d(TAG, "Trying existing connection.");
            if(mBluetoothGatt.connect()){
                Log.d(TAG, "Connecting");
                mMainActivity.setConnectionStatus("Trying existing connection.");
                return;
            } else {
                mMainActivity.setConnectionStatus("Failed to connect.");
                Log.e(TAG, "Reconnect failed.");
                return;
            }
        }

        // New connection
        final BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(address);
        if(device == null){
            Log.e(TAG, "Device not found");
            return;
        }
        Set<BluetoothDevice> bonded  = mBluetoothAdpater.getBondedDevices();
        if(bonded.size() > 0) {
            for (BluetoothDevice bDevice : bonded) {
                if (device.equals(bDevice)) {
                    Log.d(TAG, "Previous bond exists.");
                } else {
                    Log.d(TAG, bDevice.getAddress() + " != " + device.getAddress());
                }
            }
        } else Log.w(TAG, "No bonded devices");

        mBluetoothGatt = device.connectGatt(mMainActivity,false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);

        Log.d(TAG, "Trying to create new connection");
        mMainActivity.setConnectionStatus("Creating new connection.");
        mBluetoothDeviceAddress = address;
        return;
    }

    /**
     * Initializes the BluetoothManager and Adapter,
     * returning a flag as to whether it is successful or not.
     */
    public boolean initializeBluetooth(){
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)this.mMainActivity.getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null){
                Log.e(TAG, "COULD NOT INITIALIZE BLUETOOTH MANAGER");
                return false;
            }
        }
        mBluetoothAdpater = mBluetoothManager.getAdapter();
        if(mBluetoothAdpater == null){
            Log.e(TAG, "COULD NOT GET BLUETOOTH ADAPTER");
            return false;
        }
        return true;
    }

    /**
     * Gets the specific service on the calipers that handles data transmission.
     * sets a flag in the notification characteristic of the service which 'subscribes' the user to this service.
     * then enables the 'indication' descriptor of the characteristic so the BluetoothGattCallback is fired when data is sent out.
     */
    public void enableIndication(){
        if(mBluetoothGatt == null) {
            Log.e(TAG,"Gatt server is null");
            return;
        }
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        BluetoothGattService mDataService = null;

        // Get service and characteristic we are interested in
        for(BluetoothGattService s : services){
         if(s.getUuid().equals(SERVICE_UUID)){
             mDataService = s;
         }
        }
        if(mDataService == null) {
            Log.e(TAG, "Could not find service");
            return;
        }
        BluetoothGattCharacteristic mMeasurementService = mDataService.getCharacteristic(TX_RECEIVED_DATA_UUID);

        // A single int represents all the property flags combined.
        final int mCharacteristicProperties = mMeasurementService.getProperties();

        // Double check to make sure the Characteristic has the Indicate property.
        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0){
            //Log.d(TAG, "Char has INDICATE prop");
            Log.d(TAG, "Enable indicate for: " + mBluetoothGatt.getDevice().getAddress());
            mBluetoothGatt.setCharacteristicNotification(mMeasurementService, true);

            // Descriptor to write to is always the first in the array.
            BluetoothGattDescriptor mDescr = mMeasurementService.getDescriptors().get(0);
            mDescr.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean indicationSet = mBluetoothGatt.writeDescriptor(mDescr);
            Log.d(TAG, "Indication is: " + indicationSet);
            int retry = 0;

            /**
             * Sometimes writing the descriptor is not successful on the first attempt,
             * checking the result of the write and retrying no more than 3 times before returning an error on the
             * descriptor write
            */
            while (retry < 3 && !indicationSet){
                try {
                    Log.d(TAG, "Retrying indicate set:"+retry);
                    Thread.sleep(1000);
                    indicationSet = mBluetoothGatt.writeDescriptor(mDescr);
                    retry++;
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }

            if(indicationSet && bonded){
                mMainActivity.setConnectionStatus("Connection complete.");
            }
        }
    }

    /**
     * This method handles the broadcast event that contains the received measurement from the remote device
     * @param intentAction
     * @param characteristic
     */
    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic characteristic){

        if (TX_RECEIVED_DATA_UUID.equals(characteristic.getUuid())) {
            Intent _intent = new Intent(intentAction);
            _intent.putExtra(CommunicationCharacteristics.DEVICE_ADDRESS, mBluetoothDeviceAddress);

            final byte[] data = characteristic.getValue();
            if(data != null) {
                _intent.putExtra(MEASUREMENT_DATA, new String(data));
                LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(_intent);
            }

        }
    }

    public void registerReceivers(){
        LocalBroadcastManager.getInstance(mMainActivity).registerReceiver(broadcastReceiver, new IntentFilter(ACTION_DISCONNECT_DEVICE));
        mMainActivity.getBaseContext().registerReceiver(bondStateReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    public void unRegisterReceivers(){
        LocalBroadcastManager.getInstance(mMainActivity).unregisterReceiver(broadcastReceiver);
        mMainActivity.getBaseContext().unregisterReceiver(bondStateReceiver);
    }

    public void closeGatt(){
        Log.d(TAG, "Closing connection");
        mMainActivity.setConnectionStatus("Connection closed.");
        if(mBluetoothGatt!= null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    private void setTitle(final String title){
        mMainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainActivity.getSupportActionBar().setTitle(title);
            }
        });
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED){
                Log.i(TAG, "Connected to Gatt server: " + mBluetoothGatt.getDevice().getAddress());
                Log.i(TAG, "Bond state: " + mBluetoothGatt.getDevice().getBondState());
                if(mBluetoothGatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED){
                    bonded = true;
                    setTitle("Connected - " + mBluetoothGatt.getDevice().getName());
                } else {
                    bonded = false;
                }
                Log.d(TAG, "Start service discovery...");
                if (mBluetoothGatt.discoverServices()) {
                    Log.d(TAG, "Service discovery started: " + mBluetoothGatt.getDevice().getAddress());
                } else
                    Log.e(TAG, "Could not start service discovery: " + mBluetoothGatt.getDevice().getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected from Gatt server: " + gatt.getDevice().getAddress());
                mMainActivity.setConnectionStatus("Device disconnected");
                setTitle("Disconnected - " + mBluetoothGatt.getDevice().getName());
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "Service discovered: " + mBluetoothGatt.getDevice().getAddress() + " with status: " + status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "Bond status with: " + gatt.getDevice().getAddress() + " = " + mBluetoothGatt.getDevice().getBondState());
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        enableIndication();
                    }
                }, 1000);

            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(MEASUREMENT_RECEIVED, characteristic);
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "received: " + action);
            if(action.equals(ACTION_DISCONNECT_DEVICE)){
                Log.d(TAG, "User disconnect");
                mMainActivity.setConnectionStatus("User disconnect.");
                setTitle("Disconnected - " + mBluetoothGatt.getDevice().getName());
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }
    };

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                bonded = true;
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "BondState change ("+device.getAddress()+"): old=" + bondState +
                " new=" + previousBondState);
                if(device.getAddress().equals(mBluetoothDeviceAddress) && bondState == BluetoothDevice.BOND_BONDED){
                    setTitle("Connected - " + device.getName());
                    Log.d(TAG, "Start service discovery...");
                    if (mBluetoothGatt.discoverServices()) {
                        mMainActivity.setConnectionStatus("Discovering services.");
                        Log.d(TAG, "Service discovery started: " + mBluetoothGatt.getDevice().getAddress());
                    } else {
                        mMainActivity.setConnectionStatus("Service discovery error.");
                        Log.e(TAG, "Could not start service discovery: " + mBluetoothGatt.getDevice().getAddress());
                    }
                }

                if(device.getAddress().equals(mBluetoothDeviceAddress) && bondState == BluetoothDevice.BOND_NONE){
                    bonded = false;
                    mMainActivity.setConnectionStatus("Device disconnected.");
                    setTitle("Disconnected - " + device.getName());
                }
            }
        }
    };
}
