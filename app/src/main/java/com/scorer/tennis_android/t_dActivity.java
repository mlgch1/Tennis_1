package com.scorer.tennis;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class t_dActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_t_d);

    }

    // ***********************************

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ***********************************

    public void onClick(View view) {
        finish();
    }


}
