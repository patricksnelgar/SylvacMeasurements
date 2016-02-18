package com.patrick.Sylvac_Calipers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

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
    private int mIdCount = 1;
    private int mMeasurementCount = 0;
    private boolean AUTO_ADD = true;
    public int numDataPoints = 0;
    private List<Record> listRecords;
    private RecordAdapter listRecordsAdapter;

    private SharedPreferences mSharedPrefs;


    public DataReceiver(MainActivity pRecordActivity){
        this.mParentActivity = pRecordActivity;
        mCurrentRecord = (TextView) mParentActivity.findViewById(R.id.textCurrentRecordData);
        mCurrentEntryID = (EditText) mParentActivity.findViewById(R.id.editCurrentID);
        listRecords = new ArrayList<>();
        listRecordsAdapter = new RecordAdapter(mParentActivity, R.layout.single_record, listRecords);
        mHistory = (ListView) mParentActivity.findViewById(R.id.listRecordEntries);

        mCurrentEntryID.setText(String.valueOf(mIdCount++));

        mHistory.setAdapter(listRecordsAdapter);
        listRecordsAdapter.notifyDataSetChanged();

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);
        numDataPoints = Integer.parseInt(mSharedPrefs.getString(mParentActivity.PREFERENCE_VALUES_PER_ENTRY,"-1"));
        if(numDataPoints == -1) Log.w(TAG, "Failed to set 'VALUES_PER_ENTRY' from preferences");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String data = new String(intent.getByteArrayExtra("NUM_VALUE")).trim();
        switch (action){
            case RecordFragment.MEASUREMENT_RECEIVED:{
                if(mMeasurementCount >= numDataPoints){
                    // TODO
                    // extract current record and insert into the history list
                    // notify dataset changed...
                    Record newEntry = new Record(mCurrentEntryID.getText().toString(), mCurrentRecord.getText().toString());
                    listRecords.add(newEntry);
                    listRecordsAdapter.notifyDataSetChanged();
                    scrollList();

                    Log.i(TAG, "'" + String.format("%1$-3s", String.valueOf(mIdCount)) + "'" + data + "'");
                    mMeasurementCount = 0;
                    mCurrentEntryID.setText(String.valueOf(mIdCount++));
                    mCurrentRecord.setText("");
                }
                mCurrentRecord.append(data+"   ");
                mMeasurementCount++;
                break;
            }
            default:
                Log.i(TAG, "Action received: " + action);
        }
    }

    public boolean setAutoAdd(boolean setValue){
        this.AUTO_ADD = setValue;
        if(AUTO_ADD == setValue) return true;

        return false;
    }

    public void setNumMeasurements(int setValue){
        this.numDataPoints = setValue;
    }

    private void scrollList(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mHistory.setSelection(listRecords.size() - 1);
            }
        });
    }
}
