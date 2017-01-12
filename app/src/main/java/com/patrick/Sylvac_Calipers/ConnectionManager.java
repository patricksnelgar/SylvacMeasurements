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
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private Handler mHandler;
    private ConnectionTimeout mConnectionTimeoutRunnable;

    private int mGattHash = 0;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;

    // Flags for checking stages of connection.
    private boolean mConnected = false;
    private int mBondState = 10;
    private boolean mServiceDiscoveryStarted = false;
    private boolean mServiceDiscoveryCompleted = false;

    private MainActivity mMainActivity;

    class ConnectionTimeout implements Runnable {
        @Override
        public void run() {
            Log.d("ConnectionTimeout", "connection failed, retry.");
            reconnect();
        }
    }

    enum BondState {
        BOND_ONLY_DEVICE,
        BOND_ONLY_INSTRUMENT,
        BOND_TWO_WAY,
        NO_BOND
    }

    // Sets the MainActivity (for handling UI calls and registering receivers)
    // also registers the BroadcastReceiver.
    public ConnectionManager(MainActivity pParent){
        mMainActivity = pParent;
        if(mBluetoothAdpater == null || mBluetoothManager == null)
            Log.d(TAG,"init:"+initializeBluetooth());

        LocalBroadcastManager.getInstance(mMainActivity).registerReceiver(mBroadcastReceiver, makeBroadcastReceiverFilter());

        mHandler = new Handler();
        mConnectionTimeoutRunnable = new ConnectionTimeout();
    }

    // Configures the BluetoothManager and Adapter,
    // includes catches for errors.
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
     * Given a MAC address, try to create a connection to the device,
     * can be of two types; new connection, where the BluetoothGatt object is null, and an
     * existing connection, where the BluetoothGatt object is not null.
     */
    public boolean connect(final String mDeviceAddress){

        // Catch Bluetooth not init or invalid address
        if(mBluetoothAdpater == null ||  mDeviceAddress == null){
            Log.w(TAG, "Adapter or device address not set");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(mDeviceAddress);

        if(device == null){
            Log.d(TAG, "Device not found.");
            return false;
        }

        Log.d(TAG, getBondStatus(device).toString());
        mHandler.postDelayed(mConnectionTimeoutRunnable, 5000L);

        if(mBluetoothDeviceAddress == null || mBluetoothGatt == null){
            mBluetoothGatt = device.connectGatt(mMainActivity, false, mGattCallback);
            if(mBluetoothGatt == null){
                return false;
            }
            mBluetoothDeviceAddress = device.getAddress();
            return true;
        }

        return mBluetoothGatt.connect();
    }

    // Closes and cleans up the current connection
    public void disconnect() {
        if(mBluetoothAdpater == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter has not been initialized.");
            return;
        }

        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * For use during an attempted connection that stalls for some reason,
     * or the service discovery times out.
     */
    public void reconnect(){
        if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        connect(mBluetoothDeviceAddress);
    }

    private BondState getBondStatus(BluetoothDevice mBluetoothDevice){
        BondState mBondState = checkIfDeviceBonded(mBluetoothDevice);
        return mBondState;
    }

    private BondState checkIfDeviceBonded(BluetoothDevice mBluetoothDevice){
        Set<BluetoothDevice> mBondedDevices = mBluetoothAdpater.getBondedDevices();
        boolean isSylvacDevice = Pattern.matches("^(SY|IBRBLE|MTY)$", mBluetoothDevice.getName());

        if(!isSylvacDevice && !mBondedDevices.contains(mBluetoothDevice)){
            Log.d("checkIfDeviceBonded", "No bond");
            return BondState.NO_BOND;
        }
        if(isSylvacDevice && !mBondedDevices.contains(mBluetoothDevice)){
            Log.d("checkIfDeviceBonded", "Bond only instrument");
            return BondState.BOND_ONLY_INSTRUMENT;
        }

        if(isSylvacDevice || !mBondedDevices.contains(mBluetoothDevice)){
            Log.d("checkIfDeviceBonded", "Bond two way");
            return BondState.BOND_TWO_WAY;
        }
        Log.d("checkIfDeviceBonded", "Bond only device");
        return BondState.BOND_ONLY_DEVICE;
    }

    /**
     * Gets the specific service on the calipers that handles data transmission.
     * sets a flag in the notification characteristic of the service which 'subscribes' the user to this service.
     * then enables the 'indication' descriptor of the characteristic so the BluetoothGattCallback is fired when data is sent out.
     */
    public void enableIndication(){
        BluetoothGattService mDataService = mBluetoothGatt.getService(UUID.fromString("c1b25000-caaf-6d0e-4c33-7dae30052840"));
        BluetoothGattCharacteristic mMeasurementService = mDataService.getCharacteristic(UUID.fromString("c1b25010-caaf-6d0e-4c33-7dae30052840"));

        final int mCharacteristicProperties = mMeasurementService.getProperties();

        // Double check to make sure the Characteristic has the Indicate property.
        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0){
            Log.d(TAG, "Char has INDICATE prop");
            mBluetoothGatt.setCharacteristicNotification(mMeasurementService, true);

            BluetoothGattDescriptor mDescr = mMeasurementService.getDescriptors().get(0);
            mDescr.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean indicationSet = mBluetoothGatt.writeDescriptor(mDescr);
            Log.d(TAG, "Indication is: " + indicationSet);
            int retry = 0;

            // Sometimes the writing of the descriptor is not successful the first time
            // so a retry loop normally fixes this.
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

            if(indicationSet) mMainActivity.setConnectionStatus("Connection complete.");
        }
    }

    public void enableNotification(){
        BluetoothGattService mDataService = mBluetoothGatt.getService(UUID.fromString("c1b25000-caaf-6d0e-4c33-7dae30052840"));
        BluetoothGattCharacteristic mNotifyChar = mDataService.getCharacteristic(UUID.fromString("C1B25013-CAAF-6D0E-4C33-7DAE30052840"));

        final int mCharacteristicProperties = mNotifyChar.getProperties();

        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
            Log.d(TAG, "Char has NOTIFY prop.");

            mBluetoothGatt.setCharacteristicNotification(mNotifyChar, true);

            BluetoothGattDescriptor mDescriptor = mNotifyChar.getDescriptors().get(0);
            mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            boolean notifySet = mBluetoothGatt.writeDescriptor(mDescriptor);

            int retry = 0;

            while (retry < 3 && !notifySet){
                try {
                    Log.d(TAG, "retry notify set:"+retry);
                    Thread.sleep(1000);
                    notifySet = mBluetoothGatt.writeDescriptor(mDescriptor);
                    retry++;
                } catch (InterruptedException ie){
                    Log.e(TAG, ie.getLocalizedMessage());
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

    public IntentFilter makeBroadcastReceiverFilter(){
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_DEVICE_CONNECTED);
        mIntentFilter.addAction(ACTION_DEVICE_DISCONNECTED);
        mIntentFilter.addAction(ACTION_DEVICE_SERVICES_DISCOVERED);
        mIntentFilter.addAction(ACTION_DATA_AVAILABLE);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        return mIntentFilter;
    }

    /**
     * Callback that handles changes in communication link between the calipers and app.
     * Updates are broadcast to the system based on what changes in the link to be properly handled elsewhere.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        private String TAG = "BluetoothGattCallback";

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            // STATE_CONNECTED    = 2
            // STATE_DISCONNECTED = 0
            if(newState == BluetoothProfile.STATE_CONNECTED){
                mHandler.removeCallbacks(mConnectionTimeoutRunnable);
                if(status == 0) {
                    broadcastUpdate(ACTION_DEVICE_CONNECTED);
                    //finishConnection();
                    Log.i(TAG, "Connected to GATT server.");
                } else {
                   reconnect();
                }

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

            // GATT_SUCCESS = 0
            // Function gets called whenever a service is discovered on the remote device,
            // a device can have multiple services.
            // status will only be 0 when all services have been discovered.
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DEVICE_SERVICES_DISCOVERED);
                Log.d(TAG, "Service discovery complete");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // Called when the subscribed indication value on the GATT server changes.
        // Characteristic is changed when the 'send data' button on the calipers is pressed.
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    /**
     * BroadcastReceiver handles most of the connect / disconnect functionality, ensures that a connection
     * and service discovery is completed, as well as setting the Indication value
     */
    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        private String TAG = "BroadcastRevceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            final String mReceivedAction = intent.getAction();

            //Log.d(TAG, "Received action: " + mReceivedAction);
            switch(mReceivedAction){
                case ACTION_DEVICE_CONNECTED:
                    mConnected = true;
                    mHandler.removeCallbacks(mConnectionTimeoutRunnable);
                    Log.d(TAG, "Connect bond state: " + mBluetoothGatt.getDevice().getBondState());
                    /**
                     * Service discovery initiation and check is performed here.
                     * Flag is set when the discovery begins and another set upon completion.
                     * This is necessary due to the service discovery not always completing, a simple 5s timeout runnable to check the flag
                     * states solved the issue.
                     */

                    try{
                        Thread.sleep(500L);
                    } catch (Exception e){
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                    Log.i(TAG, "Attempting to start service discovery: "+mBluetoothGatt.discoverServices());
                    mServiceDiscoveryStarted = true;
                    mServiceDiscoveryCompleted = false;

                    mMainActivity.setConnectionStatus("Starting service discovery.");

                    // This double checks to make sure the service discovery is completed;
                    //  required in order to enable the indicate notification
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
                    }, 2000L);
                    break;
                case ACTION_DEVICE_DISCONNECTED:
                    mConnected = false;
                    mMainActivity.setConnectionStatus("Device disconnected.");
                    disconnect();
                    break;
                case ACTION_DEVICE_SERVICES_DISCOVERED:
                    Log.d(TAG, "Service bond state: " + mBluetoothGatt.getDevice().getBondState());

                    mServiceDiscoveryCompleted = true;
                    mServiceDiscoveryStarted = false;
                    Log.d(TAG, "Indicate set.");
                    // Attempt to set the Indication notification.
                    try {
                        //Thread.sleep(500L);
                        //enableNotification();
                        Thread.sleep(500L);
                        enableIndication();
                    } catch (Exception e){
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                    break;
                case ACTION_DATA_AVAILABLE:
                    // Pull the data out of the intent and pass it on the the DataReceiver class
                    final String mData = intent.getStringExtra(EXTRA_DATA);
                    Log.d(TAG, "Received data: " + mData);
                    Intent mDataIntent = new Intent(MEASUREMENT_RECEIVED);
                    mDataIntent.putExtra(MEASUREMENT_DATA, mData);
                    LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(mDataIntent);
                    break;
                case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    Log.d("Bluetooth State Change", "state received: "+state);
            }
        }
    };
}
