package com.patrick.Sylvac_Calipers;

import java.util.ArrayList;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        DataRecord
 * Description: Class to hold the information on a single 'record' containing an ID and array of measurements
 */
public class DataRecord {
    final private String TAG = DataRecord.class.getSimpleName();
    private String entryID;
    private List<String> measurementsArray;
    private static final int numValuesPerLine = 3;
    private static final String recordSeparator = "         "; //10 spaces with 7chars per record pushes the third value onto line 2

    public DataRecord(){
        this.entryID = null;
        this.measurementsArray = null;
    }

    /**
     * Constructor to create a record from a string of measurements
     * @param ID
     * @param listMeasurement
     */
    public DataRecord(String ID, String listMeasurement){
        this.entryID = ID;
        measurementsArray = new ArrayList<>();
        formatMeasurements(listMeasurement);
    }

    /**
     * Construction for creating a record from an array of measurements
     * @param ID
     * @param inputMeasurementArray
     */
    public DataRecord(String ID, List<String> inputMeasurementArray){
        this.entryID = ID;
        this.measurementsArray = inputMeasurementArray;
    }

    /**
     * Converts a string of measurements to an array.
     * @param inputMeasurement
     */
    private void formatMeasurements(String inputMeasurement){
        String[] _split = inputMeasurement.split("   ");
        for(String i : _split){
            measurementsArray.add(i);
        }
    }

    public String getEntryID(){ return this.entryID; }

    /**
     * Returns the measurements array as a single formatted string
     * @return
     */
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

    /**
     * returns the measurement array as a CSV string
     * @return
     */
    public String getRecordForOutput(){
        String o = this.entryID;
        for(String a : measurementsArray){
            o+=","+a;
        }
        return o;
    }
}