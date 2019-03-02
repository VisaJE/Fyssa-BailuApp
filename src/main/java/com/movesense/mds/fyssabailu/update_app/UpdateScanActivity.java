package com.movesense.mds.fyssabailu.update_app;

import android.content.Intent;

import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.bailu_app.FyssaMainActivity;

public class UpdateScanActivity extends ScanActivity {
    @Override protected void continueToActivity() {
        startActivity(new Intent(UpdateScanActivity.this,FyssaSensorUpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
