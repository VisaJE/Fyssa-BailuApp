package com.movesense.mds.fyssabailu;


import android.content.Context;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import com.movesense.mds.fyssabailu.bluetooth.BleManager;
import com.movesense.mds.fyssabailu.update_app.model.MovesenseConnectedDevices;

public enum  ConnectionLostDialog {
    INSTANCE;

    private AlertDialog mAlertDialog;

    public void showDialog(final Context context) {
        Log.e("ConnectionLostDialog", "showDialog: ");
        if (mAlertDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setTitle("Connection Lost")
                    .setMessage("Appliaction will connect automatically with Movesense device" +
                            " when it will be available.")
                    .setPositiveButton("Ok", (dialog, which) -> {
                        BleManager.INSTANCE.isReconnectToLastConnectedDeviceEnable = false;

                        BleManager.INSTANCE.disconnect(MovesenseConnectedDevices.getConnectedRxDevice(0));

                        context.startActivity(new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    });

            mAlertDialog = builder.show();
        } else {
            Log.e("ConnectionLostDialog", "showDialog: DIALOG NOT NULL");
        }
    }


    public void dismissDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }
}
