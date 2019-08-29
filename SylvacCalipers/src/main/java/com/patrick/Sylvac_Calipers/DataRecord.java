package com.patrick.Sylvac_Calipers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        DataRecord
 * Description: Class to hold the information on a single 'record' containing an ID and array of measurements
 */
class DataRecord {
    private String entryID;
    private List<String> measurementsArray;
    private static final int numValuesPerLine = 3;
    private static final String recordSeparator = "         "; //10 spaces with 7chars per record pushes the third value onto line 2



    /**
     * Constructor to create a record from a string of measurements
     */
    DataRecord(String ID, String listMeasurement){
        this.entryID = ID;
        measurementsArray = new ArrayList<>();
        formatMeasurements(listMeasurement);
    }

    /**
     * Converts a string of measurements to an array.
     */
    private void formatMeasurements(String inputMeasurement){
        String[] _split = inputMeasurement.split(" {3}");
        measurementsArray.addAll(Arrays.asList(_split));
    }

    String getEntryID(){ return this.entryID; }

    /**
     * Returns the measurements array as a single formatted string
     */
    String getMeasurementsString(){

        if(this.measurementsArray.size() <= numValuesPerLine) {
            StringBuilder _outputBuilder = new StringBuilder();
            for (int i = 0; i < (measurementsArray.size() -1); i++)
                _outputBuilder.append(measurementsArray.get(i)).append(recordSeparator);
            String _output = _outputBuilder.toString();
            _output += measurementsArray.get(measurementsArray.size() - 1);
            return _output.trim();
        } else {
            int numLoops = ((this.measurementsArray.size()+ numValuesPerLine) - 1) / numValuesPerLine;
            StringBuilder output = new StringBuilder();
            int index = 0;
            for(int i = 0; i < numLoops; i++){
                for(int j = 0; j < numValuesPerLine; j++){
                    if(index < measurementsArray.size()) {
                        output.append(measurementsArray.get(index)).append(recordSeparator);
                        index++;
                    }
                }
                output.append("\n");
            }
            return output.toString();
        }
    }

    /**
     * returns the measurement array as a CSV string
     */
    String getRecordForOutput(){
        StringBuilder o = new StringBuilder(this.entryID);
        for(String a : measurementsArray){
            o.append(",").append(a);
        }
        return o.toString();
    }
}