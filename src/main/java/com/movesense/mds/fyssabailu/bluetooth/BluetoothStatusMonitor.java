package com.movesense.mds.fyssabailu.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.util.Log;

import rx.subjects.PublishSubject;

public enum BluetoothStatusMonitor {
    INSTANCE;

    private final String TAG = BluetoothStatusMonitor.class.getSimpleName();
    public PublishSubject<Integer> bluetoothStatusSubject = PublishSubject.create();
    private int bluetoothStatus = BluetoothAdapter.ERROR;

    public void setBluetoothStatus(int state) {
        Log.d(TAG, "setBluetoothStatus: state: " + state);
        bluetoothStatusSubject.onNext(state);
        bluetoothStatus = state;
        if (state == BluetoothAdapter.STATE_ON) {
            Log.d(TAG, "setBluetoothStatus: BluetoothAdapter.STATE_ON");
            return;
        }

        if (state == BluetoothAdapter.STATE_OFF) {
            Log.d(TAG, "setBluetoothStatus: BluetoothAdapter.STATE_OFF");
            return;
        }
    }

    public int getBluetoothStatus() {
        return bluetoothStatus;
    }

    public void initBluetoothStatus() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF || bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
            bluetoothStatus = BluetoothAdapter.STATE_OFF;
        } else if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON || bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {
            bluetoothStatus = BluetoothAdapter.STATE_ON;
        }
    }
}
