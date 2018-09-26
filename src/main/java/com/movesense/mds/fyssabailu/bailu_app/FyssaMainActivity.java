package com.movesense.mds.fyssabailu.bailu_app;


import android.app.TimePickerDialog;
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
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.mds.fyssabailu.BleManager;
import com.movesense.mds.fyssabailu.ConnectionLostDialog;
import com.movesense.mds.fyssabailu.DataSender;
import com.movesense.mds.fyssabailu.DataUser;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.RxBle;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.model.EnergyGet;
import com.movesense.mds.fyssabailu.model.FyssaBailuGson;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.update_app.model.DebugResponse;

import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;
import com.movesense.mds.fyssabailu.MdsRx;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.update_app.model.InfoAppResponse;
import com.movesense.mds.fyssabailu.tool.MemoryTools;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class FyssaMainActivity extends AppCompatActivity implements DataUser {


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

    private static Integer temp_threshold;

    DataSender sender;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        app = (FyssaApp) getApplication();


        subscriptions = new CompositeSubscription();



        setContentView(R.layout.activity_fyssa_main);
        ButterKnife.bind(this);


        nimiTv.setText(app.getMemoryTools().getName());
        temp_threshold = 1;
        disableButtons();
        sender = new DataSender(FyssaMainActivity.this.getCacheDir(), this);
        toast("Setting things up...");
        sender.get(FyssaApp.SERVER_THRESHOLD_URL);

    }

    @Override
    public void onGetSuccess(String response) {
         temp_threshold = Integer.parseInt(response);
         Log.d(TAG, "Threshold is now " + temp_threshold + ". Now sending the current name");
         sender.post(FyssaApp.SERVER_INSERT_URL + "?name=" + app.getMemoryTools().getName() + "&mac=" + MovesenseConnectedDevices.getConnectedRxDevice(0).getMacAddress());

    }
    @Override
    public void onGetError(VolleyError error) {
        toast("Connection to the server failed");
        onBackPressed();
    }
    @Override
    public void onPostSuccess(String response) {
        Log.d(TAG, response);
        app.getMemoryTools().saveSerial(MovesenseConnectedDevices.getConnectedRxDevice(0).getMacAddress());
        checkSensorSoftware();
    }
    @Override
    public void onPostError(VolleyError error) {
        toast("Connection to the server failed");
        onBackPressed();
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
        /*findViewById(R.id.get_button).setEnabled(false);
        findViewById(R.id.start_service_button).setEnabled(false);
        findViewById(R.id.stop_service_button).setEnabled(false);*/
        findViewById(R.id.battery_button).setEnabled(false);
    }
    private void enableButtons() {
        findViewById(R.id.do_button).setEnabled(true);
        findViewById(R.id.stop).setEnabled(true);
        /*findViewById(R.id.get_button).setEnabled(true);
        findViewById(R.id.start_service_button).setEnabled(true);
        findViewById(R.id.stop_service_button).setEnabled(true);*/
        findViewById(R.id.battery_button).setEnabled(true);
    }
    private void checkSensorSoftware() {
        checkSensorSoftware(0);
    }
    private void checkSensorSoftware(int iteration) {
        Log.d(TAG, "Checking software");
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/Info/App",
                null, new MdsResponseListener() {

                    @Override
                    public void onSuccess(String s) {
                        addConnectionSubscription();
                        Log.d(TAG, "/Info/App onSuccess: " + s);
                        InfoAppResponse infoAppResponse = new Gson().fromJson(s, InfoAppResponse.class);
                        Log.d(TAG, "Company: " + infoAppResponse.getContent().getCompany());
                        if (infoAppResponse.getContent() != null) {
                            Log.d(TAG, "Name: " + infoAppResponse.getContent().getName());
                            Log.d(TAG, "Version: " + infoAppResponse.getContent().getVersion());
                            Log.d(TAG, "Company: " + infoAppResponse.getContent().getCompany());
                        }
                        if (!FyssaApp.isSupported(infoAppResponse.getContent().getVersion())) {
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
                        } else enableButtons();
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Info onError: ", e);
                        if (iteration < 2) {
                            try {
                                Thread.sleep(2000);
                                if (MovesenseConnectedDevices.getConnectedRxDevice(0) == null) {
                                    toast("Connection failed");
                                    startMainActivity();
                                } else MdsRx.Instance.reconnect(FyssaMainActivity.this);
                            } catch (InterruptedException e1) {
                                toast(e.toString());
                            }
                            checkSensorSoftware(iteration+1);
                        }
                        else {
                            startMainActivity();
                        }

                    }
                });
    }

    public static void removeAndDisconnectFromDevices(){
        BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;
        while (MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
            MovesenseConnectedDevices.removeConnectedDevice((MovesenseConnectedDevices.getConnectedDevice(0)));
        }
        while (MovesenseConnectedDevices.getRxMovesenseConnectedDevices().size() > 0) {
            BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
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

    @OnClick({/*R.id.start_service_button, R.id.stop_service_button,  R.id.get_button, */R.id.do_button,R.id.stop, R.id.battery_button})
    public void onViewClicked(View view) {
        switch(view.getId()) {
            /*case R.id.start_service_button:
                subscribeDebug();
                break;
            case R.id.stop_service_button:
                unsubscribeDebug();
                break;
            case R.id.get_button:
                getInfo();
                break;*/
            case R.id.do_button:
                final Calendar c = Calendar.getInstance();
                final int mHour = c.get(Calendar.HOUR_OF_DAY);
                final int mMinute = c.get(Calendar.MINUTE);
                TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                int nHour = c.get(Calendar.HOUR_OF_DAY);
                                int nMinute = c.get(Calendar.MINUTE);
                                int time = (hourOfDay-nHour)*60 + (minute-nMinute);
                                if (time > 0) startService(time);
                                else toast("Invalid time.");

                            }


                        }, mHour, mMinute, true);
                timePickerDialog.show();

                break;
            case R.id.stop:
                deleteService();
                break;

            case R.id.battery_button:
                getBatteryLevel();
                break;
        }
    }

    private void startObserving() {
        removeAndDisconnectFromDevices();
        subscriptions.unsubscribe();
        finish();
        startActivity(new Intent(FyssaMainActivity.this, FyssaObserver.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void startService(int minutes) {

        FyssaBailuGson fbc = new FyssaBailuGson(new FyssaBailuGson.FyssaBailuConfig(minutes, temp_threshold));
        Log.d(TAG, "startService()->" + new Gson().toJson(fbc) + ", for " + minutes + "minutes" );
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
                removeAndDisconnectFromDevices();
                disconnect = true;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        subscriptions.unsubscribe();
        subscriptions.clear();

    }

    @Override
    public void onBackPressed() {
        removeAndDisconnectFromDevices();
        disconnect = true;

    }
    @Override
    public void onPause() {
        super.onPause();

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
