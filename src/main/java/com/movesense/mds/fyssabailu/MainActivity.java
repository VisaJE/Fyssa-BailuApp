package com.movesense.mds.fyssabailu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.movesense.mds.fyssabailu.bailu_app.FyssaMainActivity;
import com.movesense.mds.fyssabailu.bailu_app.FyssaObserver;
import com.movesense.mds.fyssabailu.update_app.ScanActivity;

import rx.subscriptions.CompositeSubscription;

public class MainActivity extends AppCompatActivity  {

    private static final String TAG = MainActivity.class.getSimpleName();
    private CompositeSubscription subscriptions;
    private AlertDialog alertDialog;
    private static SharedPreferences sharedPreferences;

    public static SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String version = BuildConfig.VERSION_NAME;
        getSupportActionBar().setTitle("Bailumittari "+ version);

        setContentView(R.layout.activity_select_test);

        subscriptions = new CompositeSubscription();


        findViewById(R.id.start_button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
    public void onBackPressed() {
        alertDialog.show();
    }

}
