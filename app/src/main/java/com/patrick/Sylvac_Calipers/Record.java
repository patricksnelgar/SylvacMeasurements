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
    private static final int numValuesPerLine = 3;
    private static final String recordSeparator = "         "; //10 spaces with 7chars per record pushes the third value onto line 2

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
        String[] _split = inputMeasurement.split("   ");
        //Log.i(TAG, "Split count: " + _split.length);
        for(String i : _split){
            measurementsArray.add(i);
        }
    }

    public String getEntryID(){ return this.entryID; }

    public List<String> getMeasurementsArray(){ return this.measurementsArray; }

    public String getMeasurementsString(){

        if(this.measurementsArray.size() <= numValuesPerLine) {
            String _output = "";
            for (int i = 0; i < (measurementsArray.size() -1); i++)
                _output += measurementsArray.get(i) + recordSeparator;
            _output += measurementsArray.get(measurementsArray.size() - 1);
            return _output.trim();
        } else {
            int numLoops = ((this.measurementsArray.size()+ numValuesPerLine) - 1) / numValuesPerLine;
            String output = "";
            int index = 0;
            for(int i = 0; i < numLoops; i++){
                for(int j = 0; j < numValuesPerLine; j++){
                    if(index < measurementsArray.size()) {
                        output += measurementsArray.get(index) + recordSeparator;
                        index++;
                    }
                }
                output+="\n";
            }
            return output;
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