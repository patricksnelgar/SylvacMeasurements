package com.patrick.Sylvac_Calipers;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 11/01/2016.
 */
public class PageFragmentAdapter extends FragmentStatePagerAdapter {

    private static final int NUM_ITEMS = 2;
    private final List<String> tabTitles = new ArrayList<>();
    private final List<Fragment> mFragmentList = new ArrayList<>();

    public PageFragmentAdapter(FragmentManager fm) {
        super(fm);
    }

    public void addFragment(Fragment frag, String title){
        mFragmentList.add(frag);
        tabTitles.add(title);
    }

    @Override
    public Fragment getItem(int pos) {
        return mFragmentList.get(pos);
    }

    @Override
    public int getCount() {
        return mFragmentList.size();
    }

    public CharSequence getPageTitle(int pos){ return tabTitles.get(pos); }
}
