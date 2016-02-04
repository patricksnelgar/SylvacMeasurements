package com.patrick.Sylvac_Calipers;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Patrick on 11/01/2016.
 */
public class RecordFragment extends Fragment {

    public static RecordFragment newInstance(){
        RecordFragment mf = new RecordFragment();
        Bundle args = new Bundle();
        args.putString("Name", "RecordFragment");
        mf.setArguments(args);

        return mf;
    }

    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.connect_fragment, container, false);
    }
}
