package com.patrick.Sylvac_Calipers;

import android.os.Bundle;
import android.support.v4.app.Fragment;
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

/**
 * Created by Patrick on 11/01/2016.
 */
public class RecordFragment extends Fragment {

    private static final String TAG = RecordFragment.class.getSimpleName();
    private int currentRecordID = 0;
    private int previousRecordID = -1;
    private EditText mRecordId;
    private ListView mRecordsList;
    private RecordAdapter mRecordAdapter;

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

    final AdapterView.OnItemClickListener mOnRecordClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        }
    };

}
