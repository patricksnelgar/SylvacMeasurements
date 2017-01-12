package com.patrick.Sylvac_Calipers;

import android.app.AlertDialog;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Patrick
 * Date: 25-Nov-15.
 * Description: BroadcastReceiver that handles the measurements coming in from the calipers.
 * Notes: Uses the values stored in SharedPreferences for actions performed upon receiving a measurement,
 *        completing a set of measurements and the saving of data.
 */
public class DataReceiver extends BroadcastReceiver {

    private final String TAG = DataReceiver.class.getSimpleName();

    private final MainActivity mParentActivity;

    private String mCurrentRecord = "";
    private TextView mCurrentRecordView;
    private EditText mCurrentEntryID;
    private ListView mHistory;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int mMeasurementCount = 0;
    public int valuesPerRecord;
    private List<Record> listRecords;
    private RecordAdapter listRecordsAdapter;
    private FileOutputStream mFileStream;
    private char space = (int) 32;

    SharedPreferences mPrefs;

    /**
     * Constructor for a new DataReceiver.
     * SharedPreferences link is made and accessed to set initial record ID.
     * Builds the ListAdapter for custom Record class.
     * @param pMainActivity: required for running on the UI thread and accessing SharedPreferences for the application
     */
    public DataReceiver(MainActivity pMainActivity){
        this.mParentActivity = pMainActivity;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        mCurrentEntryID = (EditText) mParentActivity.findViewById(R.id.editCurrentID);
        listRecords = new ArrayList<>();
        listRecordsAdapter = new RecordAdapter(mParentActivity, R.layout.single_record, listRecords);
        mHistory = (ListView) mParentActivity.findViewById(R.id.listRecordEntries);
        mHistory.setAdapter(listRecordsAdapter);
        listRecordsAdapter.notifyDataSetChanged();

        mCurrentRecordView = (TextView) mParentActivity.findViewById(R.id.currentRecordView);
    }

    /**
     * Called when a measurement is received.
     * Handles the composure of a record based on user preferences.
     * Also writes out to a file automatically on the completion of a record if the preference is enabled.
     *
     * @param context
     * @param intent: Holds the action to perform; save data, measurement received, or clear all records.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        valuesPerRecord = Integer.parseInt(mPrefs.getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "-1"));
        if(valuesPerRecord == -1) valuesPerRecord = MainActivity.DEFAULT_PREF_VALUES_PER_ENTRY;
        String action = intent.getAction();
        String data = "NULL";

        if(intent.hasExtra(CommunicationCharacteristics.MEASUREMENT_DATA))
            data = new String(intent.getStringExtra(CommunicationCharacteristics.MEASUREMENT_DATA)).trim();
        switch (action){
            case RecordFragment.MEASUREMENT_RECEIVED:{
                if(mPrefs.getBoolean(MainActivity.PREFERENCE_BEEP_ON_RECEIVE, false)){
                    Log.i(TAG, "Beep");
                    mParentActivity.playOnReceiveSound();
                }
                mMeasurementCount++;
                mCurrentRecord += data +space+space+space;
                mCurrentRecordView.setText(mCurrentRecord);

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

                    if(mPrefs.getBoolean(MainActivity.PREFERNCE_AUTO_SAVE, false)){
                        String mDir = Environment.getExternalStorageDirectory().toString();
                        File mFolderPath = new File(mDir + "/SavedData");
                        if(!mFolderPath.exists()) mFolderPath.mkdirs();
                        final File mOutput = new File(mFolderPath,mPrefs.getString(MainActivity.PREFERENCE_AUTO_SAVE_FILENAME, "----"));
                        try {
                            mFileStream = new FileOutputStream(mOutput,true);
                            PrintWriter mPw = new PrintWriter(mFileStream);
                            mPw.println(newEntry.getRecordForOutput());
                            mPw.flush();
                            mPw.close();
                            mFileStream.close();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
                break;
            }
            case RecordFragment.SAVE_DATA:
                saveAllRecords();
                break;
            case RecordFragment.CLEAR_DATA:
                listRecords.clear();
                listRecordsAdapter.notifyDataSetChanged();
                mCurrentRecord = "";
                mCurrentRecordView.setText(mCurrentRecord);
                mMeasurementCount = 0;
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
                File mFolderPath = new File(mDir + "/SavedData");
                if(!mFolderPath.exists()) mFolderPath.mkdirs();
                final File mOutput = new File(mFolderPath,filenameView.getText().toString() + ".csv");
                Log.i(TAG, mOutput.getAbsoluteFile().toString());

                /*
                    TODO:   Currently saves to emulated/0/SavedData
                            update to check for external storage and possibly a directory selector
                 */
                try {
                    mFileStream = new FileOutputStream(mOutput,true);
                    PrintWriter mPw = new PrintWriter(mFileStream);
                    for (Record x : listRecords) {
                        mPw.println(x.getRecordForOutput());
                    }
                    mPw.flush();
                    mPw.close();
                    mFileStream.close();

                    saveSuccessful = true;
                } catch (Exception e) {
                    saveSuccessful = false;
                    e.printStackTrace();
                }

                if (saveSuccessful) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mParentActivity, "Saved data to file: " + mOutput.getPath(), Toast.LENGTH_SHORT).show();
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

    public void resetCurrentRecord(){
        mCurrentRecord = "";
        mCurrentRecordView.setText("");
        mMeasurementCount = 0;
    }
}
