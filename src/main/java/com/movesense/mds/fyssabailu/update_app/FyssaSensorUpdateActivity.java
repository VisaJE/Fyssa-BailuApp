package com.movesense.mds.fyssabailu.update_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.bailu_app.FyssaApp;
import com.movesense.mds.fyssabailu.bluetooth.BleManager;
import com.movesense.mds.fyssabailu.bluetooth.MdsRx;
import com.movesense.mds.fyssabailu.bluetooth.RxBle;
import com.movesense.mds.fyssabailu.model.FyssaDeviceInfo;
import com.movesense.mds.fyssabailu.scanner.ScannerFragment;
import com.movesense.mds.fyssabailu.scanner.UpdateScanActivity;
import com.movesense.mds.fyssabailu.update_app.dfu.DfuService;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by Eemil on 20.8.2018.
 */

public class FyssaSensorUpdateActivity extends AppCompatActivity implements ScannerFragment.DeviceSelectionListener, BleManager.IBleConnectionMonitor {

    FyssaApp app;

    @BindView(R.id.start_update) Button startUpdate;
    @BindView(R.id.dfu_select_device_btn2) Button dfuSelectDeviceBtn;

    private static final int PERMISSION_REQ = 25;
    private static final int ENABLE_BT_REQ = 0;

    private final String LOG_TAG = FyssaSensorUpdateActivity.class.getSimpleName();
    @BindView(R.id.dfu_uploading_tv2) TextView dfuUploadingTv;
    @BindView(R.id.dfu_uploading_percent_tv2) TextView dfuUploadingPercentTv;

    private static final String DATA_DEVICE = "device";
    private static final String DATA_STATUS = "status";
    private static final String DATA_DFU_COMPLETED = "dfu_completed";
    private static final String DATA_DFU_ERROR = "dfu_error";

    private static final String DFU_MAC_ADDRESS = "/Info";

    private boolean mStatusOk;
    private RxBleDevice selectedDevice = null;
    private boolean mDfuCompleted;
    private boolean mResumed;
    private String mDfuError;
    private boolean mIsDeviceReconnected = false;
    private boolean isDfuEnable = false;

    private BluetoothAdapter bluetoothAdapter;

    CompositeSubscription cSubscriptions;
    Boolean subscribed = false;
    ArrayList<RxBleDevice> devices;
    ArrayList<Integer> signalStrengths;
    String knownMac;

    ScannerFragment scannerFragment;

    int selectedFile = -1;
    boolean tryWithBootloader;
    boolean wasConnected = false;

