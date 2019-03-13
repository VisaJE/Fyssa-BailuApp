package com.movesense.mds.fyssabailu.bailu_app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.bluetooth.RxBle;
import com.movesense.mds.fyssabailu.online.DataSender;
import com.movesense.mds.fyssabailu.online.DataUser;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;


// Mostly copied from ScanFragment.java
public class FyssaObserverActivity extends AppCompatActivity implements DataUser {

    public static Activity enclosingClass;
    private final String LOG_TAG = this.getClass().getCanonicalName();

    @BindView(R.id.info_tv)
    TextView infoTv;

    private BluetoothAdapter bluetoothAdapter;

    private static final int COARSE_PERMISSIONS_REQUEST_LOCATION = 99;
    private static final int FINE_PERMISSIONS_REQUEST_LOCATION = 97;

    private RxBleClient rxBleClient;
    private CompositeSubscription subscriptions;
    private FyssaDeviceView deviceView;
    DataSender dataSender;


    private FyssaApp app;
    private FyssaGeocoder geocoder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fyssa_observe);
        ButterKnife.bind(this);
        enclosingClass = this;

        app = (FyssaApp) getApplication();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
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
        dataSender = new DataSender(FyssaObserverActivity.this.getCacheDir(), this);
        if (checkLocationPermission()) {
            geocoder = new FyssaGeocoder(this,(LocationManager) this.getSystemService(LOCATION_SERVICE));
            if (checkCoarseLocationPermission()) startScanning();
        }

    }


    protected void createView() {
        // Set up list and adapter for scanned devices
        deviceView = new FyssaDeviceView();
        RecyclerView deviceList = findViewById(R.id.partiers_view);
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(deviceView);
        //deviceList.setItemAnimator(null);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
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
            if (adv[11] == (byte) 0xEA) {
                Integer score = ((adv[7] & 0xFF) << 8) | (adv[8] & 0xFF);
                Integer timePartying = ((adv[9] & 0xFF) << 8) | (adv[10] & 0xFF);
                //Log.d(LOG_TAG, "Getting advertisement " + score + ", " + timePartying + " from device" + device.getMacAddress() + "\n");
                //Log.d(LOG_TAG, "Full hex adv: " + bytesToHex(adv) + "\n");
                handleScanResult(device, score, timePartying);
            }
        }

    }

    private void handleScanResult(RxBleDevice rxBleScanResult, Integer score, Integer timePartying) {
        if (deviceView.knowsDevice(rxBleScanResult.getMacAddress()))
            deviceView.handle(rxBleScanResult, score, timePartying);
        else {
            Log.d(LOG_TAG, "No name was found for " + rxBleScanResult.getMacAddress()
            );
            dataSender.get(FyssaApp.SERVER_GET_URL + rxBleScanResult.getMacAddress());
        }
        // Testing for location info
        if (geocoder.isEnabled()) Log.d(LOG_TAG, geocoder.getLocationInfo());
    }

    private void sendParty() {
        Log.d(LOG_TAG, "Sending a party");
        if (deviceView.getItemCount() >= 2 || deviceView.getScore() > 10) {
            dataSender.post(FyssaApp.SERVER_GET_PARTY_URL + "?place=" +
                    geocoder.getLocationInfo() + "&longitude=" + geocoder.getLongitude() +
                    "&latitude=" + geocoder.getLatitude() + "&population=" + deviceView.getItemCount() +
                    "&score=" + deviceView.getScore());
        }
        else toast(getString(R.string.dont_send));

    }

    private void getParties() {
        Log.d(LOG_TAG, "Getting parties");
        dataSender.get(FyssaApp.SERVER_GET_PARTY_URL);
    }


    @OnClick({R.id.get_party, R.id.send_party})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.get_party:
                getParties();
                break;
            case R.id.send_party:
                sendParty();
                break;
        }
    }

    private boolean checkCoarseLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.title_location_permission)
                            .setMessage(R.string.text_location_permission)
                            .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                                //Prompt the user once explanation has been shown
                                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                        COARSE_PERMISSIONS_REQUEST_LOCATION);
                            })
                            .create()
                            .show();
                } else {
                    // No explanation needed, we can request the permission.
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            COARSE_PERMISSIONS_REQUEST_LOCATION);
                }
            }
            return false;
        } else {
            return true;
        }
    }

    private boolean checkLocationPermission() {
        if (true || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.title_location_permission)
                            .setMessage(R.string.text_fine_location_permission)
                            .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                                //Prompt the user once explanation has been shown
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        FINE_PERMISSIONS_REQUEST_LOCATION);
                            })
                            .create()
                            .show();

                } else {
                    // No explanation needed, we can request the permission.
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            FINE_PERMISSIONS_REQUEST_LOCATION);
                }
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case FINE_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(LOG_TAG, "Got fine location permissions!");

                    LocationManager logMan = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    if (geocoder == null) geocoder = new FyssaGeocoder(getApplicationContext(), logMan);
                    startScanning();
                }
                else {
                    checkCoarseLocationPermission();
                }
                return;
            }
            case COARSE_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(LOG_TAG, "Got coarse location permissions!");
                    startScanning();
                }
            }
            default:
                Log.d(LOG_TAG, "onRequestPermissionResult fall through" + requestCode);
        }
    }


    private void startScanning() {

        Log.d(LOG_TAG, "START SCANNING !!!");
        // Start scanning
        subscriptions.clear();
        subscriptions.add(rxBleClient.scanBleDevices()
                .subscribe(rxBleScanResult -> checkScanResult(rxBleScanResult), new ThrowableToastingAction(this)));
    }


    @Override
    public void onBackPressed() {
        subscriptions.unsubscribe();
        subscriptions.clear();
        finish();
        startActivity(new Intent(FyssaObserverActivity.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));

    }

    @Override
    public void onGetSuccess(String response) {
        Log.d(LOG_TAG, "onGetSucces:" +response);
        if (response.length() > 17 && response.charAt(2) == ':' && response.charAt(5) == ':' && response.charAt(8) == ':')
            deviceView.putDevice(response.substring(0, 17), response.substring(17));
        else toast(response);

        /*for (String i : deviceView.nameMap.keySet()) {
            Log.d(LOG_TAG, "Found in nameMap:" + i + ">" + deviceView.nameMap.get(i));
        }*/
    }

    @Override
    public void onGetError(VolleyError error) {
        Log.e(LOG_TAG, "Error while reaching server", error);
    }

    @Override
    public void onPostSuccess(String response) {

    }

    @Override
    public void onPostError(VolleyError error) {
        Log.e(LOG_TAG, "Error while reaching server", error);
    }
    private void toast(String text) {
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
    }
    @Override
    protected void onPause() {
        super.onPause();
    }

}

