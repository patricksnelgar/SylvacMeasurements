package com.patrick.Sylvac_Calipers;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: Adapter for custom row view of each record
 */
public class RecordAdapter extends ArrayAdapter<DataRecord> {

    private static final String TAG = RecordAdapter.class.getSimpleName();

    RecordAdapter(Context context, int resourceID, List<DataRecord> dataRecords){
        super(context, resourceID, dataRecords);
    }

    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent){
        DataRecord _single = getItem(position);

        if(convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.single_record, parent, false);

        TextView textViewID = (TextView) convertView.findViewById(R.id.textRecordID);
        TextView textViewData = (TextView) convertView.findViewById(R.id.textRecordData);

        textViewID.setText(_single != null ? _single.getEntryID().trim() : null);
        textViewData.setText(_single != null ? _single.getMeasurementsString() : null);

        return convertView;
    }
}
