package com.patrick.Sylvac_Calipers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Patrick on 12/02/2016.
 */
public class RecordAdapter extends ArrayAdapter<Record> {

    private static final String TAG = RecordAdapter.class.getSimpleName();
    private int numDataPoints = -1;
    private SharedPreferences mPrefs;

    public RecordAdapter(Context context, int resourceID, List<Record> records){
        super(context, resourceID, records);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public View getView(int position, View convertView, ViewGroup parent){
        Record _single = getItem(position);
        //Log.i(TAG, "building view for position:"+position);

        if(convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.single_record, parent, false);

        TextView textViewID = (TextView) convertView.findViewById(R.id.textRecordID);
        TextView textViewData = (TextView) convertView.findViewById(R.id.textRecordData);

        textViewID.setText(_single.getEntryID().trim());
        textViewData.setText(_single.getMeasurementsString());

        return convertView;
    }
}
