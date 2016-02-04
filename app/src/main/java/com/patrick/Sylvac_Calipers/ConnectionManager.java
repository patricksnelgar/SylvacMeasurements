package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by Patrick on 27/01/2016.
 */
public class ConnectionManager implements CommunicationCharacteristics{

    public enum BondState {
        DEVICE_ONLY,
        INSTRUMENT_ONLY,
        TWO_WAY,
        NO_BOND
    }

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

        makeConnection();
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
        switch (this.currentState){
            case CONNECTING:
            case DESIRED_DISCONNECTION:
            case NOT_CONNECTED:
            default:
                return;
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
        }
        //closeGatt();
    }

    public boolean connect(){
        if(mBluetoothAdpater == null || this.deviceAddress == null) Log.i(TAG, "Adapter or device address not set");
        do{
            BluetoothDevice lBluetoothDevice = mBluetoothAdpater.getRemoteDevice(this.deviceAddress);
            if(lBluetoothDevice == null){
                Log.i(TAG, "Could not find instrument");
                return false;
            }
            this.mHandler.postDelayed(this.timeoutRunnable, 12000L);
            if(this.deviceAddress != null && this.mBluetoothGatt != null){
                return this.mBluetoothGatt.connect();
            }
            this.callback = new BluetoothLeGattCallback(this);
            this.mBluetoothGatt = lBluetoothDevice.connectGatt(this.parent, false, this.callback);
        } while(this.mBluetoothGatt == null);
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

    public void finishConnection(){
        ConnectionState lConnectionState = ConnectionState.RECONNECTING;
        broadcastUpdate("Connexion reussi");
        if(this.mBluetoothGatt == null){
            Log.i(TAG, "Undesired connection");
            redoConnection(ConnectionState.UNDESIRED_CONNECTION);
            return;
        }
        Log.i(TAG, "Starting service discovery");
        this.mBluetoothGatt.discoverServices();
    }

    private void closeGatt(){
        if(this.mBluetoothGatt != null){
            this.mBluetoothGatt.disconnect();
            this.mBluetoothGatt.close();
            this.mBluetoothGatt = null;
        }
    }

    public void startServiceDiscovery(){
        mBluetoothGatt.discoverServices();
    }

    public void registerToServices(){
        CommunicationManager.enableIndication(mBluetoothGatt);
        CommunicationManager.enableNotification(mBluetoothGatt);
    }


    public void setConnectionState(com.patrick.Sylvac_Calipers.ConnectionState pConnectionState){
        this.previousState = this.currentState;
        this.currentState = pConnectionState;
    }

    private void connectionTimeout(){
        this.mHandler.postDelayed(this.timeoutRunnable, 12000L);
    }

    public void removeTimeout(){
        this.mHandler.removeCallbacks(this.timeoutRunnable);
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
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(new Intent(intentAction).putExtra("DEVICE_ADDRESS", this.deviceAddress));
    }

    public void broadcastUpdate(String intentAction, BluetoothGattCharacteristic recievedCharacteristic){
        Intent _intent = new Intent(intentAction);
        _intent.putExtra("DEVICE_ADDRESS", this.deviceAddress);
        if((TX_ANSWER_FROM_INSTRUMENT_UUID.equals(recievedCharacteristic.getUuid())) || (TX_RECEIVED_DATA_UUID.equals(recievedCharacteristic.getUuid()))){
            byte[] characteristicValue = recievedCharacteristic.getValue();
            _intent.putExtra("NUM_CANAL", recievedCharacteristic.getUuid().toString());
            if(characteristicValue != null && characteristicValue.length > 0) _intent.putExtra("EXTRA_DATA", characteristicValue);
        }
        LocalBroadcastManager.getInstance(this.parent).sendBroadcast(_intent);
    }

    public ConnectionState getConnectionState() { return this.currentState; }
}
