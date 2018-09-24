package com.movesense.mds.fyssabailu.update_app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.Toast;

import com.movesense.mds.fyssabailu.BleManager;
import com.movesense.mds.fyssabailu.ConnectionLostDialog;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.MdsRx;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.bailu_app.FyssaApp;
import com.movesense.mds.fyssabailu.bailu_app.FyssaInfoActivity;
import com.movesense.mds.fyssabailu.bailu_app.FyssaMainActivity;
import com.movesense.mds.fyssabailu.tool.MemoryTools;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;
import com.movesense.mds.fyssabailu.model.MdsConnectedDevice;

import butterknife.ButterKnife;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public class SelectTestActivity extends AppCompatActivity {

    private AlertDialog alertDialog;
    private CompositeSubscription subscriptions;
    private boolean closeApp = false;
    private boolean disconnect = false;
    private ImageButton startButton;
    private ImageButton startButton2;
    private ImageButton updateButton;
    FyssaApp app;
    private final String TAG = SelectTestActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_test);
        ButterKnife.bind(this);
        app = (FyssaApp) getApplication();
        subscriptions = new CompositeSubscription();

        alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.close_app)
                .setMessage(R.string.do_you_want_to_close_application)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    closeApp = true;
                    BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
                })
                .setNegativeButton(R.string.no, (dialog, which) -> alertDialog.dismiss())
                .create();

        startButton = (ImageButton) findViewById(R.id.start_button);
        startButton2 = (ImageButton) findViewById(R.id.start_button2);


        startButton.setOnClickListener(v -> {
            Log.d("ONCLICK", "Start the app");
            startActivity(new Intent(SelectTestActivity.this, FyssaMainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        });


        subscriptions.add(MdsRx.Instance.connectedDeviceObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mdsConnectedDevice -> {
                    // Stop refreshing
                    if (mdsConnectedDevice.getConnection() == null) {
                        Log.e(TAG, "call: Rx Disconnect");
                        if (closeApp) {
                            if (Build.VERSION.SDK_INT >= 21) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        } else if (disconnect) {
                            startActivity(new Intent(SelectTestActivity.this, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else {
                            ConnectionLostDialog.INSTANCE.showDialog(SelectTestActivity.this);
                        }
                    } else {
                        ConnectionLostDialog.INSTANCE.dismissDialog();
                        Log.e(TAG, "call: Rx Connect");
                    }
                }, new ThrowableToastingAction(this)));


        //startActivity(new Intent(SelectTestActivity.this, ClassDataManagerActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        subscriptions.unsubscribe();
    }
}

