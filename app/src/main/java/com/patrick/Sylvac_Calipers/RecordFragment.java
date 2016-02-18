package com.patrick.Sylvac_Calipers;

import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Patrick on 11/01/2016.
 */
public class RecordFragment extends Fragment {

    public static final String MEASUREMENT_RECEIVED = "MEASUREMENT_RECEIVED";

    private static final String TAG = RecordFragment.class.getSimpleName();
    private int currentRecordID = 0;
    private int previousRecordID = -1;
    private EditText mRecordId;
    private ListView mRecordsList;
    private RecordAdapter mRecordAdapter;
    private List<Record> mListRecords;
    private DataReceiver mDataReceiver;
    private MainActivity mParentActivity;

    public static RecordFragment newInstance(){
        RecordFragment mf = new RecordFragment();
        Bundle args = new Bundle();
        args.putString("Name", "RecordFragment");
        mf.setArguments(args);

        return mf;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.record_fragment, container, false);
        mRecordId = (EditText) v.findViewById(R.id.editCurrentID);
        mRecordId.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                previousRecordID = currentRecordID;
                if(actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    try {
                        currentRecordID = Integer.parseInt(mRecordId.getText().toString());
                        Log.i(TAG, "User changed ID to: " + currentRecordID);
                    } catch (Exception e){
                        currentRecordID = previousRecordID;
                        Log.e(TAG, "Error setting current record id with: " + mRecordId.getText().toString() + "resetting current ID to: " + currentRecordID);
                        mRecordId.setText(String.format("%n"+currentRecordID).trim());
                    }
                }
               return false;
            }
        });

        mRecordsList = (ListView) v.findViewById(R.id.listRecordEntries);
        mRecordsList.setOnItemClickListener(mOnRecordClickListener);

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        mListRecords = new ArrayList<>();
        mRecordAdapter = new RecordAdapter(getContext(), R.layout.single_record, mListRecords);
        mRecordsList.setAdapter(mRecordAdapter);
        for(int i = 0; i < 10; i++) {
            previousRecordID = currentRecordID;
            mListRecords.add(new Record(String.valueOf(currentRecordID++), "0001.1 - 0001.2 - 0001.3 - 0001.4"));
        }
        mRecordAdapter.notifyDataSetChanged();
        mRecordId.setText(String.valueOf(currentRecordID));

        // TODO: initialize Datareceiver. pass DR to RecordAddapter
        //mDataReceiver = new DataReceiver(mParentActivity);
        //LocalBroadcastManager.getInstance(mParentActivity).registerReceiver(mDataReceiver, makeDataReceiverFilter());

    }

    private IntentFilter makeDataReceiverFilter(){
        IntentFilter mIfilter = new IntentFilter();
        mIfilter.addAction(MEASUREMENT_RECEIVED);
        return mIfilter;
    }

    public void setParent(MainActivity parent){
        this.mParentActivity = parent;
    }

    final AdapterView.OnItemClickListener mOnRecordClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        }
    };

}
