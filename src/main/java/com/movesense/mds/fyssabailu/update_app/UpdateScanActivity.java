package com.movesense.mds.fyssabailu.update_app;

import android.content.Intent;

public class UpdateScanActivity extends ScanActivity {
    @Override protected void continueToActivity() {
        startActivity(new Intent(UpdateScanActivity.this, FyssaSensorUpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
