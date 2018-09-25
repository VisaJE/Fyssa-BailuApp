package com.movesense.mds.fyssabailu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.movesense.mds.fyssabailu.bailu_app.FyssaApp;
import com.movesense.mds.fyssabailu.bailu_app.FyssaInfoActivity;
import com.movesense.mds.fyssabailu.bailu_app.FyssaObserver;
import com.movesense.mds.fyssabailu.tool.MemoryTools;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateStandaloneActivity;
import com.movesense.mds.fyssabailu.update_app.ScanActivity;

import rx.subscriptions.CompositeSubscription;

public class MainActivity extends AppCompatActivity  {

    private static final String TAG = MainActivity.class.getSimpleName();
    private CompositeSubscription subscriptions;
    private AlertDialog alertDialog;
    private FyssaApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (FyssaApp) getApplication();
        String version = BuildConfig.VERSION_NAME;
        getSupportActionBar().setTitle("Bailumittari "+ version);
        setContentView(R.layout.activity_select_test);

        subscriptions = new CompositeSubscription();

        findViewById(R.id.start_button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (app.getMemoryTools().getName().equals(MemoryTools.DEFAULT_STRING)) {
                    startActivity(new Intent(MainActivity.this, FyssaInfoActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    Log.d(TAG, "No name yet");
                } else {
                    startActivity(new Intent(MainActivity.this, ScanActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                }

            }
        });
        findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, FyssaObserver.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
        });

        alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.close_app)
                .setMessage(R.string.do_you_want_to_close_application)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= 21) {
                        finishAndRemoveTask();
                    } else {
                        finish();
                    }
                })
                .setNegativeButton(R.string.no, (dialog, which) -> alertDialog.dismiss())
                .create();

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.reset_name:
                app.getMemoryTools().saveName(MemoryTools.DEFAULT_STRING);
                toast("Your username has been reset.");
                return true;

            case R.id.reset_serial:
                app.getMemoryTools().saveSerial(MemoryTools.DEFAULT_STRING);
                RxBle.Instance.initialize(app);
                MdsRx.Instance.initialize(app);
                return true;
            case R.id.update_sensor:
                startActivity(new Intent(MainActivity.this, FyssaSensorUpdateActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            case R.id.update_sensor_s:
                startActivity(new Intent(MainActivity.this, FyssaSensorUpdateStandaloneActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onBackPressed() {
        alertDialog.show();
    }

}
