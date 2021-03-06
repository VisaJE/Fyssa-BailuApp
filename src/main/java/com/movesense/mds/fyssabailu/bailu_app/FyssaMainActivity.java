package com.movesense.mds.fyssabailu.bailu_app;


import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.mds.fyssabailu.ConnectionLostDialog;
import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.ThrowableToastingAction;
import com.movesense.mds.fyssabailu.bluetooth.BleManager;
import com.movesense.mds.fyssabailu.bluetooth.MdsRx;
import com.movesense.mds.fyssabailu.model.EnergyGet;
import com.movesense.mds.fyssabailu.model.FyssaBailuGson;
import com.movesense.mds.fyssabailu.online.DataSender;
import com.movesense.mds.fyssabailu.online.DataUser;
import com.movesense.mds.fyssabailu.update_app.FyssaSensorUpdateActivity;
import com.movesense.mds.fyssabailu.update_app.model.DebugResponse;
import com.movesense.mds.fyssabailu.update_app.model.InfoAppResponse;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class FyssaMainActivity extends AppCompatActivity implements DataUser {


    private static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    private static final String FYSSA_DEBUG_PATH = "/Fyssa/Debug";
    private static final String BAILU_PATH = "/Fyssa/Bailu";
    private static final String BATTERY_PATH = "/System/Energy";
    private static Integer temp_threshold;
    private final String TAG = FyssaMainActivity.class.getSimpleName();
    @BindView(R.id.nimi_tv)
    TextView nimiTv;
    @BindView(R.id.do_button)
    Button doButton;
    @BindView(R.id.battery_button)
    Button batteryButton;
    @BindView(R.id.stop)
    Button stopButton;
    private DataSender sender;
    private CompositeSubscription subscriptions;
    private FyssaApp app;
    private MdsSubscription mdsSubscription;
    private boolean closeApp = false;
    private boolean disconnect = false;

    private AlertDialog updateAlert;

    public static void removeAndDisconnectFromDevices() {
        BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;
        while (MovesenseConnectedDevices.getConnectedDevices().size() > 0) {
            MovesenseConnectedDevices.removeConnectedDevice((MovesenseConnectedDevices.getConnectedDevice(0)));
        }
        while (MovesenseConnectedDevices.getRxMovesenseConnectedDevices().size() > 0) {
            BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));
            MovesenseConnectedDevices.removeRxConnectedDevice(MovesenseConnectedDevices.getConnectedRxDevice(0));
        }
    }

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
        sender.post(FyssaApp.SERVER_INSERT_URL + "?name=" + android.net.Uri.encode(app.getMemoryTools().getName()) + "&mac=" + MovesenseConnectedDevices.getConnectedRxDevice(0).getMacAddress());

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
        Log.d(TAG, "Adding subscriptions");
        subscriptions.clear();
        subscriptions.add(MdsRx.Instance.connectedDeviceObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mdsConnectedDevice -> {
                    // Stop refreshing
                    if (mdsConnectedDevice.getConnection() == null) {
                        Log.e(TAG, "call: Rx Disconnect");
                        if (closeApp) {
                            finishAndRemoveTask();
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
        findViewById(R.id.battery_button).setEnabled(true);
    }

    private void checkSensorSoftware() {
        checkSensorSoftware(0);
    }

    private void checkSensorSoftware(int iteration) {
        if (MovesenseConnectedDevices.getConnectedDevices().size() <= 0) {
            Log.d(TAG, "SENSOR WASNT CONNECTED");
            startMainActivity();
            return;
        }
        Log.d(TAG, "Checking software");
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + "/Info/App",
                null, new MdsResponseListener() {

                    @Override
                    public void onSuccess(String s) {
                        if (updateAlert != null) updateAlert.dismiss();
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
                            disableButtons();
                            updateAlert = builder.setMessage("Non compatible software detected. Update?").setPositiveButton("Yes", (dialog, which) -> {
                                switch (which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        if (FyssaApp.hasBootloader(infoAppResponse.getContent().getVersion()))
                                            updateSensorSoftware();
                                        else
                                            updateSensorSoftware(); // Useless now that everythin happens within the same update activity.
                                        break;
                                }
                            }).show();
                        } else enableButtons();
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Info onError: ", e);
                        if (iteration < 2) {
                            try {
                                Log.d(TAG, "Sleeping before retry.");
                                Thread.sleep(2000);
                                Log.d(TAG, "Done sleeping");
                                if (MovesenseConnectedDevices.getRxMovesenseConnectedDevices().size() <= 0) {
                                    toast("Connection failed");
                                    startMainActivity();
                                } else MdsRx.Instance.reconnect(FyssaMainActivity.this);
                            } catch (InterruptedException e1) {
                                toast(e.toString());
                            }
                            checkSensorSoftware(iteration + 1);
                        } else {
                            removeAndDisconnectFromDevices();
                            startMainActivity();
                        }

                    }
                });
    }

    private void updateSensorSoftware() {
        //removeAndDisconnectFromDevice();
        if (updateAlert != null) updateAlert.dismiss();
        subscriptions.clear();
        startActivity(new Intent(FyssaMainActivity.this, FyssaSensorUpdateActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP ));
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        try {
            toast("Serial: " + MovesenseConnectedDevices.getConnectedDevice(0).getSerial());
        } catch (Exception e) {
            startMainActivity();
            return;
        }

        checkSensorSoftware();
    }

    @OnClick({/*R.id.start_service_button, R.id.stop_service_button,  R.id.get_button, */R.id.do_button, R.id.stop, R.id.battery_button})
    public void onViewClicked(View view) {
        switch (view.getId()) {

            case R.id.do_button:
                final Calendar c = Calendar.getInstance();
                final int mHour = c.get(Calendar.HOUR_OF_DAY);
                final int mMinute = c.get(Calendar.MINUTE);
                TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                        (view1, hourOfDay, minute) -> {
                            int nHour = c.get(Calendar.HOUR_OF_DAY);
                            int nMinute = c.get(Calendar.MINUTE);
                            int time = (hourOfDay - nHour) * 60 + (minute - nMinute);
                            if (time > 0) startService(time);
                            else if (time < 0) startService(24 * 60 + time);
                            else toast("Invalid time.");

                        }, mHour, mMinute, true);
                timePickerDialog.show();
                toast("Set how long to measure.");

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
        subscriptions.clear();
        finish();
        startActivity(new Intent(FyssaMainActivity.this, FyssaObserverActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void startService(int minutes) {
        disableButtons();
        FyssaBailuGson fbc = new FyssaBailuGson(new FyssaBailuGson.FyssaBailuConfig(minutes, temp_threshold));
        Log.d(TAG, "startService()->" + new Gson().toJson(fbc) + ", for " + minutes + "minutes");
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
        stopButton.setEnabled(false);
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH + "/Stop",
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        Log.d(TAG, "Stopped party metering at: " + MdsRx.SCHEME_PREFIX +
                                MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BAILU_PATH);
                        Log.d(TAG, "deleteService: Got response" + s);
                        toast("Stopped measuring.");
                        stopButton.setEnabled(true);
                    }

                    @Override
                    public void onError(MdsException e) {
                        stopButton.setEnabled(true);
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
        batteryButton.setEnabled(false);
        Mds.builder().build(this).get(MdsRx.SCHEME_PREFIX +
                        MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + BATTERY_PATH,
                null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String s) {
                        EnergyGet e = new Gson().fromJson(s, EnergyGet.class);
                        toast("Battery percentage: " + e.getPercentage() + ", mV: " + e.getVoltage());
                        batteryButton.setEnabled(true);
                    }

                    @Override
                    public void onError(MdsException e) {
                        batteryButton.setEnabled(false);
                        Log.e(TAG, "onError: ", e);
                    }
                });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


    private void subscribeFyssaDebug() {
        Log.d(TAG, "Subscribing with" + "{\"Uri\": \"" +MovesenseConnectedDevices.getConnectedDevice(0).getSerial() +
                FYSSA_DEBUG_PATH + "\"}");
        mdsSubscription = Mds.builder().build(this).subscribe(URI_EVENTLISTENER, "{\"Uri\": \"" +MovesenseConnectedDevices.getConnectedDevice(0).getSerial() +
                FYSSA_DEBUG_PATH + "\"}",
                new MdsNotificationListener() {
                    @Override
                    public void onNotification(String s) {
                        Log.d("D/FyssaDebug: ", s);
                    }

                    @Override
                    public void onError(MdsException e) {
                        Log.e(TAG, "Error on subscribing fyssadebug:", e);
                    }
                });
    }


    private void unsubscribeFyssaDebug() {
        if (mdsSubscription != null) mdsSubscription.unsubscribe();

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
                subscriptions.clear();
                startActivity(new Intent(FyssaMainActivity.this, FyssaSensorUpdateActivity.class));
                return true;

            case R.id.remove_device:
                removeAndDisconnectFromDevices();
                disconnect = true;
                return true;
            case R.id.fyssa_debug_start:
                subscribeFyssaDebug();
                return true;
            case R.id.fyssa_debug_stop:
                unsubscribeFyssaDebug();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
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
        subscriptions.clear();
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
