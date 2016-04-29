package com.patrick.Sylvac_Calipers;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Patrick on 27/01/2016.
 */
public class HelpActivity extends AppCompatActivity {

    final private String TAG = HelpActivity.class.getSimpleName();

    private ListView mHelpList;
    private List<String> mTopics;
    private List<String> mContent;
    private HelpListAdapter mAdapter;
    private ArrayList<HelpTopic> mHelpEntries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help_fragment);

        getSupportActionBar().setTitle("FAQ and Troubleshooting");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mHelpEntries = new ArrayList<>();
        buildTopicData();

        mHelpList = (ListView) findViewById(R.id.listHelpTopics);
        mAdapter = new HelpListAdapter(this, mHelpEntries);
        mHelpList.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

    }

    private void buildTopicData(){
        mTopics = new ArrayList(Arrays.asList(getResources().getStringArray(R.array.help_sections)));
        mContent = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.section_info)));
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

        public HelpTopic(String t, String c){
            title = t;
            content = c;
        }

        public String getTitle() {
            return this.title;
        }

        public String getContent() {
            return this.content;
        }
    }

    private static class HelpListAdapter extends ArrayAdapter<HelpTopic> {

        private static final String TAG = HelpListAdapter.class.getSimpleName();

        public HelpListAdapter(Context context, ArrayList<HelpTopic> topics){
            super(context, 0, topics);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HelpTopic _topic = getItem(position);

            if(convertView == null){
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.help_topic, parent, false);
            }

            TextView title = (TextView) convertView.findViewById(R.id.topicTitle);
            TextView content = (TextView) convertView.findViewById(R.id.topicContent);

            title.setText(_topic.getTitle());
            content.setText(_topic.getContent());

            return convertView;
        }
    }
}