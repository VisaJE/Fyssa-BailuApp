package com.movesense.mds.fyssabailu.bailu_app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.movesense.mds.fyssabailu.DataSender;
import com.movesense.mds.fyssabailu.DataUser;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.RxBle;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;

import java.util.AbstractQueue;
import java.util.Queue;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;


// Mostly copied from ScanFragment.java
public class FyssaObserver extends AppCompatActivity implements DataUser {

    public static Activity enclosingClass;
    private final String LOG_TAG = this.getClass().getCanonicalName();

    @BindView(R.id.info_tv)
    TextView infoTv;




    private BluetoothAdapter bluetoothAdapter;

    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private RxBleClient rxBleClient;
    private CompositeSubscription subscriptions;
    private FyssaDeviceView deviceView;
    DataSender dataSender;

    private FyssaApp app;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fyssa_observe);
        ButterKnife.bind(this);
        enclosingClass = this;

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
        infoTv.setText("---Scanning for parties---");
        createView();
        dataSender = new DataSender(FyssaObserver.this.getCacheDir(), this);
        startScanning();
    }


    protected void createView() {
        // Set up list and adapter for scanned devices
        deviceView = new FyssaDeviceView();
        RecyclerView deviceList = (RecyclerView) findViewById(R.id.partiers_view);
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(deviceView);
        //deviceList.setItemAnimator(null);
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


    void checkScanResult(RxBleScanResult scanResult) {
        RxBleDevice device = scanResult.getBleDevice();
        //Log.d(LOG_TAG, "Found device " + device.getName()+ "\n");
        //Log.d(LOG_TAG, bytesToHex(scanResult.getScanRecord()));
        if (device.getName() != null && device.getName().contains("Movesense")) {
            byte[] adv = scanResult.getScanRecord();
            if (adv[11] == (byte)0xEA) {
                Integer score = ((adv[7] & 0xFF) << 8) | (adv[8] & 0xFF);
                Integer timePartying = ((adv[9]&0xFF) << 8) | (adv[10] & 0xFF);
                //Log.d(LOG_TAG, "Getting advertisement " + score + ", " + timePartying + " from device" + device.getMacAddress() + "\n");
                //Log.d(LOG_TAG, "Full hex adv: " + bytesToHex(adv) + "\n");
                handleScanResult(device, score, timePartying);
            }
        }

    }
    private void handleScanResult(RxBleDevice rxBleScanResult, Integer score, Integer timePartying) {
        if (deviceView.nameMap.containsKey(rxBleScanResult.getMacAddress())) deviceView.handle(rxBleScanResult, score, timePartying);
        else {
            Log.d(LOG_TAG, "No name was found for " + rxBleScanResult.getMacAddress()
            );
            dataSender.get(FyssaApp.SERVER_GET_URL +rxBleScanResult.getMacAddress());
            deviceView.handle(rxBleScanResult, score, timePartying);
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
                        checkScanResult(rxBleScanResult);
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
        finish();
        startActivity(new Intent(FyssaObserver.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));

    }

    @Override
    public void onGetSuccess(String response) {
        Log.d(LOG_TAG, "onGetSucces:" +response);
        deviceView.nameMap.put(response.substring(0, 17), response.substring(17));
        /*for (String i : deviceView.nameMap.keySet()) {
            Log.d(LOG_TAG, "Found in nameMap:" + i + ">" + deviceView.nameMap.get(i));
        }*/
    }

    @Override
    public void onGetError(VolleyError error) {
        toast("Error while getting a name");
    }

    @Override
    public void onPostSuccess(String response) {

    }

    @Override
    public void onPostError(VolleyError error) {

    }
    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        deviceView.timer.cancel();
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        deviceView.schedule();
    }
    @Override
    protected void onPause() {
        super.onPause();
        deviceView.stopTimer();
    }
}

