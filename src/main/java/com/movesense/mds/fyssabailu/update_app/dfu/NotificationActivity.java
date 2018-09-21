package com.movesense.mds.fyssabailu.update_app.dfu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.update_app.SelectTestActivity;

/**
 *
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
            // Start the app before finishing
            final Intent parentIntent = new Intent(this, MainActivity.class);
            parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Intent startAppIntent = new Intent(this, FyssaSensorUpdateActivity.class);
            startAppIntent.putExtras(getIntent().getExtras());
            startActivities(new Intent[] { parentIntent, startAppIntent });
        } else {
            Log.e(TAG, "NOT isTaskRoot()");
        }

        // Now finish, which will drop the user in to the activity that was at the top
        //  of the task stack
        finish();
    }
}
