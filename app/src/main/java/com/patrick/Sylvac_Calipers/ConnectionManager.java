package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Manages connections between bluetooth devices. Also handles custom broadcast events
 */
public class ConnectionManager implements CommunicationCharacteristics{

    private static final String TAG = ConnectionManager.class.getSimpleName();
    private static final Object lock = new Object();
    private static BluetoothAdapter mBluetoothAdpater;
    private static BluetoothManager mBluetoothManager;
    private static int nbThreadWaiting = 0;
    private static boolean attemptingConnect = false;
    private BluetoothLeGattCallback callback;
    private boolean bufferPossible = false;
    private String deviceAddress;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean hasLock = false;
    private BluetoothGatt mBluetoothGatt;
    private int nbReconnects = 0;
    private MainActivity parent;
    private ConnectionState previousState = ConnectionState.NOT_CONNECTED;
    private ConnectionState currentState = ConnectionState.NOT_CONNECTED;
    private Runnable timeoutRunnable;

    private static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt mBtGatt, UUID charID){
        if(mBtGatt == null) {
            Log.w(TAG, "Bluetooth is null");
            return null;
        }
        BluetoothGattService mService = mBtGatt.getService(CommunicationCharacteristics.SERVICE_UUID);
        if(mService == null) {
            Log.w(TAG, "Service is null");
            return null;
        }
        BluetoothGattCharacteristic rxChar = mService.getCharacteristic(charID);

        if(rxChar != null)
            return rxChar;

        Log.w(TAG, "Characteristic not found: " + charID);
        return null;

    }

    class Notifier implements Runnable {
        private final ConnectionManager mManager;

        public Notifier(ConnectionManager m) { this.mManager = m; }

        public void run (){
            Log.i(TAG, "Enable notify");
            mManager.enableNotification(mManager.mBluetoothGatt);
            mManager.broadcastUpdate(CommunicationCharacteristics.ACTION_GATT_SERVICES_DISCOVERED);
        }
    }

    public ConnectionManager(MainActivity pParent, String pAddress){
        this.parent = pParent;
        this.deviceAddress = pAddress;

        if(mBluetoothAdpater == null || mBluetoothManager == null) initializeBluetooth();

        this.timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if(ConnectionManager.this.currentState == ConnectionState.CONNECTED) return;
                if(ConnectionManager.this.nbReconnects > 0){
                    ConnectionManager.this.release();
                    return;
                }
                if(ConnectionManager.this.currentState == ConnectionState.CONNECTING){
                    ConnectionManager.this.redoConnection(ConnectionState.NOT_CONNECTED);
                    return;
                }
            }
        };
    }

    public boolean initializeBluetooth(){
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)this.parent.getSystemService(Context.BLUETOOTH_SERVICE);
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

    public void redoConnection(ConnectionState pConnectionState){
        if(this.nbReconnects <= 0){
            closeGatt();
            setConnectionState(pConnectionState);
            this.nbReconnects++;
            makeConnection();
            return;
        }
        closeGatt();
        release();
    }

    public void makeConnection(){
        switch (currentState){
            case CONNECTING:
                Log.i(TAG, "makeConnection() - Connecting state");
                break;
            case DESIRED_DISCONNECTION:
                break;
            case NOT_CONNECTED:
                connect();
                break;
            case CONNECTED:
                if(!this.hasLock) take();
                if(this.hasLock){
                    connect();
                    setConnectionState(ConnectionState.CONNECTING);
                    return;
                }
                release();
                return;
            case RECONNECTING:
                if(!this.hasLock) take();
                if(this.hasLock){
                    makeReconnection();
                    return;
                }
                release();
                return;
            default:
                return;
        }
    }

    public boolean connect(){
        this.callback = new BluetoothLeGattCallback(this);
        if(mBluetoothAdpater == null || this.deviceAddress == null) Log.i(TAG, "Adapter or device address not set");
        BluetoothDevice lBluetoothDevice = mBluetoothAdpater.getRemoteDevice(this.deviceAddress);
        if(lBluetoothDevice == null){
            Log.i(TAG, "Could not find instrument");
            return false;
        }

        this.mBluetoothGatt = lBluetoothDevice.connectGatt(this.parent, true, this.callback);
        return true;
    }

    public void makeReconnection(){
        BluetoothDevice lBluetoothDevice = mBluetoothAdpater.getRemoteDevice(this.deviceAddress);
        if(lBluetoothDevice == null) return;
        connectionTimeout();
        if(mBluetoothGatt != null && deviceAddress != null){
            this.mBluetoothGatt.connect();
            return;
        }
        Log.i(TAG, "No Valid address");
        callback = new BluetoothLeGattCallback(this);
        mBluetoothGatt = lBluetoothDevice.connectGatt(this.parent, true, this.callback);
    }

    public void closeGatt(){
        if(this.mBluetoothGatt != null){
            this.mBluetoothGatt.disconnect();
            this.mBluetoothGatt.close();
            this.mBluetoothGatt = null;
        }
    }

    public void setDeviceAddress(String address){
        this.deviceAddress = address;
    }

    public void setConnectionState(com.patrick.Sylvac_Calipers.ConnectionState pConnectionState){
        this.previousState = this.currentState;
        this.currentState = pConnectionState;
    }

    private void connectionTimeout(){
        this.mHandler.postDelayed(this.timeoutRunnable, 12000L);
    }

    private void take(){
        for(;;){
            try{
                synchronized (lock){
                    if(!attemptingConnect){
                        attemptingConnect = true;
                        this.hasLock = true;
                    } else {
                        nbThreadWaiting++;
                        lock.wait();
                        nbThreadWaiting--;
                    }
                }
            } catch (InterruptedException localInterruptedException){
                localInterruptedException.printStackTrace();
                return;
            }
        }
    }

    private void release(){
        attemptingConnect = false;
        synchronized (lock){
            if(nbThreadWaiting > 0) lock.notifyAll();
            this.hasLock = false;
            return;
        }
    }

    public void servicesDiscovered(boolean complete){
        if(complete){
            Log.i(TAG, "Enable indicate");
            enableIndication(this.mBluetoothGatt);
            mHandler.postDelayed(new Notifier(this), 500);
            return;
        }
        closeGatt();
        makeConnection();
    }

    public void broadcastUpdate(String intentAction){
        Log.i(TAG, "Broadcasting update: " + intentAction + " - " + deviceAddress);
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(new Intent(intentAction).putExtra(ConnectFragment.DEVICE_ADDRESS, this.deviceAddress));
    }

    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic recievedCharacteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra(CommunicationCharacteristics.DEVICE_ADDRESS, this.deviceAddress);
        if (TX_ANSWER_FROM_INSTRUMENT_UUID.equals(recievedCharacteristic.getUuid()) || TX_RECEIVED_DATA_UUID.equals(recievedCharacteristic.getUuid())) {
            //Have to do this as there is an issue with converting byte value 13 to a string
            byte[] characteristicValue = Arrays.copyOfRange(recievedCharacteristic.getValue(), 0, recievedCharacteristic.getValue().length -1);
            Log.d(TAG, "Have data: size=" + characteristicValue.length + " values=" + Arrays.toString(characteristicValue));
            _intent.putExtra(CommunicationCharacteristics.NUM_CANAL, recievedCharacteristic.getUuid().toString());
            if(characteristicValue != null && characteristicValue.length > 0) _intent.putExtra(ConnectFragment.EXTRA_DATA, characteristicValue);
        }
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(_intent);
    }

    public ConnectionState getConnectionState() { return this.currentState; }

    public MainActivity getMainActivity() { return parent; }

    public BluetoothDevice getBluetoothDevice() { return mBluetoothGatt.getDevice(); }

    public BluetoothGatt getBluetoothGatt() { return mBluetoothGatt; }

    public  void setBluetoothGatt(BluetoothGatt gatt){
        this.mBluetoothGatt = gatt;
    }

    public void removeBluetoothGatt(){
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void enableIndication(BluetoothGatt mBluetoothGatt){
        BluetoothGattCharacteristic rxChar = getCharacteristic(mBluetoothGatt, CommunicationCharacteristics.TX_RECEIVED_DATA_UUID);
        if(rxChar != null){
            mBluetoothGatt.setCharacteristicNotification(rxChar, true);
            BluetoothGattDescriptor mDescrip = rxChar.getDescriptor(CommunicationCharacteristics.CCCD_UUID);
            mDescrip.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            if(mBluetoothGatt.writeDescriptor(mDescrip)){
                Log.i(TAG, "Indication set!");
            } else {
                Log.w(TAG, "Indication NOT set");
            }
        }
    }

    public void enableNotification(BluetoothGatt mBluetoothGatt){
        BluetoothGattCharacteristic rxChar = getCharacteristic(mBluetoothGatt, CommunicationCharacteristics.TX_ANSWER_FROM_INSTRUMENT_UUID);
        if(rxChar != null){
            mBluetoothGatt.setCharacteristicNotification(rxChar, true);
            BluetoothGattDescriptor mDescrip = rxChar.getDescriptor(CommunicationCharacteristics.CCCD_UUID);
            mDescrip.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if(mBluetoothGatt.writeDescriptor(mDescrip)){
                Log.i(TAG, "Notification set");
            } else {
                Log.w(TAG, "Notification not set");
            }
        }
    }
}
