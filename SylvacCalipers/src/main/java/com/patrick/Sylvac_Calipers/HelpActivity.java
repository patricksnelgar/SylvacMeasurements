package com.patrick.Sylvac_Calipers;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author:      Patrick Snelgar
 * Name:        HelpActivity
 * Description: Displays the help topics from help_info.xml in a user friendly list.
 */
public class HelpActivity extends AppCompatActivity {

    final private String TAG = HelpActivity.class.getSimpleName();

    private ArrayList<HelpTopic> mHelpEntries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help_fragment);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;

        actionBar.setTitle("FAQ and Troubleshooting");
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        mHelpEntries = new ArrayList<>();
        buildTopicData();

        ListView mHelpList = (ListView) findViewById(R.id.listHelpTopics);
        HelpListAdapter mAdapter = new HelpListAdapter(getApplicationContext(), mHelpEntries);
        mHelpList.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

    }

    private void buildTopicData(){
        List<String> mTopics = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.help_sections)));
        List<String> mContent = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.section_info)));
        if(mTopics.size() != mContent.size() || mTopics.size() < mContent.size()) return;

        for(int i = 0; i < mTopics.size(); i++){
            mHelpEntries.add(new HelpTopic(mTopics.get(i), mContent.get(i)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        } else return false;
    }

    static class HelpTopic {
        private String title;
        private String content;

        HelpTopic(String t, String c){
            title = t;
            content = c;
        }

        public String getTitle() {
            return this.title;
        }

        String getContent() {
            return this.content;
        }
    }

    private static class HelpListAdapter extends ArrayAdapter<HelpTopic> {

        private static final String TAG = HelpListAdapter.class.getSimpleName();

        HelpListAdapter(Context context, ArrayList<HelpTopic> topics){
            super(context, 0, topics);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            HelpTopic _topic = getItem(position);

            if(convertView == null){
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.help_topic, parent, false);
            }

            Log.d(TAG,"building help topic: " + (_topic != null ? _topic.getTitle() : null));

            TextView title = (TextView) convertView.findViewById(R.id.topicTitle);
            TextView content = (TextView) convertView.findViewById(R.id.topicContent);

            title.setText(_topic != null ? _topic.getTitle() : null);
            content.setText(_topic != null ? _topic.getContent() : null);

            return convertView;
        }
    }
}