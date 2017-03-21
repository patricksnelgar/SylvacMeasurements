package com.patrick.Sylvac_Calipers;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        RecordFragment
 * Description: UI for recording and displaying current record data and history
 */
public class RecordFragment extends Fragment {

    public static final String MEASUREMENT_RECEIVED = "MEASUREMENT_RECEIVED";
    public static final String SAVE_DATA = "SAVE_DATA";
    public static final String CLEAR_DATA = "CLEAR_DATA";

    private static final String TAG = RecordFragment.class.getSimpleName();
    private EditText mRecordId;
    private DataReceiver mDataReceiver;
    private MainActivity mParentActivity;
    private SharedPreferences mPrefs;
    private int previousRecordID;
    private int currentRecordID;
    private int valuesPerRecord;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(getContext(), R.xml.preferences, false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);
        valuesPerRecord = Integer.parseInt(mPrefs.getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "-1"));
        if(valuesPerRecord == -1) valuesPerRecord = MainActivity.DEFAULT_PREF_VALUES_PER_ENTRY;

        mPrefs.registerOnSharedPreferenceChangeListener(mPreferenceChange);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.record_fragment, container, false);
        mRecordId = (EditText) v.findViewById(R.id.editCurrentID);
        mRecordId.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                previousRecordID = mPrefs.getInt(MainActivity.PREFERENCE_CURRENT_ID, 0);
                if(actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    try {
                        currentRecordID = Integer.parseInt(mRecordId.getText().toString());
                        mPrefs.edit().putInt(MainActivity.PREFERENCE_CURRENT_ID, currentRecordID).commit();
                    } catch (Exception e){
                        currentRecordID = previousRecordID;
                        mRecordId.setText(String.format("%n"+mPrefs.getInt(MainActivity.PREFERENCE_CURRENT_ID, 0)).trim());
                    }
                }
               return false;
            }
        });

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDataReceiver = new DataReceiver(mParentActivity);
        LocalBroadcastManager.getInstance(mParentActivity).registerReceiver(mDataReceiver, makeDataReceiverFilter());
    }

    @Override
    public void onResume() {
        super.onResume();
        mRecordId.setText(String.valueOf(mPrefs.getInt(MainActivity.PREFERENCE_CURRENT_ID, 0)));
    }

    private IntentFilter makeDataReceiverFilter(){
        IntentFilter mIfilter = new IntentFilter();
        mIfilter.addAction(MEASUREMENT_RECEIVED);
        mIfilter.addAction(SAVE_DATA);
        mIfilter.addAction(CLEAR_DATA);
        return mIfilter;
    }

    public void setParent(MainActivity parent){
        this.mParentActivity = parent;
    }

    final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChange = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(key.equals(MainActivity.PREFERENCE_VALUES_PER_ENTRY)){
                mDataReceiver.resetCurrentRecord();
            }
        }
    };
}
