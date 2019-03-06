package com.movesense.mds.fyssabailu.adb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.mds.fyssabailu.bluetooth.MdsRx;
import com.movesense.mds.fyssabailu.bluetooth.RxBle;
import com.movesense.mds.fyssabailu.model.MdsConnectedDevice;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoNewSw;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoOldSw;
import com.movesense.mds.fyssabailu.update_app.FormatHelper;
import com.movesense.mds.internal.connectivity.BleManager;
import com.movesense.mds.internal.connectivity.MovesenseConnectedDevices;
import com.movesense.mds.internal.connectivity.MovesenseDevice;

import com.polidea.rxandroidble.RxBleScanResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public class AdbBridge extends BroadcastReceiver {
    public static final String CONNECTED_WITH = "Connected with: ";

    // Connection example
    // We need to grant permission before we can scan BLE devices
    // adb shell pm grant com.movesense.mds.sampleapp android.permission.ACCESS_COARSE_LOCATION
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type connect --es address "macAddress"

    // DFU
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type dfu_update -es address "macAddress" -es "dfu_address"

    // Led
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type put --es path Component/Led --es value '''{\"isOn\":true}'''

    // Linear Acc Subscribe
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type subscribe --es path "Meas/Acc/26"
    // Linear Acc Unsubscribe
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type unsubscribe --es path Meas/Acc/26

    // Dfu Update
    // You must be connected before dfu update
    // adb shell am broadcast -a android.intent.action.MOVESENSE --es type dfu_update --es file_path "file_name_in_external_storage_folder"

    private final String LOG_TAG = AdbBridge.class.getSimpleName();
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";

    private static final CompositeSubscription mCompositeSubscription = new CompositeSubscription();
    private static final CompositeSubscription mScanningCompositeSubscription = new CompositeSubscription();

    private static Map<String, MdsSubscription> mSubscriptions = new HashMap<>();
    private String mDevice_name;
    private static String file_path;
    private boolean isConnecting = false;
    private Context mContext;

    private String type;
    private String path;
    private String value;
    private String id;
    private String movesense_mac_address;
    private String dfu_mac_address;

    private static final String TYPE = "type";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private static final String ID = "id";
    private static final String ADDRESS = "address";
    private static final String DFU_ADDRESS = "dfu_address";
    private static final String FILE_PATH = "file_path";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(LOG_TAG, "onReceive()");

        mContext = context;

        if (intent.getExtras() == null) {
            Log.i(LOG_TAG, "No extras");
            return;
        }

        try {

            if (intent.hasExtra(TYPE)) {
                type = intent.getStringExtra(TYPE).toLowerCase();
                Log.d(LOG_TAG, "onReceive type: " + type);
            }

            if (intent.hasExtra(PATH)) {
                path = intent.getStringExtra(PATH);
                Log.d(LOG_TAG, "onReceive path: " + path);
            }

            if (intent.hasExtra(VALUE)) {
                value = intent.getStringExtra(VALUE);
                Log.d(LOG_TAG, "onReceive value: " + value);
            }

            if (intent.hasExtra(ID)) {
                id = intent.getStringExtra(ID);
                Log.d(LOG_TAG, "onReceive id: " + id);
            }

            if (intent.hasExtra(ADDRESS)) {
                movesense_mac_address = intent.getStringExtra(ADDRESS);
                Log.d(LOG_TAG, "onReceive address: " + movesense_mac_address);
            }

            if (intent.hasExtra(DFU_ADDRESS)) {
                dfu_mac_address = intent.getStringExtra(DFU_ADDRESS);
                Log.d(LOG_TAG, "onReceive dfu_address: " + dfu_mac_address);
            }

            if (intent.hasExtra(FILE_PATH)) {
                file_path = intent.getStringExtra(FILE_PATH);
                Log.d(LOG_TAG, "onReceive file_path: " + file_path);
            }

        } catch (Exception e) {
            Log.i(LOG_TAG, "Extras error");
            return;
        }

        if (type != null && !type.equals("connect") && !type.equals("dfu_update")) {
            //No connected device
            if (MovesenseConnectedDevices.getConnectedDevices().size() == 0) {
                Log.i(LOG_TAG, "No devices connected");
                return;
            }
        } else {
            if (movesense_mac_address == null || movesense_mac_address.isEmpty()) {
                Log.i(LOG_TAG, "No address specified for connection");
            }
        }

        final Mds build = Mds.builder().build(context);


        if (type.equals("subscribe")) {
            MdsSubscription mdsSubscription = build.subscribe(URI_EVENTLISTENER,
                    FormatHelper.formatContractToJson(
                            MovesenseConnectedDevices.getConnectedDevice(0).getSerial(),
                            path),
                    new MdsNotificationListener() {
                        @Override
                        public void onNotification(String data) {
                            Log.d(LOG_TAG, "ID:" + id + " " + path + " OUTPUT: " + data);

                        }

                        @Override
                        public void onError(MdsException error) {
                            Log.e(LOG_TAG, "onError(): ", error);
                        }
                    });

            mSubscriptions.put(path, mdsSubscription);
            MdsSubscription sub = mSubscriptions.get(path);
        } else if (type.equals("unsubscribe")) {
            try {
                MdsSubscription sub = mSubscriptions.get(path);
                if (sub != null)
                    sub.unsubscribe();
                mSubscriptions.remove(path);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onError(): ", e);
            }
        } else if (type.equals("get")) {
            build.get(MdsRx.SCHEME_PREFIX + MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/" + path,
                    value, new MdsResponseListener() {
                        @Override
                        public void onSuccess(String data) {
                            Log.d(LOG_TAG, "ID:" + id + " " + path + " OUTPUT: " + data);
                        }

                        @Override
                        public void onError(MdsException error) {
                            Log.e(LOG_TAG, "onError()", error);
                        }
                    });
        } else if (type.equals("put")) {
            build.put(MdsRx.SCHEME_PREFIX + MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/" + path,
                    value, new MdsResponseListener() {
                        @Override
                        public void onSuccess(String data) {
                            Log.d(LOG_TAG, "ID:" + id + " " + path + " OUTPUT: " + data);
                        }

                        @Override
                        public void onError(MdsException error) {
                            Log.e(LOG_TAG, "onError()", error);
                        }
                    });
        } else if (type.equals("post")) {
            build.post(MdsRx.SCHEME_PREFIX + MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/" + path,
                    value, new MdsResponseListener() {
                        @Override
                        public void onSuccess(String data) {
                            Log.d(LOG_TAG, "ID:" + id + " " + path + " OUTPUT: " + data);
                        }

                        @Override
                        public void onError(MdsException error) {
                            Log.e(LOG_TAG, "onError()", error);
                        }
                    });

        } else if (type.equals("delete")) {
            build.delete(MdsRx.SCHEME_PREFIX + MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/" + path,
                    value, new MdsResponseListener() {
                        @Override
                        public void onSuccess(String data) {
                            Log.d(LOG_TAG, "ID:" + id + " " + path + " OUTPUT: " + data);
                        }

                        @Override
                        public void onError(MdsException error) {
                            Log.e(LOG_TAG, "onError()", error);
                        }
                    });

        } else if (type.equals("connect")) {
            try {
                mScanningCompositeSubscription.add(RxBle.Instance.getClient().scanBleDevices()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<RxBleScanResult>() {
                            @Override
                            public void call(RxBleScanResult scanResult) {
                                Log.d(LOG_TAG, "scanResult: " + scanResult.getBleDevice().getName() + " : " +
                                        scanResult.getBleDevice().getMacAddress());
                                if (!isConnecting && movesense_mac_address.equals(scanResult.getBleDevice().getMacAddress())) {
                                    Log.e(LOG_TAG, "scanResult: FOUND DEVICE FROM INTENT Connecting..." + scanResult.getBleDevice().getName() + " : " +
                                            scanResult.getBleDevice().getMacAddress());
                                    isConnecting = true;
                                    Mds.builder().build(mContext).connect(scanResult.getBleDevice().getMacAddress(), null);
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Log.e(LOG_TAG, "BEFORE CONNECT YOU NEED GRANT LOCATION PERMISSION !!!");
                                Log.e(LOG_TAG, "Connect Error: ", throwable);
                            }
                        }));

                mCompositeSubscription.add(MdsRx.Instance.connectedDeviceObservable()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<MdsConnectedDevice>() {
                            @Override
                            public void call(MdsConnectedDevice mdsConnectedDevice) {
                                if (mdsConnectedDevice.getConnection() != null) {

                                    mScanningCompositeSubscription.clear();

                                    if (mdsConnectedDevice.getDeviceInfo() instanceof MdsDeviceInfoNewSw) {
                                        MdsDeviceInfoNewSw mdsDeviceInfoNewSw = (MdsDeviceInfoNewSw) mdsConnectedDevice.getDeviceInfo();
                                        MovesenseConnectedDevices.addConnectedDevice(new MovesenseDevice(
                                                mdsDeviceInfoNewSw.getAddressInfoNew().get(0).getAddress(),
                                                mdsDeviceInfoNewSw.getDescription(),
                                                mdsDeviceInfoNewSw.getSerial(),
                                                mdsDeviceInfoNewSw.getSw()));

                                        mDevice_name = mdsDeviceInfoNewSw.getDescription();

                                        Log.d(LOG_TAG, CONNECTED_WITH + mdsDeviceInfoNewSw.getSerial() + " : " + movesense_mac_address);

                                    } else if (mdsConnectedDevice.getDeviceInfo() instanceof MdsDeviceInfoOldSw) {
                                        MdsDeviceInfoOldSw mdsDeviceInfoOldSw = (MdsDeviceInfoOldSw) mdsConnectedDevice.getDeviceInfo();
                                        MovesenseConnectedDevices.addConnectedDevice(new MovesenseDevice(
                                                mdsDeviceInfoOldSw.getAddressInfoOld(),
                                                mdsDeviceInfoOldSw.getDescription(),
                                                mdsDeviceInfoOldSw.getSerial(),
                                                mdsDeviceInfoOldSw.getSw()));

                                        mDevice_name = mdsDeviceInfoOldSw.getDescription();

                                        Log.d(LOG_TAG, CONNECTED_WITH + mdsDeviceInfoOldSw.getSerial() + " : " + movesense_mac_address);
                                    }
                                } else {

                                    if (file_path == null || file_path.isEmpty()) {
                                        Log.e(LOG_TAG, "File path error");
                                        return;
                                    }

                                    File dir = Environment.getExternalStorageDirectory();
                                    File yourFile = new File(dir, file_path);

                                    Log.e(LOG_TAG, "DFU: dir: " + dir);
                                    Log.e(LOG_TAG, "DFU: file: " + yourFile);
                                    Log.e(LOG_TAG, "DFU: file: " + yourFile.exists());
                                    Log.e(LOG_TAG, "DFU: path: " + yourFile.getPath());

                                    if (!yourFile.exists()) {
                                        Log.e(LOG_TAG, "File not exists - path: " + yourFile.getPath());
                                        return;
                                    }
                                    // Käytetään suoraan dfuservicea, ei tässä mitään utilia tarvita.
                                    //DfuUtil.runDfuServiceUpdate(context, DfuUtil.incrementMacAddress(movesense_mac_address), mDevice_name,
                                      //      Uri.parse("file://" + yourFile.getPath()), null);

                                    if (MovesenseConnectedDevices.getConnectedDevices().size() == 1) {
                                        MovesenseConnectedDevices.getConnectedDevices().remove(0);
                                    } else {
                                        Log.e(LOG_TAG, "ERROR: Wrong MovesenseConnectedDevices list size");
                                    }

                                }
                            }
                        }));

            } catch (Exception e) {
                Log.e(LOG_TAG, "BEFORE CONNECT YOU NEED GRANT LOCATION PERMISSION AND TURN ON BLUETOOTH !!!");
            }

        } else if (type.equals("disconnect")) {
            if (MovesenseConnectedDevices.getConnectedDevices().size() > 0
                    && movesense_mac_address != null && !movesense_mac_address.isEmpty()) {

                if (MovesenseConnectedDevices.getRxMovesenseConnectedDevices().size() > 0) {
                    BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
                }
            }
        } else if (type.equals("dfu_update")) {

            if (MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
                Log.e(LOG_TAG, "EI NÄITÄ PITÄIS TULLA");

            } else {

                mScanningCompositeSubscription.clear();

                mScanningCompositeSubscription.add(RxBle.Instance.getClient().scanBleDevices()
                        .timeout(60, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<RxBleScanResult>() {
                            @Override
                            public void call(RxBleScanResult scanResult) {
                                Log.d(LOG_TAG, "scanResult: " + scanResult.getBleDevice().getName() + " : " +
                                        scanResult.getBleDevice().getMacAddress());
                                if (!isConnecting && (scanResult.getBleDevice().getMacAddress().equals(dfu_mac_address))) {
                                    Log.e(LOG_TAG, "scanResult: FOUND DFU DEVICE FROM INTENT Connecting..." + scanResult.getBleDevice().getName() + " : " +
                                            scanResult.getBleDevice().getMacAddress());
                                    isConnecting = true;
                                    mDevice_name = scanResult.getBleDevice().getName();

                                    if (file_path == null || file_path.isEmpty()) {
                                        Log.e(LOG_TAG, "File path error");
                                        return;
                                    }

                                    File dir = Environment.getExternalStorageDirectory();
                                    File yourFile = new File(dir, file_path);

                                    Log.e(LOG_TAG, "DFU: dir: " + dir);
                                    Log.e(LOG_TAG, "DFU: file: " + yourFile);
                                    Log.e(LOG_TAG, "DFU: file: " + yourFile.exists());
                                    Log.e(LOG_TAG, "DFU: path: " + yourFile.getPath());

                                    if (!yourFile.exists()) {
                                        Log.e(LOG_TAG, "File not exists - path: " + yourFile.getPath());
                                        return;
                                    }
/*
                                    DfuUtil.runDfuServiceUpdate(context, dfu_mac_address, mDevice_name,
                                            Uri.parse("file://" + yourFile.getPath()), null);
*/
                                    mScanningCompositeSubscription.clear();
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Log.e(LOG_TAG, "BEFORE CONNECT YOU NEED GRANT LOCATION PERMISSION !!!");
                                Log.e(LOG_TAG, "Connect Error: ", throwable);
                            }
                        }));
            }
        }
    }
}
