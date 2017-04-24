package com.grg.playground.accescontrol.Activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grg.playground.accescontrol.Common;
import com.grg.playground.accescontrol.R;

/**
 * Created by playground on 29/01/2017.
 */

public class TagInfoTool extends BasicActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_info_tool);

        /**  TODO: later define dialog messages
        mLayout = (LinearLayout) findViewById(R.id.linearLayoutTagInfoTool);
        mErrorMessage = (TextView) findViewById(
                R.id.textTagInfoToolErrorMessage); */

        Log.i("Got to **TagInfoTool** ","---------------------> Got here to TagInfoTool ****8");
       // updateTagInfo(Common.getTag());
    }

}
