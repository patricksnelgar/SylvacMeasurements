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

import java.util.UUID;

import static com.patrick.Sylvac_Calipers.RecordFragment.MEASUREMENT_RECEIVED;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Manages connections between bluetooth mDiscoveredDevices. Also handles custom broadcast events
 */
public class ConnectionManager implements CommunicationCharacteristics{

    // TAG for logging purposes only
    private static final String TAG = ConnectionManager.class.getSimpleName();

    // Bluetooth objects used for connection
    private BluetoothAdapter mBluetoothAdpater;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;

    private boolean mConnected = false;
    private boolean mServiceDiscoveryStarted = false;
    private boolean mServiceDiscoveryCompleted = false;

    private MainActivity mMainActivity;

    public ConnectionManager(MainActivity pParent){
        mMainActivity = pParent;
        if(mBluetoothAdpater == null || mBluetoothManager == null)
            initializeBluetooth();

        LocalBroadcastManager.getInstance(mMainActivity).registerReceiver(mBroadcastReceiver, makeBroadcastReceiverFilter());
    }

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

    public boolean connect(final String mDeviceAddress){
        // Catch Bluetooth not init or invalid address
        if(mBluetoothAdpater == null ||  mDeviceAddress == null){
            Log.w(TAG, "Adapter or device address not set");
            return false;
        }

        // The requested connection has been seen before
        if(mBluetoothDeviceAddress != null && mDeviceAddress.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null){
            Log.d(TAG, "Trying existing connection");
            if(mBluetoothGatt.connect()){
                return true;
            } else return false;
        }

        // New connection requested
        final BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(mDeviceAddress);
        if(device == null){
            Log.d(TAG, "Device not found.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(mMainActivity, false, mGattCallback);
        Log.d(TAG, "Trying to create new connection.");
        mBluetoothDeviceAddress = mDeviceAddress;
        return true;
    }

    public void disconnect() {
        if(mBluetoothAdpater == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter has not been initialized.");
            return;
        }

        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        mBluetoothDeviceAddress = null;
    }

    public void reconnect(){
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;

        connect(mBluetoothDeviceAddress);
    }

    public void enableIndication(){
        BluetoothGattService mDataService = mBluetoothGatt.getService(UUID.fromString("c1b25000-caaf-6d0e-4c33-7dae30052840"));
        BluetoothGattCharacteristic mMeasurementService = mDataService.getCharacteristic(UUID.fromString("c1b25010-caaf-6d0e-4c33-7dae30052840"));

        final int mCharacteristicProperties = mMeasurementService.getProperties();

        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0){
            Log.d(TAG, "Char has INDICATE prop");
            mBluetoothGatt.setCharacteristicNotification(mMeasurementService, true);

            BluetoothGattDescriptor mDescr = mMeasurementService.getDescriptors().get(0);
            mDescr.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean indicationSet = mBluetoothGatt.writeDescriptor(mDescr);
            Log.d(TAG, "Indication is: " + indicationSet);
            int retry = 0;
            while (retry < 3 && !indicationSet){
                try {
                    Log.d(TAG, "Retrying indicate set:"+retry);
                    Thread.sleep(1000);
                    mBluetoothGatt.writeDescriptor(mDescr);
                    retry++;
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }

    public void broadcastUpdate(String intentAction){
        Log.i(TAG, "Broadcasting update: " + intentAction + " - " + mBluetoothDeviceAddress);
        LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(new Intent(intentAction).putExtra(DEVICE_ADDRESS, mBluetoothDeviceAddress));
    }

    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic characteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra(CommunicationCharacteristics.DEVICE_ADDRESS, mBluetoothDeviceAddress);
        if (TX_ANSWER_FROM_INSTRUMENT_UUID.equals(characteristic.getUuid()) || TX_RECEIVED_DATA_UUID.equals(characteristic.getUuid())) {
            //Have to do this as there is an issue with converting byte value 13 to a string
            final byte[] data = characteristic.getValue();
            final StringBuilder stBuilder = new StringBuilder(data.length);
            for (byte byteChar : data){
                stBuilder.append(String.format("%02X ", byteChar));
            }
            _intent.putExtra(EXTRA_DATA, new String(data));
        }
        LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(_intent);
    }

    public MainActivity getMainActivity() { return mMainActivity; }

    public IntentFilter makeBroadcastReceiverFilter(){
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_DEVICE_CONNECTED);
        mIntentFilter.addAction(ACTION_DEVICE_DISCONNECTED);
        mIntentFilter.addAction(ACTION_DEVICE_SERVICES_DISCOVERED);
        mIntentFilter.addAction(ACTION_DATA_AVAILABLE);

        return mIntentFilter;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        private String TAG = "BluetoothGattCallback";

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if(newState == BluetoothProfile.STATE_CONNECTED){
                intentAction = ACTION_DEVICE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_DEVICE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT Server.");
                broadcastUpdate(intentAction);
            } else {
                Log.d(TAG, "ChangedState: " + newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DEVICE_SERVICES_DISCOVERED);
                Log.d(TAG, "Service discovery complete");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String mReceivedAction = intent.getAction();
            Log.d(TAG, "Received action: " + mReceivedAction);
            switch(mReceivedAction){
                case ACTION_DEVICE_CONNECTED:
                    mConnected = true;
                    // Had to move the discovery of services to this section due to specific scenario not firing the 'onStateChanged"
                    // function in the BluetoothGattCallback:
                    //  When the calipers' bluetooth is turned off then on again, connection is remade but the "onStateChanged" function
                    //  never fires in the BluetoothGattCallback which means the service discovery was never completed and
                    //  the indication notification was never enabled. The calipers where in such a state that it would 'transmit' data but
                    // it was never received on the phone end.
                    Log.i(TAG, "Attempting to start service discovery: "+mBluetoothGatt.discoverServices());
                    mServiceDiscoveryStarted = true;
                    mServiceDiscoveryCompleted = false;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(!mServiceDiscoveryCompleted) {

                                Log.d(TAG, "Service discovery timeout.");
                                Log.d(TAG, "Retrying service discovery...");
                                mServiceDiscoveryCompleted = false;
                                mServiceDiscoveryStarted = false;
                                reconnect();
                            }
                        }
                    }, 5000L);
                    break;
                case ACTION_DEVICE_DISCONNECTED:
                    mConnected = false;
                    break;
                case ACTION_DEVICE_SERVICES_DISCOVERED:
                    mServiceDiscoveryCompleted = true;
                    mServiceDiscoveryStarted = false;
                    Log.d(TAG, "Indicate set.");
                    enableIndication();
                    break;
                case ACTION_DATA_AVAILABLE:
                    final String mData = intent.getStringExtra(EXTRA_DATA);
                    Log.d(TAG, "Received data: " + mData);
                    Intent mDataIntent = new Intent(MEASUREMENT_RECEIVED);
                    mDataIntent.putExtra(MEASUREMENT_DATA, mData);
                    LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(mDataIntent);
                    break;
            }
        }
    };
}
