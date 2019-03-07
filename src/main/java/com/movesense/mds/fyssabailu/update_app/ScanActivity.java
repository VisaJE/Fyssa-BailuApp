package com.movesense.mds.fyssabailu.update_app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.movesense.mds.fyssabailu.BleManager;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.MdsRx;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.ScanFragment;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.bailu_app.FyssaApp;
import com.movesense.mds.fyssabailu.bailu_app.FyssaMainActivity;
import com.movesense.mds.fyssabailu.model.MdsConnectedDevice;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoNewSw;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoOldSw;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseDevice;
import com.polidea.rxandroidble.RxBleDevice;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public abstract class ScanActivity  extends AppCompatActivity implements ScanFragment.DeviceSelectionListener {
    private final String TAG = ScanActivity.class.getSimpleName();

    private CompositeSubscription subscriptions;
    FyssaApp app;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (FyssaApp) getApplication();

        subscriptions = new CompositeSubscription();
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            Log.d(TAG, "getting scanFragment");
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content, new ScanFragment(), ScanFragment.class.getSimpleName())
                    .commit();
        }

    }

    protected abstract void continueToActivity();

    @Override
    public void onDeviceSelected(final RxBleDevice device) {
        Log.d(TAG, "onDeviceSelected: " + device.getName() + " (" + device.getMacAddress() + ")");
        MdsRx.Instance.connect(device, this);
        BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = true;
        ConnectingDialog.INSTANCE.showDialog(this);

        // Monitor for connected devices
        subscriptions.add(MdsRx.Instance.connectedDeviceObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<MdsConnectedDevice>() {
                    @Override
                    public void call(MdsConnectedDevice mdsConnectedDevice) {
                        // Stop refreshing
                        if (mdsConnectedDevice.getConnection() != null) {
                            ConnectingDialog.INSTANCE.dismissDialog();
                            // Add connected device
                            // Fixme: this should be deleted after 1.0 SW release

                            if (mdsConnectedDevice.getDeviceInfo() instanceof MdsDeviceInfoNewSw) {
                                MdsDeviceInfoNewSw mdsDeviceInfoNewSw = (MdsDeviceInfoNewSw) mdsConnectedDevice.getDeviceInfo();
                                MovesenseConnectedDevices.addConnectedDevice(new MovesenseDevice(
                                        device.getMacAddress(),
                                        mdsDeviceInfoNewSw.getDescription(),
                                        mdsDeviceInfoNewSw.getSerial(),
                                        mdsDeviceInfoNewSw.getSw()));
                            } else if (mdsConnectedDevice.getDeviceInfo() instanceof MdsDeviceInfoOldSw) {
                                MdsDeviceInfoOldSw mdsDeviceInfoOldSw = (MdsDeviceInfoOldSw) mdsConnectedDevice.getDeviceInfo();
                                MovesenseConnectedDevices.addConnectedDevice(new MovesenseDevice(
                                        device.getMacAddress(),
                                        mdsDeviceInfoOldSw.getDescription(),
                                        mdsDeviceInfoOldSw.getSerial(),
                                        mdsDeviceInfoOldSw.getSw()));
                            }
                            // We have a new SdsDevice
                            subscriptions.unsubscribe();
                            subscriptions.clear();
                            continueToActivity();
                        }
                    }
                }, new ThrowableToastingAction(this)));
    }
    @Override
    public void onBackPressed() {
        subscriptions.clear();
        startActivity(new Intent(ScanActivity.this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

}
