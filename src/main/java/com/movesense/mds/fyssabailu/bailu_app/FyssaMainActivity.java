package com.movesense.mds.fyssabailu.bailu_app;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.mds.fyssabailu.BleManager;
import com.movesense.mds.fyssabailu.ConnectionLostDialog;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.ScanFragment;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.model.EnergyGet;
import com.movesense.mds.fyssabailu.model.FyssaBailuGson;
import com.movesense.mds.fyssabailu.model.MdsConnectedDevice;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoNewSw;
import com.movesense.mds.fyssabailu.model.MdsDeviceInfoOldSw;
import com.movesense.mds.fyssabailu.update_app.ConnectingDialog;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.update_app.SelectTestActivity;
import com.movesense.mds.fyssabailu.update_app.model.DebugResponse;

import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;
import com.movesense.mds.fyssabailu.MdsRx;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.update_app.model.InfoAppResponse;
import com.movesense.mds.fyssabailu.tool.MemoryTools;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseDevice;
import com.polidea.rxandroidble.RxBleDevice;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public class FyssaMainActivity extends AppCompatActivity {


    @BindView(R.id.nimi_tv) TextView nimiTv;
    @BindView(R.id.do_button) Button doButton;

    private final String TAG = FyssaMainActivity.class.getSimpleName();

    private CompositeSubscription subscriptions;
    private FyssaApp app;


    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String BAILU_PATH = "/Fyssa/Bailu";
    public static final String BATTERY_PATH = "/System/Energy";

    private MdsSubscription mdsSubscription;


    private boolean closeApp = false;
    private boolean disconnect = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        app = (FyssaApp) getApplication();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Fyssasensori");
        }


        subscriptions = new CompositeSubscription();
        addConnectionSubscription();


        setContentView(R.layout.activity_fyssa_main);
        ButterKnife.bind(this);

        if (app.getMemoryTools().getName().equals(MemoryTools.DEFAULT_STRING)) {
            startInfoActivity();
            Log.d(TAG, "No name yet");
            finish();
        } else {
            nimiTv.setText(app.getMemoryTools().getName());
        }

    }



    private void addConnectionSubscription() {
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
                            startMainActivity();
                        } else {
                            ConnectionLostDialog.INSTANCE.showDialog(FyssaMainActivity.this);
                            disableButtons();
                        }
                    } else {
                        ConnectionLostDialog.INSTANCE.dismissDialog();
                        enableButtons();
                        Log.e(TAG, "call: Rx Connect");
                    }
                }, new ThrowableToastingAction(this)));
    }
    private void disableButtons() {
        findViewById(R.id.do_button).setEnabled(false);
        findViewById(R.id.stop).setEnabled(false);
        findViewById(R.id.get_button).setEnabled(false);
        findViewById(R.id.start_service_button).setEnabled(false);
        findViewById(R.id.stop_service_button).setEnabled(false);
    }
    private void enableButtons() {
        findViewById(R.id.do_button).setEnabled(true);
        findViewById(R.id.stop).setEnabled(true);
        findViewById(R.id.get_button).setEnabled(true);
        findViewById(R.id.start_service_button).setEnabled(true);
        findViewById(R.id.stop_service_button).setEnabled(true);
    }

    private void checkSensorSoftware() {
        Log.d(TAG, "Checking software");
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/Info/App",
                null, new MdsResponseListener() {

                    @Override
                    public void onSuccess(String s) {
                        Log.d(TAG, "/Info/App onSuccess: " + s);
                        InfoAppResponse infoAppResponse = new Gson().fromJson(s, InfoAppResponse.class);
                        Log.d(TAG, "Company: " + infoAppResponse.getContent().getCompany());
                        if (infoAppResponse.getContent() != null) {
                            Log.d(TAG, "Name: " + infoAppResponse.getContent().getName());
                            Log.d(TAG, "Version: " + infoAppResponse.getContent().getVersion());
                            Log.d(TAG, "Company: " + infoAppResponse.getContent().getCompany());
                        }
                        if (!infoAppResponse.getContent().getVersion().equals(FyssaApp.deviceVersion)) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(FyssaMainActivity.this);
                            builder.setMessage("Update?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case DialogInterface.BUTTON_POSITIVE:
                                            updateSensorSoftware();
                                            break;
                                    }
                                }
                            }).show();
                        } else doButton.setEnabled(true);
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Info onError: ", e);
                    }
                });
    }

    private void removeAndDisconnectFromDevice(){
        if(MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
            BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
            BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;
            MovesenseConnectedDevices.removeRxConnectedDevice(MovesenseConnectedDevices.getConnectedRxDevice(0));
        }
    }

    private void updateSensorSoftware() {
        //removeAndDisconnectFromDevice();
        subscriptions.unsubscribe();
        startActivity(new Intent(FyssaMainActivity.this, FyssaSensorUpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    protected void onResume() {
        super.onResume();
        addConnectionSubscription();
        try {
            toast("Serial: " + MovesenseConnectedDevices.getConnectedDevice(0).getSerial());
        } catch (Exception e) {
            startMainActivity();
            return;
        }

        checkSensorSoftware();
    }

    @OnClick({R.id.start_service_button, R.id.stop_service_button, R.id.do_button, R.id.stop, R.id.get_button, R.id.battery_button})
    public void onViewClicked(View view) {
        switch(view.getId()) {
            case R.id.start_service_button:
                subscribeDebug();
                break;
            case R.id.stop_service_button:
                unsubscribeDebug();
                break;
            case R.id.do_button:
                startService();

                break;
            case R.id.stop:
                deleteService();
                break;
            case R.id.get_button:
                getInfo();
                break;
            case R.id.battery_button:
                getBatteryLevel();
                break;
        }
    }

    private void startObserving() {
        removeAndDisconnectFromDevice();
        subscriptions.unsubscribe();
        finish();
        startActivity(new Intent(FyssaMainActivity.this, FyssaObserver.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void startService() {
        FyssaBailuGson fbc = new FyssaBailuGson(new FyssaBailuGson.FyssaBailuConfig(4, 25));
        Log.d(TAG, "startService()->" + new Gson().toJson(fbc));
        Mds.builder().build(this).put(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH,
                new Gson().toJson(fbc), new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        toast(s);
                        Log.d(TAG, s);
                        startObserving();
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "onError: ", e);
                    }
                });
    }
    private void deleteService() {
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH + "/Stop",
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        Log.d(TAG, "Stopped party metering at: " + MdsRx.SCHEME_PREFIX +
                                MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH);
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "onError: ", e);
                    }
                });
    }
    private void getInfo() {
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH,
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        Log.d(TAG, "Found a value from: " + MdsRx.SCHEME_PREFIX +
                                MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH);
                        toast(s);
                        Log.d(TAG, s);
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "onError: ", e);
                    }
                });
    }

    private void getBatteryLevel() {
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BATTERY_PATH,
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        EnergyGet e = new Gson().fromJson(s, EnergyGet.class);
                        toast("Battery percentage: " + e.getPercentage() + ", mV: " + e.getVoltage());
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "onError: ", e);
                    }
                });
    }

    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


    private void subscribeDebug() {
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/System/Debug/Config",
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        Log.d(TAG, "Sensor debug config: " + s);
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Error on debug:", e);
                    }
                });
        mdsSubscription = Mds.builder().build(this).subscribe(URI_EVENTLISTENER, "{\"Uri\": \"" +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/System/Debug/4\"}",
                        new MdsNotificationListener() {
                    @Override
                    public void onNotification(String s) {
                        DebugResponse resp = new Gson().fromJson(s, DebugResponse.class);
                        Log.d(TAG, resp.getMessage());
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Error on subscribing debug:", e);
                    }
                });
    }


    private void unsubscribeDebug() {
        if (mdsSubscription != null) mdsSubscription.unsubscribe();

        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fyssa_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.update:
                startActivity(new Intent(FyssaMainActivity.this, FyssaSensorUpdateActivity.class));
                return true;

            case R.id.remove_device:
                removeAndDisconnectFromDevice();
                disconnect = true;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        unsubscribeDebug();
        super.onDestroy();
        subscriptions.unsubscribe();
        subscriptions.clear();

    }

    @Override
    public void onBackPressed() {
        unsubscribeDebug();
    }
    @Override
    public void onPause() {
        super.onPause();
        unsubscribeDebug();
        subscriptions.unsubscribe();
    }


    private void startInfoActivity() {
        startActivity(new Intent(FyssaMainActivity.this, FyssaInfoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void startMainActivity() {
        startActivity(new Intent(FyssaMainActivity.this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
