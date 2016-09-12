package com.patrick.Sylvac_Calipers;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

/**
 * Created by Patrick on 21/01/2016.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsFragment _settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setTitle("Preferences");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

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
            _p.setSummary("Number of measurements per entry: " + getPreferenceManager().getSharedPreferences().getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "Error"));
            _p = getPreferenceManager().findPreference(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME);
            boolean enablefilename = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(MainActivity.PREFERNCE_AUTO_SAVE,false);
            _p.setSummary("Autosave file: " + getPreferenceManager().getDefaultSharedPreferences(getContext()).getString(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME, "----"));
            _p.setEnabled(enablefilename);
        }


        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i("Settings", "Key changed: " + key );
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            switch (key) {
                case MainActivity.PREFERENCE_VALUES_PER_ENTRY:
                    Preference _p = findPreference(key);
                    _p.setSummary("Number of measurements per entry: " + prefs.getString(key, "Error"));
                    break;
                case MainActivity.PREFERNCE_AUTO_SAVE:
                    boolean enable = getPreferenceManager().getSharedPreferences().getBoolean(key, false);
                    Preference mPrefFile = findPreference(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME);
                    mPrefFile.setEnabled(enable);
                    break;
                case MainActivity.PREFERENCE_AUTO_SAVE_FILENAME:
                    Preference mPrefFileName = findPreference(key);
                    mPrefFileName.setSummary("Autosave file: " + getPreferenceManager().getSharedPreferences().getString(key, "----"));
                    break;
                default:
                    Log.e("Settings", "Preference " + key + " not handled");
            }
        }
    }
}
