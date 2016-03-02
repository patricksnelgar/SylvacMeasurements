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

import java.util.UUID;

/**
 * Created by Patrick on 27/01/2016.
 */
public class ConnectionManager implements CommunicationCharacteristics{

    final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    final UUID SERVICE_UUID = UUID.fromString("C1B25000-CAAF-6D0E-4C33-7DAE30052840");
    final UUID RX_CMD_TO_INSTRUMENT_UUID = UUID.fromString("C1B25012-CAAF-6D0E-4C33-7DAE30052840");
    final UUID TX_ANSWER_FROM_INSTRUMENT_UUID = UUID.fromString("C1B25013-CAAF-6D0E-4C33-7DAE30052840");
    final UUID TX_RECEIVED_DATA_UUID = UUID.fromString("C1B25010-CAAF-6D0E-4C33-7DAE30052840");

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

        this.mBluetoothGatt = lBluetoothDevice.connectGatt(this.parent, false, this.callback);
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
        mBluetoothGatt = lBluetoothDevice.connectGatt(this.parent, false, this.callback);
    }

    public void closeGatt(){
        if(this.mBluetoothGatt != null){
            this.mBluetoothGatt.disconnect();
            this.mBluetoothGatt.close();
            this.mBluetoothGatt = null;
        }
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

    public void broadcastUpdate(String intentAction){
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(new Intent(intentAction).putExtra(ConnectFragment.DEVICE_ADDRESS, this.deviceAddress));
    }

    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic recievedCharacteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra(ConnectFragment.DEVICE_ADDRESS, this.deviceAddress);
        if((TX_ANSWER_FROM_INSTRUMENT_UUID.equals(recievedCharacteristic.getUuid())) || (TX_RECEIVED_DATA_UUID.equals(recievedCharacteristic.getUuid()))){
            byte[] characteristicValue = recievedCharacteristic.getValue();
            _intent.putExtra(ConnectFragment.CHARACTERISTIC_ID, recievedCharacteristic.getUuid().toString());
            if(characteristicValue != null && characteristicValue.length > 0) _intent.putExtra(ConnectFragment.EXTRA_DATA, characteristicValue);
        }
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(_intent);
    }

    public ConnectionState getConnectionState() { return this.currentState; }

    public MainActivity getMainActivity() { return parent; }

    public BluetoothDevice getBluetoothDevice() { return mBluetoothGatt.getDevice(); }

    public BluetoothGatt getBluetoothGatt() { return mBluetoothGatt; }

    public void removeBluetoothGatt(){
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void enableIndication(BluetoothGatt mBluetoothGatt){
        BluetoothGattService btService = mBluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic btChar = btService.getCharacteristic(TX_RECEIVED_DATA_UUID);
        mBluetoothGatt.setCharacteristicNotification(btChar, true);

        BluetoothGattDescriptor btDes = btChar.getDescriptor(CCCD_UUID);
        btDes.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);

        if(mBluetoothGatt.writeDescriptor(btDes)) Log.i(TAG, "Indication set!");
    }

    public void enableNotification(BluetoothGatt mBtGatt){
        BluetoothGattService btService = mBtGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic btChar = btService.getCharacteristic(TX_ANSWER_FROM_INSTRUMENT_UUID);
        mBtGatt.setCharacteristicNotification(btChar, true);

        BluetoothGattDescriptor btDes = btChar.getDescriptor(CCCD_UUID);
        btDes.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        if(mBtGatt.writeDescriptor(btDes)) Log.i(TAG, "Notification set!");
    }
}
