package com.movesense.mds.fyssabailu.update_app;

import android.os.Bundle;

public class FyssaSensorUpdateStandaloneActivity extends FyssaSensorUpdateActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    String fileName() {
        return "movesense_dfu_s";
    }


}
