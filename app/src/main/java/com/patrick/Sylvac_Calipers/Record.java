package com.patrick.Sylvac_Calipers;

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
        String[] _split = inputMeasurement.split("   ");
        //Log.i(TAG, "Split count: " + _split.length);
        for(String i : _split){
            measurementsArray.add(i);
        }
    }

    public String getEntryID(){ return this.entryID; }

    public List<String> getMeasurementsArray(){ return this.measurementsArray; }

    public String getMeasurementsString(int numDdataPoints){
        String _output = "";
        for(String i : this.measurementsArray) _output += i + "   ";

        return _output.trim();
    }

    public void setEntryID(String ID){
        this.entryID = ID;
    }

    public void setMeasurementsArray(List<String> inputMeasurementArray){
        this.measurementsArray = inputMeasurementArray;
    }
}