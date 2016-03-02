package com.patrick.Sylvac_Calipers;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {


    public static final String PREFERENCE_VALUES_PER_ENTRY = "values_per_entry";
    public static final String PREFERENCE_EDITING_ENABLED = "editing_enabled";
    public static final String PREFERENCE_BEEP_ON_RECEIVE = "beep_on_receive";
    public static final String PREFERENCE_ONLY_SYLVAC = "sylvac_devices";
    public static final int DEFAULT_PREF_VALUES_PER_ENTRY = 3;

    private static final int REQUEST_ENABLE_BT = 1;
    private PageFragmentAdapter mAdapter;
    private ViewPager mPager;
    private Toolbar mToolbar;
    private TabLayout mTabLayout;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.framework);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_title));

        mAdapter = new PageFragmentAdapter(getSupportFragmentManager());
        ConnectFragment _connect = new ConnectFragment();
        _connect.setParent(this);
        RecordFragment _record = new RecordFragment();
        _record.setParent(this);
        mAdapter.addFragment(_connect, "Connect");
        mAdapter.addFragment(_record, "Record");
        mAdapter.addFragment(new StatusFragment(), "Status");

        mPager = (ViewPager) findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mPager);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
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
            case R.id.action_bluetooth:
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            case R.id.action_save:
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("SAVE_DATA"));
            default:
                return super.onOptionsItemSelected(mItem);
        }
    }

}
