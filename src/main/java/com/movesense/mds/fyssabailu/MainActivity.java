package com.movesense.mds.fyssabailu;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.movesense.mds.fyssabailu.bailu_app.FyssaApp;
import com.movesense.mds.fyssabailu.bailu_app.FyssaInfoActivity;
import com.movesense.mds.fyssabailu.bailu_app.FyssaObserverActivity;
import com.movesense.mds.fyssabailu.tool.MemoryTools;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.scanner.MainScanActivity;

import rx.subscriptions.CompositeSubscription;

import static com.movesense.mds.fyssabailu.bailu_app.FyssaMainActivity.removeAndDisconnectFromDevices;

public class MainActivity extends AppCompatActivity  {

    private static final String TAG = MainActivity.class.getSimpleName();
    private CompositeSubscription subscriptions;
    private AlertDialog alertDialog;
    private FyssaApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (FyssaApp) getApplication();
        getSupportActionBar().setTitle(R.string.app_name);
        setContentView(R.layout.activity_main_menu);

        subscriptions = new CompositeSubscription();

        findViewById(R.id.start_button2).setOnClickListener(v -> {
            if (app.getMemoryTools().getName().equals(MemoryTools.DEFAULT_STRING)) {
                startActivity(new Intent(MainActivity.this, FyssaInfoActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Log.d(TAG, "No name yet");
            } else {
                removeAndDisconnectFromDevices();
                startActivity(new Intent(MainActivity.this, MainScanActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }

        });
        findViewById(R.id.start_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FyssaObserverActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));

        findViewById(R.id.help_button).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_help)
                        .setMessage(R.string.help_text)
                        .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }
                        )
                        .create().show());

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

        // Checks location services
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            // Bluetooth is not enable so run
            BluetoothAdapter.getDefaultAdapter().enable();
        }
            final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_location_on)
                        .setMessage(R.string.text_location_on)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            alertDialog.dismiss();
                            this.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        })
                        .setNegativeButton(R.string.no, (dialog, which) -> {
                            if (Build.VERSION.SDK_INT >= 21) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        })
                        .create().show();

            }

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
                removeAndDisconnectFromDevices();

                return true;
            case R.id.update_sensor:
                startActivity(new Intent(MainActivity.this, FyssaSensorUpdateActivity.class)
                        );
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
