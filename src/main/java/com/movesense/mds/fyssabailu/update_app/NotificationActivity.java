package com.movesense.mds.fyssabailu.update_app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.update_app.DfuService;

/**
 * Class responsible for handling clicks on notifications
 */

public class NotificationActivity extends Activity {

    private final String TAG = NotificationActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate()");

        // If this activity is the root activity of the task, the app is not running
        if (isTaskRoot()) {
            Log.e(TAG, "isTaskRoot()");
            // Start the app before finishing
            startActivity(new Intent(this, MainActivity.class));

        } else {
            Log.e(TAG, "NOT isTaskRoot()");
        }

        // Now finish, which will drop the user in to the activity that was at the top
        //  of the task stack
        finish();
    }
}
