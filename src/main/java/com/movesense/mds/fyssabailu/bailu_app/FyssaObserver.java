package com.movesense.mds.fyssabailu.bailu_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.RxBle;
import com.movesense.mds.fyssabailu.ScanFragment;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;

import butterknife.ButterKnife;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;


// Mostly copied from ScanFragment.java
public class FyssaObserver extends AppCompatActivity {

    private final String LOG_TAG = this.getClass().getCanonicalName();

    private BluetoothAdapter bluetoothAdapter;

    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private RxBleClient rxBleClient;
    private CompositeSubscription subscriptions;

    private FyssaApp app;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fyssa_observe);
        ButterKnife.bind(this);


        app = (FyssaApp) getApplication();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Fyssasensori");
        }
        // Ask For Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is not enable so run
            bluetoothAdapter.enable();
        }

        // Capture instance of RxBleClient to make code look cleaner
        rxBleClient = RxBle.Instance.getClient();

        // Create one composite subscription to hold everything
        subscriptions = new CompositeSubscription();

        startScanning();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    void handleScanResult(RxBleScanResult scanResult) {
        RxBleDevice device = scanResult.getBleDevice();
        if (device.getName() != null && device.getName().contains("Movesense")) {
            byte[] adv = scanResult.getScanRecord();
            byte startSize = adv[0];
            byte advSize = adv[startSize + 1];
            if (advSize == 0x7) {
                Integer score = ((adv[7]) << 8) | adv[8];
                Integer timePartying = ((adv[9]) << 8) | adv[10];
                Log.d(LOG_TAG, "Getting advertisement " + score + ", " + timePartying + " from device" + device.getMacAddress());
                Log.d(LOG_TAG, "Full hex adv: " + bytesToHex(adv));
            }
        }

    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.title_location_permission)
                            .setMessage(R.string.text_location_permission)
                            .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                                @RequiresApi(api = Build.VERSION_CODES.M)
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //Prompt the user once explanation has been shown
                                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                            MY_PERMISSIONS_REQUEST_LOCATION);
                                }
                            })
                            .create()
                            .show();

                } else {
                    // No explanation needed, we can request the permission.
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            MY_PERMISSIONS_REQUEST_LOCATION);
                }
            }
            return false;
        } else {
            return true;
        }
    }

    private void startScanning() {
        // Make sure we have location permission
        if (!checkLocationPermission()) {
            return;
        }

        Log.d(LOG_TAG, "START SCANNING !!!");
        // Start scanning
        subscriptions.add(rxBleClient.scanBleDevices()
                .subscribe(new Action1<RxBleScanResult>() {
                    @Override
                    public void call(RxBleScanResult rxBleScanResult) {
                        handleScanResult(rxBleScanResult);
                    }
                }, new ThrowableToastingAction(this)));
    }

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                    // The user bluetooth is ready to use.

                    // start scanning again in case of ready Bluetooth
                    startScanning();
                    return;
                }

                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    // The user bluetooth is turning off yet, but it is not disabled yet.
                    return;
                }

                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    // The user bluetooth is already disabled.
                    return;
                }

            }
        }
    };
    @Override
    public void onBackPressed() {
        subscriptions.unsubscribe();
        subscriptions.clear();
        startActivity(new Intent(FyssaObserver.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));

    }
}
