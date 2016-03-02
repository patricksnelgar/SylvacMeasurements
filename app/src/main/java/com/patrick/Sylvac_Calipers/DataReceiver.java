package com.patrick.Sylvac_Calipers;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
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
    private final String tab = "    ";
    private TextView mCurrentRecord;
    private EditText mCurrentEntryID;
    private ListView mHistory;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int mIdCount = 0;
    private int mMeasurementCount = 0;
    public int valuesPerRecord;
    private List<Record> listRecords;
    private RecordAdapter listRecordsAdapter;

    SharedPreferences mPrefs;

    public DataReceiver(MainActivity pRecordActivity){
        this.mParentActivity = pRecordActivity;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        mCurrentRecord = (TextView) mParentActivity.findViewById(R.id.textCurrentRecordData);
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
        Log.i(TAG, "ValuesPer: " + valuesPerRecord);
        String action = intent.getAction();
        String data = "NULL";
        if(intent.hasExtra("NUM_VALUE"))
            data = new String(intent.getStringExtra("NUM_VALUE")).trim();
        switch (action){
            case RecordFragment.MEASUREMENT_RECEIVED:{
                if(mMeasurementCount >= valuesPerRecord){

                    Record newEntry = new Record(mCurrentEntryID.getText().toString(), mCurrentRecord.getText().toString());
                    listRecords.add(newEntry);
                    listRecordsAdapter.notifyDataSetChanged();
                    scrollList();

                    // TODO: Need to push current record ID value into prefs for persistent storage
                    Log.i(TAG, mCurrentEntryID.getText().toString() + " '" + mCurrentRecord.getText().toString() + "'");
                    mMeasurementCount = 0;
                    int currentID = Integer.parseInt(mCurrentEntryID.getText().toString());
                    int nextID = currentID + 1;
                    Log.i(TAG, "Updating ID: " + currentID + " -> " + nextID);
                    mCurrentEntryID.setText(String.valueOf(nextID));
                    mCurrentRecord.setText("");
                }
                mCurrentRecord.append(data+" - ");
                mMeasurementCount++;
                break;
            }
            case "SAVE_DATA":
                saveAllRecords();
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
        builder.setPositiveButton("Save Data", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                String mDir = Environment.getExternalStorageDirectory().toString();
                File path = new File(mDir+"/SavedData");
                path.mkdirs();
                String name = filenameView.getText().toString();
                Log.i(TAG, path + " : " + name);
                File output = new File(path, name);
                //TODO: Fix storage
                try {
                    FileOutputStream fs = new FileOutputStream(output);
                    for (Record x : listRecords) {
                        fs.write(x.getRecordForOutput().getBytes());
                    }
                    fs.flush();
                    fs.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.show();
    }

    private void writeToFile(){

    }
}
