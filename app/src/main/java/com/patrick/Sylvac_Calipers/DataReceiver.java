package com.patrick.Sylvac_Calipers;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author: Patrick
 * Date: 25-Nov-15.
 * Description: receives the values from BleGattCallback to process inside the RecordActivity
 * Notes:
 */
public class DataReceiver extends BroadcastReceiver {


    private final String TAG = DataReceiver.class.getSimpleName();
    private final MainActivity mParentActivity;
    private String mCurrentRecord = "";
    private EditText mCurrentEntryID;
    private ListView mHistory;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int mMeasurementCount = 0;
    public int valuesPerRecord;
    private List<Record> listRecords;
    private RecordAdapter listRecordsAdapter;

    SharedPreferences mPrefs;

    public DataReceiver(MainActivity pRecordActivity){
        this.mParentActivity = pRecordActivity;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        mCurrentEntryID = (EditText) mParentActivity.findViewById(R.id.editCurrentID);
        listRecords = new ArrayList<>();
        listRecordsAdapter = new RecordAdapter(mParentActivity, R.layout.single_record, listRecords);
        mHistory = (ListView) mParentActivity.findViewById(R.id.listRecordEntries);
        mHistory.setAdapter(listRecordsAdapter);
        listRecordsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        valuesPerRecord = Integer.parseInt(mPrefs.getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "-1"));
        if(valuesPerRecord == -1) valuesPerRecord = MainActivity.DEFAULT_PREF_VALUES_PER_ENTRY;
        String action = intent.getAction();
        String data = "NULL";

        if(intent.hasExtra(CommunicationCharacteristics.DATA_VALUE))
            data = new String(intent.getStringExtra(CommunicationCharacteristics.DATA_VALUE)).trim();
        switch (action){
            case RecordFragment.MEASUREMENT_RECEIVED:{
                if(mPrefs.getBoolean(MainActivity.PREFERENCE_BEEP_ON_RECEIVE, false)){
                    Log.i(TAG, "Beep");
                    mParentActivity.playOnReceiveSound();
                }
                mMeasurementCount++;
                mCurrentRecord += data + "   ";
                //Log.i(TAG, mMeasurementCount + ":" + valuesPerRecord + " = " + mCurrentRecord);
                if(mMeasurementCount >= valuesPerRecord){
                    int currentID = mPrefs.getInt(MainActivity.PREFERENCE_CURRENT_ID, 0);
                    int nextID = currentID + 1;
                    mCurrentEntryID.setText(String.valueOf(nextID));
                    mPrefs.edit().putInt(MainActivity.PREFERENCE_CURRENT_ID, nextID).commit();

                    Record newEntry = new Record(String.valueOf(currentID), mCurrentRecord);
                    listRecords.add(newEntry);
                    listRecordsAdapter.notifyDataSetChanged();
                    scrollList();
                    mMeasurementCount = 0;
                    mCurrentRecord="";
                }
                break;
            }
            case RecordFragment.SAVE_DATA:
                saveAllRecords();
                break;
            case RecordFragment.CLEAR_DATA:
                listRecords.clear();
                listRecordsAdapter.notifyDataSetChanged();
                break;
            default:
                Log.i(TAG, "Action received: " + action);
        }
    }

    private void scrollList(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mHistory.setSelection(listRecords.size() - 1);
            }
        });
    }

    private void saveAllRecords(){
        LayoutInflater factory = LayoutInflater.from(mParentActivity);

        AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
        builder.setTitle("Save Data");
        final View alertView = factory.inflate(R.layout.save_data_layout, null);
        builder.setView(alertView);
        final EditText filenameView = (EditText) alertView.findViewById(R.id.filenameText);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save Data", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                boolean saveSuccessful = false;

                String mDir = Environment.getExternalStorageDirectory().toString();
                File path = new File(mDir + "/SavedData");
                path.mkdirs();
                String name = filenameView.getText().toString();
                Log.i(TAG, path + name);
                final File output = new File(path, name);
                /*
                    TODO:   Currently saves to emulated/0/SavedData
                            update to check for external storage and possibly a directory selector
                 */
                try {
                    FileOutputStream fs = new FileOutputStream(output);
                    PrintWriter pr = new PrintWriter(fs);
                    for (Record x : listRecords) {
                        pr.println(x.getRecordForOutput());
                    }
                    pr.flush();
                    pr.close();
                    fs.close();

                    saveSuccessful = true;
                } catch (Exception e) {
                    saveSuccessful = false;
                    e.printStackTrace();
                }

                if (saveSuccessful) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mParentActivity, "Saved data to file: " + output.toString(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mParentActivity, "Failed to save data", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
        builder.show();
    }
}
