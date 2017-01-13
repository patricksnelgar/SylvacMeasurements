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

import java.lang.reflect.Method;
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
    private ServiceTimeout mServiceTimeoutRunnable;
    private CharacteristicTimeout mCharacteristicTimeoutEnable;
    private BondStateReceiver mBondStateReceiver;

    private CharWrite mCharWrite = CharWrite.CHARACTERISTIC_INDICATE;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;

    // Flags for checking stages of connection.
    private boolean mConnected = false;
    private boolean mServiceDiscoveryStarted = false;
    private boolean mServiceDiscoveryCompleted = false;
    private boolean hasLock = false;
    private boolean mConnecting = false;
    private boolean tryingConnect;
    private Object lock;

    private MainActivity mMainActivity;

    class ConnectionTimeout implements Runnable {
        @Override
        public void run() {
            Log.d("ConnectionTimeout", "connection failed, retry.");
            reconnect();
        }
    }

    class ServiceTimeout implements Runnable {
        @Override
        public void run(){
            Log.d(TAG,"Service discovery timeout.");
        }
    }

    class CharacteristicTimeout implements Runnable {
        @Override
        public void run() {
            switch(mCharWrite){
                case CHARACTERISTIC_NOTIFY:
                    startEnableNotificationRunnable();
                    startCharacteristicTimeoutRunnable();
                    return;
                case CHARACTERISTIC_INDICATE:
                    startEnableIndicationRunnable();
                    startCharacteristicTimeoutRunnable();
                    return;
                default:
                    return;
            }
        }
    }

    class NotificationRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Enable notify.");
            startCharacteristicTimeoutRunnable();
            enableNotification();
        }
    }

    enum BondState {
        BOND_ONLY_DEVICE,
        BOND_ONLY_INSTRUMENT,
        BOND_TWO_WAY,
        NO_BOND
    }

    enum CharWrite {
        CHARACTERISTIC_NOTIFY,
        CHARACTERISTIC_INDICATE
    }

    // Sets the MainActivity (for handling UI calls and registering receivers)
    // also registers the BroadcastReceiver.
    public ConnectionManager(MainActivity pParent, String mTargetDeviceAddress) {
        mMainActivity = pParent;
        mBluetoothDeviceAddress = mTargetDeviceAddress;
        if (mBluetoothAdpater == null || mBluetoothManager == null)
            Log.d(TAG, "init:" + initializeBluetooth());

        LocalBroadcastManager.getInstance(mMainActivity).registerReceiver(mBroadcastReceiver, makeBroadcastReceiverFilter());
        lock = new Object();
        tryingConnect = false;
        Context context = mMainActivity.getBaseContext();
        mBondStateReceiver = new BondStateReceiver(context, this);
        context.registerReceiver(mBondStateReceiver, new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED"));

        mHandler = new Handler();
        mConnectionTimeoutRunnable = new ConnectionTimeout();
        mServiceTimeoutRunnable = new ServiceTimeout();

        connect();
        mConnecting = true;
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
    public boolean connect(){

        // Catch Bluetooth not init or invalid address
        if(mBluetoothAdpater == null ||  mBluetoothDeviceAddress == null){
            Log.w(TAG, "Adapter or device address not set");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(mBluetoothDeviceAddress);

        if(device == null){
            Log.d(TAG, "Device not found.");
            return false;
        }

        Log.d(TAG, getBondStatus(device).toString());
        mHandler.postDelayed(mConnectionTimeoutRunnable, 2000);

        if(mBluetoothDeviceAddress == null || mBluetoothGatt == null){
            Log.d(TAG, "Create GATT object");

            // Changed to use >API22 BLE code
            mBluetoothGatt = device.connectGatt(mMainActivity, false, mGattCallback, BluetoothDevice.TRANSPORT_LE); // TRANSPORT_LE = 2
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER); // LOW_POWER = 2

            refreshServicesCache(mBluetoothGatt);

            if(mBluetoothGatt == null){
                return false;
            }
            Log.d(TAG, "Gatt created.");
            return true;
        }

        return mBluetoothGatt.connect();

    }

    private void finishConnection() {
        mConnecting = false;
        discoverServices();
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
            mMainActivity.setConnectionStatus("Reconnecting...");
        }

        connect();
    }

    public  void connectionComplete(){
        mMainActivity.setConnectionStatus("Connection complete!");
    }

    private void discoverServices() {
        if(mBluetoothGatt == null){
            Log.d(TAG, "mBluetoothGatt is NULL!!");
            closeAndOpenConnection();
        } else if(!mServiceDiscoveryStarted){
            mServiceDiscoveryStarted = true;
            mServiceDiscoveryCompleted = false;
            mHandler.postDelayed(mServiceTimeoutRunnable, 2000);
            if(mBluetoothGatt.discoverServices()){
                mMainActivity.setConnectionStatus("Starting service discovery.");
                Log.d(TAG, "Service discovery started.");
            }
        }
    }

    private void closeAndOpenConnection(){
        Log.d(TAG, "redo connection.");
        disconnect();
        connect();
    }

    private boolean refreshServicesCache(BluetoothGatt gatt) {
        // Need to refresh the device services cache
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);

            if(localMethod != null){
                return ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
            }
        } catch (Exception e){
            Log.e("TAG", "refresh cache error: " + e.getLocalizedMessage());
        }

        return false;
    }

    private void serviceDiscoveryComplete(){
        mServiceDiscoveryStarted = false;
        mServiceDiscoveryCompleted = true;
        mHandler.removeCallbacks(mServiceTimeoutRunnable);

        startEnableIndicationRunnable();
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

    public void startCharacteristicTimeoutRunnable(){
        mHandler.postDelayed(mCharacteristicTimeoutEnable, 2000);
    }

    public void removeCharacteristicTimeoutRunnable(){
        mHandler.removeCallbacks(mCharacteristicTimeoutEnable);
    }

    public void startEnableNotificationRunnable(){
        mHandler.postDelayed(new NotificationRunnable(), 500);
    }

    public void startEnableIndicationRunnable() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Enable indicate.");
                startCharacteristicTimeoutRunnable();
                enableIndication();
            }
        }, 500);
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

        return mIntentFilter;
    }

    public void startServiceDiscovery() { discoverServices(); }

    public void setDeviceAddress(String address) { mBluetoothDeviceAddress = address; }

    public String getDeviceAddress() { return mBluetoothDeviceAddress; }

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
                    finishConnection();
                    Log.i(TAG, "Connected to GATT server.");
                } else if(mConnecting) {
                    closeAndOpenConnection();
                } else {
                   Log.d(TAG, "Cant connect, retrying.");
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
                //broadcastUpdate(ACTION_DEVICE_SERVICES_DISCOVERED);
                serviceDiscoveryComplete();
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

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "writing descriptor: " + status);
            removeCharacteristicTimeoutRunnable();
            if (status == 0) {// SUCCESS
                switch(mCharWrite){
                    case CHARACTERISTIC_NOTIFY:
                        Log.d(TAG, "Notification active.");
                        mCharWrite = CharWrite.CHARACTERISTIC_INDICATE;
                        connectionComplete();
                        break;
                    case CHARACTERISTIC_INDICATE:
                        Log.d(TAG, "Indicate active.");
                        mCharWrite = CharWrite.CHARACTERISTIC_NOTIFY;
                        Log.d(TAG, "Enable notify.");
                        enableNotification();
                        break;
                    default:
                }
            } else {
                Log.d(TAG, "Wrong status");
            }
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
                    //mHandler.removeCallbacks(mConnectionTimeoutRunnable);
                    //Log.d(TAG, "Connect bond state: " + mBluetoothGatt.getDevice().getBondState());
                    /**
                     * Service discovery initiation and check is performed here.
                     * Flag is set when the discovery begins and another set upon completion.
                     * This is necessary due to the service discovery not always completing, a simple 5s timeout runnable to check the flag
                     * states solved the issue.
                     */
                    /*
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
                    */
                    break;
                case ACTION_DEVICE_DISCONNECTED:
                    mConnected = false;
                    mMainActivity.setConnectionStatus("Device disconnected.");
                    //disconnect();
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
                default:
                    Log.d(TAG, "Received: " + mReceivedAction);
                    break;
            }
        }
    };
}
