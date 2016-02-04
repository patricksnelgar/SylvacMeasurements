package com.patrick.Sylvac_Calipers;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.logging.Handler;

public class MainActivity extends AppCompatActivity {


    Handler mHandler;

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

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_title));

        mAdapter = new PageFragmentAdapter(getSupportFragmentManager());
        ConnectFragment _connect = new ConnectFragment();
        _connect.setParent(this);
        mAdapter.addFragment(_connect, "Connect");
        mAdapter.addFragment(new RecordFragment(), "Record");
        mAdapter.addFragment(new BluetoothFragment(), "Status");

        mPager = (ViewPager) findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mPager);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
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
            default:
                return super.onOptionsItemSelected(mItem);
        }
    }

}
