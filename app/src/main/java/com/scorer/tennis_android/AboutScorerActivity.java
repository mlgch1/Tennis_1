package com.scorer.tennis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class AboutScorerActivity extends Activity {

    // ***********************************

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_scorer);

        TextView t = (TextView) findViewById(R.id.about_Scorer);

        int versionCode = BuildConfig.VERSION_CODE;
        String versionName = BuildConfig.VERSION_NAME;

        t.setText("Version  " + versionCode + "      " + "Build  " + versionName);
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
