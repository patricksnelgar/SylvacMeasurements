package com.patrick.Sylvac_Calipers;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
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

    private static final int REQUEST_ENABLE_BT = 1;
    private PageFragmentAdapter mAdapter;
    private ViewPager mPager;
    private Toolbar mToolbar;
    private TabLayout mTabLayout;
    private SharedPreferences mPrefs;
    private static MediaPlayer mPlayer;

    private ConnectFragment fConnect;
    private RecordFragment fRecord;
    private StatusFragment fStatus;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.framework);

        mPlayer = MediaPlayer.create(this, R.raw.received);
        mPlayer.setOnErrorListener(mPlayerErrorListener);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.edit().putInt(PREFERENCE_CURRENT_ID, 0).commit();


        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_title));

        mAdapter = new PageFragmentAdapter(getSupportFragmentManager());
        fConnect = new ConnectFragment();
        fConnect.setParent(this);
        fRecord = new RecordFragment();
        fRecord.setParent(this);
        mAdapter.addFragment(fConnect, "Connect");
        mAdapter.addFragment(fRecord, "Record");
        //mAdapter.addFragment(new StatusFragment(), "Status");

        mPager = (ViewPager) findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mPager);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if(Build.VERSION.SDK_INT == 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("File Access");
                builder.setMessage("Please enable the app to write external storage");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                });
                builder.show();
            }
        }

        //mPlayer = MediaPlayer.create(this, R.raw.received);
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
                fConnect.scanForDevices(true);
                return true;
            case R.id.action_dummy_data:
                Intent mIntent = new Intent(RecordFragment.MEASUREMENT_RECEIVED);
                mIntent.putExtra(CommunicationCharacteristics.DATA_VALUE, "+0.001");
                LocalBroadcastManager.getInstance(this).sendBroadcast(mIntent);
                return true;
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
