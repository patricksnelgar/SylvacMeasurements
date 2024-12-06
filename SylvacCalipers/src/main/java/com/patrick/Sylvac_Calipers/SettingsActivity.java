package com.patrick.Sylvac_Calipers;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Author:      Patrick Snelgar
 * Name:        SettingsActivity
 * Description: Activity to show and change the Preferences for the application
 */
public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {

    private SettingsFragment _settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;

        actionBar.setTitle("Preferences");
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        _settings = new SettingsFragment();
    }

    public boolean onOptionsItemSelected(MenuItem mItem){
        if(mItem.getItemId() == android.R.id.home){
            finish();
            return true;
        } else return false;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState){
        super.onPostCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, _settings).commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(final Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            Preference _p = getPreferenceManager().findPreference(MainActivity.PREFERENCE_VALUES_PER_ENTRY);
            if (_p != null) {
                _p.setSummary("Number of measurements per entry: " + getPreferenceManager().getSharedPreferences().getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "Error"));
            }


            _p = getPreferenceManager().findPreference(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME);
            if(_p != null){
                boolean enablefilename = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(MainActivity.PREFERENCE_AUTO_SAVE, false);
                _p.setSummary("Autosave file: " + getPreferenceManager().getDefaultSharedPreferences(getContext()).getString(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME, "----"));
                _p.setEnabled(enablefilename);
            }

            _p = getPreferenceManager().findPreference(MainActivity.PREFERENCE_ENABLE_BLOCK_MODE);
            if(_p != null){
                boolean blockModeEnabled = getPreferenceManager().getDefaultSharedPreferences(getContext()).getBoolean(MainActivity.PREFERENCE_ENABLE_BLOCK_MODE, false);

                Preference _q = getPreferenceManager().findPreference(MainActivity.PREFERNCE_RECORDS_PER_BLOCK);
                Preference _s = getPreferenceManager().findPreference(MainActivity.PREFERENCE_BLOCK_ID_INCREMENT);

                if(_q != null && _s != null) {
                    _q.setSummary("Number of records per block: " + getPreferenceManager().getSharedPreferences().getString(MainActivity.PREFERNCE_RECORDS_PER_BLOCK, "NA"));
                    _q.setEnabled(blockModeEnabled);

                    _s.setSummary("Increment ID between blocks by: " + getPreferenceManager().getSharedPreferences().getString(MainActivity.PREFERENCE_BLOCK_ID_INCREMENT, "NA"));
                    _s.setEnabled(blockModeEnabled);
                }
            }
        }

        /**
         * Listener to update the summary for some of the preferences when they change
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i("Settings", "Key changed: " + key );
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            switch (key) {
                case MainActivity.PREFERENCE_VALUES_PER_ENTRY:
                    Preference _p = findPreference(key);
                    _p.setSummary("Number of measurements per entry: " + prefs.getString(key, "Error"));
                    break;
                case MainActivity.PREFERENCE_AUTO_SAVE:
                    boolean enable = getPreferenceManager().getSharedPreferences().getBoolean(key, false);
                    Preference mPrefFile = findPreference(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME);
                    mPrefFile.setEnabled(enable);
                    break;
                case MainActivity.PREFERENCE_AUTO_SAVE_FILENAME:
                    Preference mPrefFileName = findPreference(key);
                    mPrefFileName.setSummary("Autosave file: " + getPreferenceManager().getSharedPreferences().getString(key, "----"));
                    break;
                case MainActivity.PREFERENCE_CURRENT_ID:
                    break;
                case MainActivity.PREFERENCE_ENABLE_BLOCK_MODE:
                    boolean blockModeEnabled = getPreferenceManager().getSharedPreferences().getBoolean(key, false);
                    Preference mPrefRecordsPerBlock = findPreference(MainActivity.PREFERNCE_RECORDS_PER_BLOCK);
                    Preference mPrefBlockIDIncrement = findPreference(MainActivity.PREFERENCE_BLOCK_ID_INCREMENT);

                    mPrefRecordsPerBlock.setEnabled(blockModeEnabled);
                    mPrefBlockIDIncrement.setEnabled(blockModeEnabled);
                    break;
                case MainActivity.PREFERNCE_RECORDS_PER_BLOCK:
                    mPrefRecordsPerBlock = findPreference(key);
                    mPrefRecordsPerBlock.setSummary("Number of records per block: " + getPreferenceManager().getSharedPreferences().getString(key, "NA"));
                    break;
                case MainActivity.PREFERENCE_BLOCK_ID_INCREMENT:
                    mPrefBlockIDIncrement = findPreference(key);
                    mPrefBlockIDIncrement.setSummary("Increment ID between blocks by: " + getPreferenceManager().getSharedPreferences().getString(key, "NA"));
                    break;
                default:
                    Log.e("Settings", "Preference " + key + " not handled");
            }
        }
    }
}
