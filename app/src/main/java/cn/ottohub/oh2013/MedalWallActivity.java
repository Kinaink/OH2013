package cn.ottohub.oh2013;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

/** 用户头衔墙 */
public class MedalWallActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ListView listView = new ListView(this);
        setContentView(listView);
        initRoundTitleBar();
        ArrayList<String> honours = getIntent().getStringArrayListExtra("honours");
        if (honours == null) honours = new ArrayList<String>();
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, honours));
        setTitle("头衔墙");
    }
}
