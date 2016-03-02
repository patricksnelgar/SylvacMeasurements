package com.patrick.Sylvac_Calipers;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Patrick
 * Date: 18-Dec-15.
 * Description:
 * Notes:
 */
public class Record {
    final private String TAG = Record.class.getSimpleName();
    private String entryID;
    private List<String> measurementsArray;

    public Record(){
        this.entryID = null;
        this.measurementsArray = null;
    }

    public Record(String ID, String listMeasurement){
        this.entryID = ID;
        measurementsArray = new ArrayList<>();
        formatMeasurements(listMeasurement);
    }

    public Record(String ID, List<String> inputMeasurementArray){
        this.entryID = ID;
        this.measurementsArray = inputMeasurementArray;
    }

    private void formatMeasurements(String inputMeasurement){
        String[] _split = inputMeasurement.split(" - ");
        //Log.i(TAG, "Split count: " + _split.length);
        for(String i : _split){
            measurementsArray.add(i);
        }
    }

    public String getEntryID(){ return this.entryID; }

    public List<String> getMeasurementsArray(){ return this.measurementsArray; }

    public String getMeasurementsString(){
        if(this.measurementsArray.size() <= 4) {
            String _output = "";
            for (int i = 0; i < (measurementsArray.size() -1); i++)
                _output += measurementsArray.get(i) + " - ";
            _output += measurementsArray.get(measurementsArray.size() - 1);
            return _output.trim();
        } else {
            Log.i(TAG, "need to loop: " + this.measurementsArray.size() / 4);
            return "Multiple lines";
        }
    }

    public void setEntryID(String ID){
        this.entryID = ID;
    }

    public void setMeasurementsArray(List<String> inputMeasurementArray){
        this.measurementsArray = inputMeasurementArray;
    }

    public String getRecordForOutput(){
        String o = this.entryID;
        for(String a : measurementsArray){
            o+=","+a;
        }
        return o;
    }
}