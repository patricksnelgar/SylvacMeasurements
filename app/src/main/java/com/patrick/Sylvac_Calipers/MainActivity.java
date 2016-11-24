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

public class MainActivity extends AppCompatActivity {


    public static final String PREFERENCE_VALUES_PER_ENTRY = "values_per_entry";
    public static final String PREFERENCE_EDITING_ENABLED = "editing_enabled";
    public static final String PREFERENCE_BEEP_ON_RECEIVE = "beep_on_receive";
    public static final String PREFERENCE_ONLY_SYLVAC = "sylvac_devices";
    public static final String PREFERENCE_CURRENT_ID = "current_ID";
    public static final String PREFERENCE_AUTO_SAVE_FILENAME = "auto_save_filename";
    public static final String PREFERNCE_AUTO_SAVE = "auto_save";
    public static final int DEFAULT_PREF_VALUES_PER_ENTRY = 3;

    private String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private PageFragmentAdapter mAdapter;
    private ViewPager mPager;
    private Toolbar mToolbar;
    private TabLayout mTabLayout;
    private SharedPreferences mPrefs;
    private MediaPlayer mPlayer;
    private ConnectionManager mConn;

    private RecordFragment fRecord;
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

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.edit().putInt(PREFERENCE_CURRENT_ID, 0).apply();

        final ConnectionManager mConnMan = new ConnectionManager(this);
        mConn = mConnMan;

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_title));

        mAdapter = new PageFragmentAdapter(getSupportFragmentManager());
        //fConnect = new ConnectFragment();
        //fConnect.setParent(this);
        fScan = new DeviceScanFragment();
        fScan.setParent(this);
        fScan.setConnectionMan(mConn);
        fRecord = new RecordFragment();
        fRecord.setParent(this);
        fRecord.setConnectionMan(mConn);
        mAdapter.addFragment(fScan, "Scan");
        mAdapter.addFragment(fRecord, "Record");

        mPager = (ViewPager) findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mPager);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if(Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100); //Any number
            }

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                mConn.disconnect();
                return true;
            /*case R.id.action_dummy_data:
                Intent mIntent = new Intent(RecordFragment.MEASUREMENT_RECEIVED);
                mIntent.putExtra(CommunicationCharacteristics.MEASUREMENT_DATA, "+0.001");
                LocalBroadcastManager.getInstance(this).sendBroadcast(mIntent);
                return true;*/
            default:
                return super.onOptionsItemSelected(mItem);
        }
    }

    public void playOnReceiveSound(){
        //MediaPlayer mPlayer = MediaPlayer.create(this, R.raw.received);
        //mPlayer.start();
        mPlayer.start();
    }

    final MediaPlayer.OnErrorListener mPlayerErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.e("MainActivity", "mPlayer error: " + what + " - " + extra);
            return true;
        }
    };
}
