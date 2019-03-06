package com.movesense.mds.fyssabailu.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothStatusReceiver extends BroadcastReceiver {

    private final String TAG = BluetoothStatusReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int state;
        state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

        Log.d(TAG, "onReceive: action:" + action + " ,state:" + state);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            BluetoothStatusMonitor.INSTANCE.setBluetoothStatus(state);
        }
    }
}
