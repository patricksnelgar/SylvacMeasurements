package com.patrick.Sylvac_Calipers;

import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        PageFragmentAdapter
 * Description: An adapter that manages fragments.
 */
public class PageFragmentAdapter extends FragmentStatePagerAdapter {

    private final List<String> tabTitles = new ArrayList<>();
    private final List<Fragment> mFragmentList = new ArrayList<>();

    PageFragmentAdapter(FragmentManager fm) {
        super(fm);
    }

    void addFragment(Fragment frag, String title){
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
