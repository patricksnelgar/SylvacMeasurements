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
 * Author: Patrick Snelgar
 * Name:   DataReceiver
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
    private List<DataRecord> listDataRecords;
    private RecordAdapter listRecordsAdapter;
    private FileOutputStream mFileStream;
    private char space = (int) 32;

    SharedPreferences mPrefs;

    /**
     * Constructor for a new DataReceiver.
     * SharedPreferences link is made and accessed to set initial record ID.
     * Builds the ListAdapter for custom DataRecord class.
     * @param pMainActivity: required for running on the UI thread and accessing SharedPreferences for the application
     */
    public DataReceiver(MainActivity pMainActivity){
        this.mParentActivity = pMainActivity;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

        mCurrentEntryID = (EditText) mParentActivity.findViewById(R.id.editCurrentID);
        listDataRecords = new ArrayList<>();
        listRecordsAdapter = new RecordAdapter(mParentActivity, R.layout.single_record, listDataRecords);
        mHistory = (ListView) mParentActivity.findViewById(R.id.listRecordEntries);
        mHistory.setAdapter(listRecordsAdapter);
        listRecordsAdapter.notifyDataSetChanged();

        mCurrentRecordView = (TextView) mParentActivity.findViewById(R.id.currentRecordView);
    }

    /**
     * Called when a measurement is received.
     * Handles the composure of a record based on user preferences.
     * Writes out to a file automatically on the completion of a record if the preference is enabled.
     *
     * @param context
     * @param intent: Holds the action to perform; save data, measurement received, or clear all records.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Get the values from Preferences
        valuesPerRecord = Integer.parseInt(mPrefs.getString(MainActivity.PREFERENCE_VALUES_PER_ENTRY, "-1"));
        if(valuesPerRecord == -1) valuesPerRecord = MainActivity.DEFAULT_PREF_VALUES_PER_ENTRY;
        String action = intent.getAction();
        String data = "NULL";

        if(intent.hasExtra(CommunicationCharacteristics.MEASUREMENT_DATA))
            data = new String(intent.getStringExtra(CommunicationCharacteristics.MEASUREMENT_DATA)).trim();
        switch (action){
            case RecordFragment.MEASUREMENT_RECEIVED:{
                // User can set the device to not beep on receive.
                if(mPrefs.getBoolean(MainActivity.PREFERENCE_BEEP_ON_RECEIVE, false)){
                    Log.i(TAG, "Beep");
                    mParentActivity.playOnReceiveSound();
                }

                mMeasurementCount++;
                // using "   " resulted in the whitespace being removed
                mCurrentRecord += data +space+space+space;
                mCurrentRecordView.setText(mCurrentRecord);

                // All value for a record have been received
                if(mMeasurementCount >= valuesPerRecord){
                    int currentID = mPrefs.getInt(MainActivity.PREFERENCE_CURRENT_ID, 0);
                    int nextID = currentID + 1;
                    // Set the next recordID text field
                    mCurrentEntryID.setText(String.valueOf(nextID));
                    // Update the preferences to relect the incremented ID
                    mPrefs.edit().putInt(MainActivity.PREFERENCE_CURRENT_ID, nextID).commit();

                    // Create a new record from the measurements
                    DataRecord newEntry = new DataRecord(String.valueOf(currentID), mCurrentRecord);
                    listDataRecords.add(newEntry);
                    listRecordsAdapter.notifyDataSetChanged();
                    // make sure the most recent record is always visible
                    scrollList();
                    mMeasurementCount = 0;
                    mCurrentRecord="";

                    // Handles the auto-saving of data to a file.
                    if(mPrefs.getBoolean(MainActivity.PREFERNCE_AUTO_SAVE, false)){
                        String mDir = Environment.getExternalStorageDirectory().toString();
                        File mFolderPath = new File(mDir + "/SavedData");
                        // Create the folder structure if not found
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
                listDataRecords.clear();
                listRecordsAdapter.notifyDataSetChanged();
                mCurrentRecord = "";
                mCurrentRecordView.setText(mCurrentRecord);
                mMeasurementCount = 0;
                break;
            default:
                Log.i(TAG, "Action received: " + action);
        }
    }

    /**
     * Sets the 'active' entry in the list view to the most recent
     * therefore bringing it into focus so the latest records are always visible.
     */
    private void scrollList(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mHistory.setSelection(listDataRecords.size() - 1);
            }
        });
    }

    /**
     * Writes all the records in listDataRecords to a user specified file
     */
    private void saveAllRecords(){
        LayoutInflater factory = LayoutInflater.from(mParentActivity);

        // Create a dialog to ask the user for a filename
        AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
        builder.setTitle("Save Data");
        final View alertView = factory.inflate(R.layout.save_data_layout, null);
        builder.setView(alertView);
        final EditText filenameView = (EditText) alertView.findViewById(R.id.filenameText);
        builder.setNegativeButton("Cancel", null);
        // Saves all the data when the user confirms the filename
        builder.setPositiveButton("Save Data", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                boolean saveSuccessful = false;

                String mDir = Environment.getExternalStorageDirectory().toString();
                File mFolderPath = new File(mDir + "/SavedData");
                if(!mFolderPath.exists()) mFolderPath.mkdirs();
                final File mOutput = new File(mFolderPath,filenameView.getText().toString() + ".csv");
                Log.i(TAG, mOutput.getAbsoluteFile().toString());


                try {
                    mFileStream = new FileOutputStream(mOutput,true);
                    PrintWriter mPw = new PrintWriter(mFileStream);
                    for (DataRecord x : listDataRecords) {
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

    /**
     * Function to clear the measurements in the current record.
     */
    public void resetCurrentRecord(){
        mCurrentRecord = "";
        mCurrentRecordView.setText("");
        mMeasurementCount = 0;
    }
}