    // Bootloaderfiles are automatically tried.
    String[] listFiles;
    int bootPadding;
    String[] listExplanations;
    @Override
    protected void onCreate(Bundle savedInstanceState ) {
        tryWithBootloader = false;
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");
        app = (FyssaApp)getApplication();
        listFiles= getResources().getStringArray(R.array.dfu_files);
        listExplanations= getResources().getStringArray(R.array.dfu_file_titles);
        bootPadding= listFiles.length/2;

        knownMac = null;

        setContentView(R.layout.activity_sensor_update);
        ButterKnife.bind(this);
        startUpdate.setEnabled(false);
        dfuSelectDeviceBtn.setEnabled(false);

        BleManager.INSTANCE.addBleConnectionMonitorListener(this);

        if (savedInstanceState != null) {
            selectedDevice = savedInstanceState.getParcelable(DATA_DEVICE);
            mStatusOk = mStatusOk || savedInstanceState.getBoolean(DATA_STATUS);

            new Handler().postDelayed(() -> startUpdate.setEnabled(selectedDevice != null && mStatusOk), 700);
            mDfuCompleted = savedInstanceState.getBoolean(DATA_DFU_COMPLETED);
            mDfuError = savedInstanceState.getString(DATA_DFU_ERROR);
        }

        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        assert manager != null;
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(LOG_TAG, "Location services unavailable!");

            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_location_on)
                    .setMessage(R.string.text_location_on)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        this.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        if (Build.VERSION.SDK_INT >= 21) {
                            finishAndRemoveTask();
                        } else {
                            finish();
                        }
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
        else if (!checkLocationPermission()) {
            Log.d(LOG_TAG, "Insufficient permissions!");
            startUpdate.setEnabled(false);
            dfuSelectDeviceBtn.setEnabled(false);
        } else {
            continueCreation();
        }
    }


    private void continueCreation() {
        isBLESupported();
        if (!isBLEEnabled()) {
            showBLEDialog();
        }

        // Ask For Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is not enable so run
            bluetoothAdapter.enable();
        }
        devices = new ArrayList<>();
        signalStrengths = new ArrayList<>();

        if (MovesenseConnectedDevices.getConnectedDevices() != null && MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
            Log.d(LOG_TAG, "Already connected to " + MovesenseConnectedDevices.getConnectedDevices().size() + " devices.");
            wasConnected = true;
            setupUpdate();
        }
        else {
            showConnectDialog();
        }

    }

    DfuProgressListener dfuProgressListener = new DfuProgressListener() {
        @Override
        public void onDeviceConnecting(String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDeviceConnecting");
        }

        @Override
        public void onDeviceConnected(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDeviceConnected");
        }

        @Override
        public void onDfuProcessStarting(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDfuProcessStarting");
        }

        @Override
        public void onDfuProcessStarted(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDfuProcessStarted");
        }

        @Override
        public void onEnablingDfuMode(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onEnablingDfuMode");
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onProgressChanged(@NonNull String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
            Log.d(LOG_TAG, "DfuProgress onProgressChanged percent: " + percent);
            dfuUploadingPercentTv.setText(String.format("%d%%", percent));
        }

        @Override
        public void onFirmwareValidating(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onFirmwareValidating");
        }

        @Override
        public void onDeviceDisconnecting(String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDeviceDisconnecting");
        }

        @Override
        public void onDeviceDisconnected(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDeviceDisconnected");
        }

        @Override
        public void onDfuCompleted(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDfuCompleted");
            dfuUploadingPercentTv.setText(R.string.dfu_status_completed);
            selectedFile = -1;
            mDfuCompleted = true;
            if (mResumed) {
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(() -> {
                    afterTransferCompleted();

                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    assert manager != null;
                    manager.cancel(DfuService.NOTIFICATION_ID);
                }, 200);
            }
        }


        @Override
        public void onDfuAborted(@NonNull String deviceAddress) {
            Log.d(LOG_TAG, "DfuProgress onDfuAborted");
            dfuUploadingPercentTv.setText(R.string.dfu_status_aborted);
            // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
            new Handler().postDelayed(() -> {
                clearUI();
                Toast.makeText(FyssaSensorUpdateActivity.this, "Dfu Aborted", Toast.LENGTH_SHORT).show();

                // if this activity is still open and upload process was completed, cancel the notification
                final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert manager != null;
                manager.cancel(DfuService.NOTIFICATION_ID);
            }, 200);
        }

        @Override
        public void onError(@NonNull String deviceAddress, int error, int errorType, String message) {
            Log.e(LOG_TAG, "DfuProgress onError " + error + " " + errorType + " " + message);
            if (!tryWithBootloader) {
                Log.d(LOG_TAG, "TRYING INSTALLATION WITH BOOTLOADER");
                tryWithBootloader = true;
                new Handler().postDelayed(() -> {
                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    assert manager != null;
                    manager.cancel(DfuService.NOTIFICATION_ID);
                    startUpdate();
                }, 1000);
                }
                else {
                if (mResumed) {
                    Log.e(LOG_TAG, "UPLOADING UTTERLY FAILED");
                    showErrorMessage(message);

                    // We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
                    new Handler().postDelayed(() -> {
                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        assert manager != null;
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }, 200);
                } else {
                    mDfuError = message;
                }
                }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.d(LOG_TAG, "onActivityResult");
        setContentView(R.layout.activity_sensor_update);
        ButterKnife.bind(this);
        startUpdate.setEnabled(false);
        dfuSelectDeviceBtn.setEnabled(false);
        if(resultCode == RESULT_OK){
            Log.d(LOG_TAG, "onActivityResult: Starting update procedure.");
            setupUpdate();
        }
        else finish();

    }

    private void setupUpdate(){
        DfuServiceListenerHelper.registerProgressListener(this, dfuProgressListener);
        checkDFUMac();
    }

    private void checkDFUMac() {
        if (MovesenseConnectedDevices.getConnectedDevices().size() <= 0) return;
        Log.d(LOG_TAG, "checkDFUMac: Looking for known mac from " + MdsRx.SCHEME_PREFIX +
                MovesenseConnectedDevices.getConnectedDevice(0).getSerial()+
                DFU_MAC_ADDRESS);
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial()+
                        DFU_MAC_ADDRESS,
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        Log.d(LOG_TAG, "onSuccess: Found info" + s);
                        FyssaDeviceInfo info = new Gson().fromJson(s, FyssaDeviceInfo.class);
                        try{
                            knownMac = info.getDfuAddress().replace('-', ':');
                            Log.d(LOG_TAG, info.getDfuAddress());
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Info wasn't suitable", e);
                            knownMac = null;
                        }
                        enableDfu();
                        skipDeviceScanningDialog();
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(LOG_TAG, "onError: Didn't get a mac address.", e);
                        enableDfu();
                        skipDeviceScanningDialog();
                    }
                });
    }


    @OnClick({R.id.dfu_select_device_btn2, R.id.start_update})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.start_update:
                isDfuEnable = true;
                if (!isBLEEnabled()) {
                    showBLEDialog();
                }
                if (selectedDevice == null) {
                    if (devices.size() == 1) {
                        selectedDevice = devices.get(0);
                        Log.d(LOG_TAG, "Starting update on found device");
                    }
                    else if(devices.size()>1) {
                        int i = findMySense();
                        if (i > -1) selectedDevice = devices.get(i);
                    }
                    else {
                        startUpdate.setEnabled(false);
                        return;
                    }
                }
                Log.d(LOG_TAG, "Found devices: " +devices.size());
                if (subscribed) {
                    subscribed = false;
                    cSubscriptions.unsubscribe();
                }
                devices.clear();
                signalStrengths.clear();
                dfuUploadingPercentTv.setVisibility(View.VISIBLE);
                dfuUploadingTv.setVisibility(View.VISIBLE);
                startUpdate.setEnabled(false);
                dfuSelectDeviceBtn.setEnabled(false);
                Log.d(LOG_TAG, "Beginning update!");
                Log.d(LOG_TAG, "Name: " + selectedDevice.getBluetoothDevice().getName());
                Log.d(LOG_TAG, "Address: " + selectedDevice.getBluetoothDevice().getAddress());


                new AlertDialog.Builder(this)
                        .setTitle("Software version")
                        .setCancelable(false)
                        .setItems(listExplanations, (dialogInterface, i) -> {
                            dialogInterface.dismiss();
                            selectedFile = i;
                            startUpdate();
                        })
                        .create().show();
                break;
            case R.id.dfu_select_device_btn2:
                if (!isBLEEnabled()) {
                    showBLEDialog();
                }

                showDeviceScanningDialog();
                break;

        }
    }


    private void enableDfu() {
        Log.d(LOG_TAG, "enableDfu()");
        if ((BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable && isDfuEnable) || MovesenseConnectedDevices.getConnectedDevices().size() == 0) {
            Log.d(LOG_TAG, "enableDfu() BleManager.isReconnectToLastConnectedDeviceEnable, or no device available!");
            //Toast.makeText(this, "Dfu Already Enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        Mds.builder().build(this).put(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/System/Mode",
                "{\"NewState\":12}", new MdsResponseListener() {
                    @Override
                    public void onSuccess(String data) {
                        Log.e(LOG_TAG, "onSuccess(): " + data);
                        isDfuEnable = true;
                        BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;
                        if (!wasConnected) disconnectFromDevices();
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "onError(): ", error);
                        isDfuEnable = false;
                        Toast.makeText(FyssaSensorUpdateActivity.this, "Failed to enable DFU.", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void showDeviceScanningDialog() {
        scannerFragment = new ScannerFragment();
        scannerFragment.show(getSupportFragmentManager(), ScannerFragment.class.getName());
    }


    // Capture instance of RxBleClient to make code look cleaner
    RxBleClient rxBleClient = RxBle.Instance.getClient();

    private void skipDeviceScanningDialog() {
        Log.d(LOG_TAG, "Subscribing to rxBle devices.");
        cSubscriptions = new CompositeSubscription();
        subscribed = true;
        cSubscriptions.add(rxBleClient.scanBleDevices()
                .subscribe(rxBleScanResult -> {
                    //Log.d(LOG_TAG, "Found device name " + rxBleScanResult.getBleDevice().getName() + " mac: " + rxBleScanResult.getBleDevice().getMacAddress());
                    String dName = rxBleScanResult.getBleDevice().getName();
                    if (dName != null && dName.equals("DfuTarg")) {
                        if (!exists(devices, rxBleScanResult.getBleDevice())) {
                            if (knownMac == null || rxBleScanResult.getBleDevice().getMacAddress().equals(knownMac))
                            Log.d(LOG_TAG, "Found device!");
                            devices.add(rxBleScanResult.getBleDevice());
                            signalStrengths.add(rxBleScanResult.getRssi());
                            new Handler().postDelayed(() -> startUpdate.setEnabled(true), 500);

                        }
                    }
                },  new ThrowableToastingAction(this)));

    }

    private void showConnectDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.connect_for_update)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    dfuSelectDeviceBtn.setEnabled(true);
                    setupUpdate();
                    if (!isBLEEnabled()) {
                        showBLEDialog();
                    }
                    showDeviceScanningDialog();
                })
                .setNegativeButton(R.string.no, (dialog, which) -> startActivityForResult(new Intent(FyssaSensorUpdateActivity.this, UpdateScanActivity.class), 1))
                .create().show();
    }

    @Override
    public void onDeviceSelected(RxBleDevice device) {
        Log.d(LOG_TAG, "onDeviceSelected()");
        selectedDevice = device;
        scannerFragment.dismiss();
        if (subscribed) {
            subscribed = false;
            cSubscriptions.unsubscribe();
        }
        new Handler().postDelayed(() -> startUpdate.setEnabled(true), 500);

    }

    private void startUpdate() {
        if (tryWithBootloader) {
            dfuUploadingTv.setText(R.string.uploading_bootloader);
        } else {
            dfuUploadingTv.setText(R.string.uploading);
        }
        DfuServiceInitiator serviceInitiator = new DfuServiceInitiator(selectedDevice.getBluetoothDevice().getAddress())
                .setDeviceName(selectedDevice.getBluetoothDevice().getName())
                .setKeepBond(false)
                .setForceDfu(false)
                .setPacketsReceiptNotificationsEnabled(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                .setPacketsReceiptNotificationsValue(DfuServiceInitiator.DEFAULT_PRN_VALUE)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);

        int i = tryWithBootloader ? selectedFile + bootPadding : selectedFile;


        Log.d(LOG_TAG, "Starting update with " + this.getResources().getIdentifier(listFiles[i], "raw", this.getPackageName()));
        serviceInitiator.setZip(this.getResources().getIdentifier(listFiles[i], "raw", this.getPackageName()));
        serviceInitiator.start(this, DfuService.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(LOG_TAG, "onResume");
        mResumed = true;

        if (mDfuCompleted) {
            afterTransferCompleted();
        }

        if (mDfuError != null)
            if (!tryWithBootloader) {
                Log.d(LOG_TAG, "TRYING INSTALLATION WITH BOOTLOADER");
                tryWithBootloader = true;
                startUpdate();
            } else {
                Log.e(LOG_TAG, "DFU TOTALLY FAILED");
                showErrorMessage(mDfuError);
            }
        if (mDfuCompleted && mDfuError != null) {
            // if this activity is still open and upload process was completed, cancel the notification
            final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.cancel(DfuService.NOTIFICATION_ID);
            mDfuError = null;
        }
    }

    private void afterTransferCompleted() {

        if (!isDfuEnable) return;
        clearUI();

        isDfuEnable = false;

        if (wasConnected) {
            Log.d(LOG_TAG, "Was connected, reconnecting.");
            BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = true;
            dfuUploadingTv.setVisibility(View.VISIBLE);
            dfuUploadingTv.setText(R.string.reconnecting_to);
            // Reconnect to last connected device
            try {
                new Handler().postDelayed(() -> MdsRx.Instance.reconnect(FyssaSensorUpdateActivity.this), 2000);

            } catch (Exception e) {
                Log.e(LOG_TAG, "Was connected, reconnecting failed.", e);
                BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;
                startActivity(new Intent(FyssaSensorUpdateActivity.this,
                        MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
        } else {
            Log.d(LOG_TAG, "Wasnt connected, finishing.");
            finish();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;

        // save the data
        Log.e(LOG_TAG, "onPause");
    }

    @Override
    public void onBackPressed() {
        if (isDfuEnable) {
            new AlertDialog.Builder(this)
                    .setTitle("Dfu Mode")
                    .setMessage("DFU operations are running. Please wait until process will be finished.")
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss()).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.e(LOG_TAG, "onSaveInstanceState");

        outState.putParcelable(DATA_DEVICE, selectedDevice != null ? selectedDevice.getBluetoothDevice() : null);
        outState.putBoolean(DATA_STATUS, mStatusOk);
        outState.putBoolean(DATA_DFU_COMPLETED, mDfuCompleted);
        outState.putString(DATA_DFU_ERROR, mDfuError);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DfuServiceListenerHelper.unregisterProgressListener(this, dfuProgressListener);
        Log.e(LOG_TAG, "onDestroy");
    }


    private boolean isBLEEnabled() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        assert manager != null;
        BluetoothAdapter adapter = manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }


    private void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, ENABLE_BT_REQ);
    }

    private void isBLESupported() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "no Ble", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public static void disconnectFromDevices(){
        while (MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
            MovesenseConnectedDevices.removeConnectedDevice((MovesenseConnectedDevices.getConnectedDevice(0)));
        }
        while (MovesenseConnectedDevices.getRxMovesenseConnectedDevices().size() > 0) {
            BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
            MovesenseConnectedDevices.removeRxConnectedDevice(MovesenseConnectedDevices.getConnectedRxDevice(0));
        }
    }


    private void clearUI() {
        dfuUploadingPercentTv.setVisibility(View.INVISIBLE);
        dfuUploadingTv.setVisibility(View.INVISIBLE);
        dfuSelectDeviceBtn.setEnabled(false);
        startUpdate.setEnabled(false);
        mStatusOk = false;
    }

    @Override
    public void onDisconnect(RxBleDevice rxBleDevice) {
        Log.e(LOG_TAG, "onDisconnect: " + rxBleDevice.getMacAddress());

        mIsDeviceReconnected = false;
    }

    @Override
    public void onConnect(RxBleDevice rxBleDevice) {
        Log.d(LOG_TAG, "onConnect: " + rxBleDevice.getMacAddress() + "Installed bootloader: " + tryWithBootloader);

        mIsDeviceReconnected = true;
        boolean longerWait = tryWithBootloader;
        runOnUiThread(() -> {
            Toast.makeText(FyssaSensorUpdateActivity.this, longerWait? R.string.connected_after_bootloader : R.string.connected_after_update, Toast.LENGTH_SHORT).show();
            if (mDfuCompleted) {
                new Handler(getMainLooper()).postDelayed(() -> {
                    Log.d(LOG_TAG, "Reconnected, finishing." + (longerWait?"Bootloader installed":"Not bootloaded"));
                    BleManager.INSTANCE.removeBleConnectionMonitorListener(FyssaSensorUpdateActivity.this);
                    finish();
                    //startActivity(new Intent(FyssaSensorUpdateActivity.this,
                      //      FyssaMainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                }, longerWait ? 13000:10000);
            }
            else Log.e(LOG_TAG, "Reconnected without dfu completion.");
        });


    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0
                        || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.text_location_on, Toast.LENGTH_SHORT).show();

                } /*else {
                                        if (!checkStorageAccess()) {
                        Log.d(LOG_TAG, "Not right storage permissions");
                        startUpdate.setEnabled(false);
                        dfuSelectDeviceBtn.setEnabled(false);
                    }
                }*/
            }
        }
    }


    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_location_permission)
                        .setMessage(R.string.text_location_permission)
                        .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                            //Prompt the user once explanation has been shown
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    MY_PERMISSIONS_REQUEST_LOCATION);
                        })
                        .create()
                        .show();

            } else {
                // No explanation needed, we can request the permission.
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }


    private void showErrorMessage(final String message) {
        clearUI();
        Toast.makeText(this, "Upload failed: " + message, Toast.LENGTH_SHORT).show();
    }


    private int findMySense() {
        if (knownMac != null) {
            for (RxBleDevice i : devices) {
                if (i.getMacAddress().equals(knownMac)) return devices.indexOf(i);
            }
        }
        Integer highest = 0;
        Integer secondHighest = -1;
        for (Integer i : signalStrengths) {
            if (highest < i) {
                secondHighest = highest;
                highest = i;
            } else if (secondHighest < i) secondHighest = i;
        }
        // Requiring 2/3rds higher strength to filter the right one.
        if (secondHighest*5/3 < highest) return signalStrengths.indexOf(highest);
        return -1;
    }

    private boolean exists(ArrayList<RxBleDevice> listed, RxBleDevice device) {
        boolean res = false;
        for (int i = 0; i < listed.size(); i++) {
            if (listed.get(i).getMacAddress().equals(device.getMacAddress())) res = true;
        }
        return res;
    }

}



