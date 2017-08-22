package com.patrick.Sylvac_Calipers;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        MainActivity
 * Description: main entry point of the application, handles the different fragments and provides the context for preferences.
 */
public class MainActivity extends AppCompatActivity {

    public static final String PREFERENCE_VALUES_PER_ENTRY = "values_per_entry";
    public static final String PREFERENCE_BEEP_ON_RECEIVE = "beep_on_receive";
    public static final String PREFERENCE_ONLY_SYLVAC = "sylvac_devices";
    public static final String PREFERENCE_CURRENT_ID = "current_ID";
    public static final String PREFERENCE_AUTO_SAVE_FILENAME = "auto_save_filename";
    public static final String PREFERENCE_AUTO_SAVE = "auto_save";
    public static final int DEFAULT_PREF_VALUES_PER_ENTRY = 3;

    private String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private MediaPlayer mPlayer;
    private MediaPlayer mRecordPlayer;

    private DeviceScanFragment fScan;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlayer = MediaPlayer.create(this, R.raw.received);
        mPlayer.setOnErrorListener(mPlayerErrorListener);

        mRecordPlayer = MediaPlayer.create(this, R.raw.record);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.edit().putInt(PREFERENCE_CURRENT_ID, 0).apply();

        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.app_title);
        setSupportActionBar(mToolbar);

        PageFragmentAdapter mAdapter = new PageFragmentAdapter(getSupportFragmentManager());

        fScan = new DeviceScanFragment();
        fScan.setParent(this);

        RecordFragment fRecord = new RecordFragment();
        fRecord.setParent(this);

        mAdapter.addFragment(fScan, "Scan");
        mAdapter.addFragment(fRecord, "Data");

        ViewPager mPager = (ViewPager) findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        TabLayout mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mPager);

        // Check if the local device supports Bluetooth LE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Need permissions in OS 23+
        if(Build.VERSION.SDK_INT >= 23) {

            List<String> permissionList = new ArrayList<>();
            // Request access to storage for saving files
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            // Request location access for BT scan results
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
               permissionList.add(Manifest.permission.BLUETOOTH_ADMIN);
            }

            if(permissionList.size() > 0) ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), 100) ;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mItem){
        switch (mItem.getItemId()){
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            case R.id.action_bluetooth_settings:
                startActivity(new Intent().setAction(Settings.ACTION_BLUETOOTH_SETTINGS));
                return true;
            case R.id.action_bluetooth:
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
                return true;
            case R.id.action_save:
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(RecordFragment.SAVE_DATA));
                return true;
            case R.id.action_clear_data:
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(RecordFragment.CLEAR_DATA));
                return true;
            case R.id.action_rescan:
                fScan.scanForDevices(true);
                return true;
            case R.id.action_disconnect:
                setConnectionStatus("Device disconnected.");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(CommunicationCharacteristics.ACTION_DISCONNECT_DEVICE));
                //fScan.resetConnectionManager();
                return true;
            default:
                return super.onOptionsItemSelected(mItem);
        }
    }

    public void playOnReceiveSound(){ mPlayer.start(); }

    public void playRecordSound() { mRecordPlayer.start(); }

    public void setConnectionStatus(final String mStatus){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fScan.setStatus(mStatus);
            }
        });
    }

    public void stopScan(){
        fScan.scanForDevices(false);
    }

    final MediaPlayer.OnErrorListener mPlayerErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.e("MainActivity", "mPlayer error: " + what + " - " + extra);
            return true;
        }
    };
}
