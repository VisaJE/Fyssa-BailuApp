package com.movesense.mds.fyssabailu.update_app;

import android.content.Intent;
import android.util.Log;


public class UpdateScanActivity extends ScanActivity {
    @Override
    protected void continueToActivity() {
        Log.d("UpdateScanActivity:", "Continuing back to earlier activity");
        setResult(RESULT_OK,new Intent());
        finish();
    }
}
