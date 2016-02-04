package com.patrick.Sylvac_Calipers;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Patrick on 11/01/2016.
 */
public class ConnectFragment extends Fragment {

    public static String TAG = ConnectFragment.class.getName();

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.connect_fragment, container, false);
    }
}
