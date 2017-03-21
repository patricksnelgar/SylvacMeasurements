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
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private ConnectionTimeout mConnectionTimeoutRunnable;
    private ServiceTimeout mServiceTimeoutRunnable;
    private CharacteristicTimeout mCharacteristicTimeoutEnable;
    private BondStateReceiver mBondStateReceiver;
    private int mConnectionAttempts = 0;

    private CharWrite mCharWrite = CharWrite.CHARACTERISTIC_INDICATE;

    // Not null when a previous connection has been made
    private String mBluetoothDeviceAddress;

    // Flags for checking stages of connection.
    private boolean mConnected = false;
    private boolean mServiceDiscoveryStarted = false;
    private boolean mServiceDiscoveryCompleted = false;
    private boolean mConnecting = false;
    private boolean tryingConnect;
    private boolean mIndicateComplete = false;
    private boolean askProfile = false;


    private MainActivity mMainActivity;

    /**
     * Runnable that is called after 2s and only when the connection has not been completed
     * and restarts the connection process.
     */
    class ConnectionTimeout implements Runnable {
        @Override
        public void run() {
            Log.d("ConnectionTimeout", "Connection failed, retry.");
            reconnect();
        }
    }

    /**
     * Runnable that executes if the service discovery on
     * a device does not complete in a fixed time frame and restarts the discovery
     */
    class ServiceTimeout implements Runnable {
        @Override
        public void run(){
            Log.d(TAG,"Service discovery timeout.");
            mServiceDiscoveryStarted = false;
            if(mBluetoothGatt != null){
                unpair();
                refreshServicesCache(mBluetoothGatt);
                disconnect();
            }
            mMainActivity.setConnectionStatus("Connection failed, close the app and retry.");
        }
    }

    /**
     * Runnable module that either starts the registration
     * for the Indicate or Notify characteristic
     */
    class CharacteristicTimeout implements Runnable {
        @Override
        public void run() {
            if(mConnectionAttempts < 3) {
                mConnectionAttempts++;
                switch (mCharWrite) {
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
            closeAndOpenConnection();
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

    /**
     * Enumerator for the different bond states
     */
    enum BondState {
        BOND_ONLY_DEVICE,
        BOND_ONLY_INSTRUMENT,
        BOND_TWO_WAY,
        NO_BOND
    }

    /**
     * Enumerator used for selecting which characteristic to enable
     */
    enum CharWrite {
        CHARACTERISTIC_NOTIFY,
        CHARACTERISTIC_INDICATE
    }

    /**
     * Constructor for ConnectionManager class, initializes the Bluetooth adapter,
     * configures the Receivers and starts the connection process.
     */
    public ConnectionManager(MainActivity pParent, String mTargetDeviceAddress) {
        mMainActivity = pParent;
        mBluetoothDeviceAddress = mTargetDeviceAddress;
        if (mBluetoothAdpater == null || mBluetoothManager == null)
            Log.d(TAG, "init:" + initializeBluetooth());

        LocalBroadcastManager.getInstance(mMainActivity).registerReceiver(mBroadcastReceiver, makeBroadcastReceiverFilter());

        tryingConnect = false;

        registerBondStateReceiver();

        mHandler = new Handler();
        mConnectionTimeoutRunnable = new ConnectionTimeout();
        mServiceTimeoutRunnable = new ServiceTimeout();

        connect();
        mConnecting = true;
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
     * Given a MAC address, try to create a connection to the device,
     * can be of two types; new connection, where the BluetoothGatt object is null, and an
     * existing connection, where the BluetoothGatt object is not null.
     */
    public boolean connect(){

        mIndicateComplete = false;

        if(!(mConnectionAttempts < 3)){
            Log.d(TAG, "Max attempts reached.");
            mMainActivity.setConnectionStatus("Connection failed, reset devices and try again.");
            mConnectionAttempts = 0;
            return false;
        }

        mConnectionAttempts++;

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
        mHandler.postDelayed(mConnectionTimeoutRunnable, 20000);

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

    /**
     * Called after the connection has been successfully established (ie. bonded)
     */
    private void finishConnection() {
        mConnecting = false;
        discoverServices();
    }

    /**
     * Used to 'reset' the ConnectionManager, clearing any
     * artifacts that may have caused issue in connecting to a device.
     */
    public void disconnect() {
        if(mBluetoothAdpater == null) {
            Log.w(TAG, "BluetoothAdapter has not been initialized.");
            return;
        }

        if(mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt is null");
            return;
        }

        // Important to call disconnect() so the remote device can go through the same procedure.
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * For use during an attempted connection that stalls for some reason,
     * or the service discovery times out.
     */
    public void reconnect(){
        /*if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mMainActivity.setConnectionStatus("Reconnecting...");
        }*/

        BluetoothDevice device = mBluetoothAdpater.getRemoteDevice(mBluetoothDeviceAddress);
        if(device!=null){

            mHandler.postDelayed(mConnectionTimeoutRunnable, 20000);

            if(mBluetoothGatt == null || mBluetoothDeviceAddress == null){
                mBluetoothGatt = device.connectGatt(mMainActivity, false, mGattCallback, BluetoothDevice.TRANSPORT_LE); // TRANSPORT_LE = 2
                mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER); // LOW_POWER = 2
                refreshServicesCache(mBluetoothGatt);

                return;
            }
            mBluetoothGatt.connect();
            return;

        }

        Log.e(TAG, "reconnect device is null");
    }

    /**
     * Unpair the device
     */
    public void unpair() {
        BluetoothDevice device = mBluetoothGatt.getDevice();
        try {
            device.getClass().getMethod("removeBond", null).invoke(device, null);
        } catch (Exception e) {
            Log.e(TAG, "Error removing bond: " + e.getLocalizedMessage());
        }
    }

    /**
     * Called when the Notify characteristic is successfully set,
     * changes the status text to notiy the user the connection is complete and
     * remote device is ready to use.
     */
    public  void connectionComplete(){
        mMainActivity.setConnectionStatus("Connection complete!");
        broadcastUpdate(ACTION_DEVICE_SERVICES_DISCOVERED);
    }

    /**
     * Check the BluetoothGatt object is not null before proceeding to
     * start the service discovery on remote device. A runnable is started with a delay
     * so the service discovery does not run indefinitely.
     */
    private void discoverServices() {
        if(mBluetoothGatt == null){
            Log.d(TAG, "mBluetoothGatt is NULL!!");
            closeAndOpenConnection();
        } else if(!mServiceDiscoveryStarted){
            mServiceDiscoveryStarted = true;
            mServiceDiscoveryCompleted = false;
            mHandler.postDelayed(mServiceTimeoutRunnable, 10000);
            if(mBluetoothGatt.discoverServices()){
                mMainActivity.setConnectionStatus("Starting service discovery.");
                Log.d(TAG, "Service discovery started.");
            }
        }
    }

    /**
     * Simple function to clean up the connection and restart it.
     */
    private void closeAndOpenConnection(){
        Log.d(TAG, "redo connection.");
        disconnect();
        connect();
    }

    /**
     * This function gets the 'hidden' function from the BluetoothGatt class
     * that clears the services cache on the local device to prevent
     * the connection using 'old' connections.
     * @param gatt device to clear the cached services for.
     * @return result of the refresh function
     */
    private boolean refreshServicesCache(BluetoothGatt gatt) {
        try {
            // Make a local copy for security.
            BluetoothGatt localBluetoothGatt = gatt;
            // Work around for calling the 'hidden' method.
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);

            if(localMethod != null){
                Log.d(TAG, "BT Services cache refreshedd");
                return ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
            }
        } catch (Exception e){
            Log.e(TAG, "refresh cache error: " + e.getLocalizedMessage());
        }

        return false;
    }

    /**
     * All services on the remote device have been discovered, so
     * remove the timeout for discovery and start registering for characteristics
     */
    private void serviceDiscoveryComplete(){
        mServiceDiscoveryStarted = false;
        mServiceDiscoveryCompleted = true;
        mHandler.removeCallbacks(mServiceTimeoutRunnable);

        startEnableIndicationRunnable();
    }

    /**
     * Checks to see what the bond relation between the remote and local device is.
     * Will only check devices that match the 'Sylvac' naming scheme.
     * @param mBluetoothDevice remote device to check bond status for
     * @return BondState for the local and remote device.
     */
    private BondState getBondStatus(BluetoothDevice mBluetoothDevice){
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
        // Get service and characteristic we are interested in
        BluetoothGattService mDataService = mBluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic mMeasurementService = mDataService.getCharacteristic(TX_RECEIVED_DATA_UUID);

        // A single int represents all the property flags combined.
        final int mCharacteristicProperties = mMeasurementService.getProperties();

        // Double check to make sure the Characteristic has the Indicate property.
        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0){
            Log.d(TAG, "Char has INDICATE prop");
            mBluetoothGatt.setCharacteristicNotification(mMeasurementService, true);

            // Descriptor to write to is always the first in the array.
            BluetoothGattDescriptor mDescr = mMeasurementService.getDescriptors().get(0);
            mDescr.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean indicationSet = mBluetoothGatt.writeDescriptor(mDescr);
            Log.d(TAG, "Indication is: " + indicationSet);
            int retry = 0;

            /**
             * Sometimes the writing of the descriptor is not successful on the first attempt,
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
        }
    }

    /**
     * Registers the local device to listen for characteristic changes on the NOTIFY characteristic.
     * Differs from the INDICATE in that the NOTIFY characteristic does not require acknowledgement of data received.
     */
    public void enableNotification(){
        // Get the service and characteristic we are interested in
        BluetoothGattService mDataService = mBluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic mNotifyChar = mDataService.getCharacteristic(TX_ANSWER_FROM_INSTRUMENT_UUID);

        // All the flags are combined in a single int
        final int mCharacteristicProperties = mNotifyChar.getProperties();

        // Make sure the characteristic has the NOTIFY property
        if((mCharacteristicProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
            Log.d(TAG, "Char has NOTIFY prop.");

            mBluetoothGatt.setCharacteristicNotification(mNotifyChar, true);

            BluetoothGattDescriptor mDescriptor = mNotifyChar.getDescriptors().get(0);
            mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            boolean notifySet = mBluetoothGatt.writeDescriptor(mDescriptor);

            int retry = 0;

            /**
             * Writing the descriptor does not always work first time, but it can on subsequent attempts
             * so a retry loop that checks the return result fixes the problem most of the time.
             */
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

    public boolean writeCharacteristic(BluetoothGatt gatt, byte[] data){
        askProfile = true;
        BluetoothGattService mDataService = gatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic rxChar = mDataService.getCharacteristic(RX_CMD_TO_INSTRUMENT_UUID);

        if(rxChar == null) return false;

        mConnectionAttempts = 3;

        do{
            rxChar.setValue(data);
            boolean complete = gatt.writeCharacteristic(rxChar);
            if(complete){
                Log.d(TAG, "Written: " + data.toString());
                return true;
            }

            mConnectionAttempts--;
        } while (mConnectionAttempts > 0);
        Log.w(TAG, "Failed to write: " + data.toString());
        return false;
    }

    /**
     * Starts the delayed runnable to restart the enabling of characteristics
     */
    public void startCharacteristicTimeoutRunnable(){
        mHandler.postDelayed(mCharacteristicTimeoutEnable, 2000);
    }

    /**
     * Removes the runnable to restart the registration of characteristics
     */
    public void removeCharacteristicTimeoutRunnable(){
        mHandler.removeCallbacks(mCharacteristicTimeoutEnable);
    }

    /**
     * Starts the runnable to enable the NOTIFY characteristic.
     */
    public void startEnableNotificationRunnable(){
        mHandler.postDelayed(new NotificationRunnable(), 1000);
    }

    /**
     * Starts the enabling of the INDICATION characteristic.
     */
    public void startEnableIndicationRunnable() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Enable indicate.");
                startCharacteristicTimeoutRunnable();
                enableIndication();
            }
        }, 1000);
    }

    /**
     * Public method to start the service discovery,
     * called from the BondStateReceiver class.
     */
    public void startServiceDiscovery() { discoverServices(); }

    public void changeProfile(byte[] data){
        String s = data.toString();
        Matcher m = Pattern.compile("^CFG ?BT ?(D0[1|2]).*$", Pattern.DOTALL).matcher(s);
    }

    public void registerBondStateReceiver(){
        Context context = mMainActivity.getBaseContext();
        mBondStateReceiver = new BondStateReceiver(context, this);
        context.registerReceiver(mBondStateReceiver, new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED"));
    }

    /**
     * An abstract method to send a system wide intent for various receivers to receive actions or data.
     * @param intentAction
     */
    public void broadcastUpdate(String intentAction){
        Log.i(TAG, "Broadcasting update: " + intentAction + " - " + mBluetoothDeviceAddress);
        LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(new Intent(intentAction).putExtra(DEVICE_ADDRESS, mBluetoothDeviceAddress));
    }

    /**
     * This method handles the broadcast event that contains the received measurement from the remote device
     * @param intentAction
     * @param characteristic
     */
    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic characteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra(CommunicationCharacteristics.DEVICE_ADDRESS, mBluetoothDeviceAddress);
        if (TX_ANSWER_FROM_INSTRUMENT_UUID.equals(characteristic.getUuid()) || TX_RECEIVED_DATA_UUID.equals(characteristic.getUuid())) {
            //Have to do this as there is an issue with converting byte value 13 to a string
            final byte[] data = characteristic.getValue();

                final StringBuilder stBuilder = new StringBuilder(data.length);
                for (byte byteChar : data) {
                    stBuilder.append(String.format("%02X ", byteChar));
                }
                _intent.putExtra(EXTRA_DATA, new String(data));

        }
        LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(_intent);
    }

    /**
     * Method to construct an IntentFilter for the Broadcast Receiver.
     * @return
     */
    public IntentFilter makeBroadcastReceiverFilter(){
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_DEVICE_CONNECTED);
        mIntentFilter.addAction(ACTION_DEVICE_DISCONNECTED);
        mIntentFilter.addAction(ACTION_DATA_AVAILABLE);
        mIntentFilter.addAction(ACTION_DISCONNECT_DEVICE);
        return mIntentFilter;
    }

    /**
     * Public method to set the remote device address to connect to.
     * @param address
     */
    public void setDeviceAddress(String address) {
        mBluetoothDeviceAddress = address;
        mConnectionAttempts = 0;
    }

    /**
     * @return the remote devices address.
     */
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
            /**
             * STATE_CONNECTED    = 2
             * STATE_DISCONNECTED = 0
             */
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

            /**
             * GATT_SUCCESS = 0
             * Function gets called whenever a service is discovered on the remote device,
             * a device can have multiple services.
             * status will only be 0 when all services have been discovered.
             */
            if(status == BluetoothGatt.GATT_SUCCESS) {
                serviceDiscoveryComplete();
                Log.d(TAG, "Service discovery complete");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        /**
         * Called when the subscribed indication value on the GATT server changes.
         * Characteristic is changed when the 'send data' button on the calipers is pressed.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        /**
         * Called when a descriptor is written to the remote device
         * @param gatt device that is modified
         * @param descriptor attempted to be written
         * @param status the result of the write.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "writing descriptor: " + status);
            removeCharacteristicTimeoutRunnable();

            // SUCCESS
            if (status == 0) {
                switch(mCharWrite){
                    case CHARACTERISTIC_NOTIFY:
                        Log.d(TAG, "Notification active.");
                        mCharWrite = CharWrite.CHARACTERISTIC_INDICATE;
                        connectionComplete();
                        if(Build.VERSION.SDK_INT > 21){
                        //    writeCharacteristic(gatt,new String("CFG BT?\r").getBytes());
                        }
                        break;
                    case CHARACTERISTIC_INDICATE:
                        mIndicateComplete = true;
                        Log.d(TAG, "Indicate active.");
                        mCharWrite = CharWrite.CHARACTERISTIC_NOTIFY;
                        Log.d(TAG, "Enable notify.");
                        enableNotification();
                        break;
                    default:
                }
            } else {
                closeAndOpenConnection();
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

            switch(mReceivedAction){
                case ACTION_DEVICE_CONNECTED:
                    mConnected = true;
                    break;
                case ACTION_DEVICE_DISCONNECTED:
                    mConnected = false;
                    mMainActivity.setConnectionStatus("Device disconnected.");
                    // Call disconnect() to clean up the communications
                    disconnect();
                    break;
               case ACTION_DATA_AVAILABLE:
                    // Pull the data out of the intent and pass it on the the DataReceiver class
                    final String mData = intent.getStringExtra(EXTRA_DATA);
                    Log.d(TAG, "Received data: " + mData);
                    Intent mDataIntent = new Intent(MEASUREMENT_RECEIVED);
                    mDataIntent.putExtra(MEASUREMENT_DATA, mData);
                    LocalBroadcastManager.getInstance(mMainActivity).sendBroadcast(mDataIntent);
                    break;
                case ACTION_DISCONNECT_DEVICE:
                    Log.d(TAG, "User requested disconnect");
                    disconnect();
                default:
                    Log.d(TAG, "Received: " + mReceivedAction);
                    break;
            }
        }
    };
}
