package com.patrick.Sylvac_Calipers;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

/**
 * Created by Patrick on 11/01/2016.
 */
public class PageFragmentAdapter extends FragmentStatePagerAdapter {

    private static final int NUM_ITEMS = 2;
    private static String[] tabTitles = {"Connect", "Record"};

    public PageFragmentAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int i) {
        switch (i){
            case 0:
                return new ConnectFragment();
            case 1:
                return new RecordFragment();
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return NUM_ITEMS;
    }

    public CharSequence getPageTitle(int pos){ return tabTitles[pos]; }
}
