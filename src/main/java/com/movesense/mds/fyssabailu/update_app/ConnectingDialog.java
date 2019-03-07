package com.movesense.mds.fyssabailu.update_app;

import android.content.Context;
import android.support.v7.app.AlertDialog;

import com.movesense.mds.fyssabailu.R;

/**
 * Singleton for Connecting dialog
 */

public enum ConnectingDialog {
    INSTANCE;

    private AlertDialog alertDialog;

    public void showDialog(Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context)
                .setTitle(R.string.connecting)
                .setMessage(R.string.please_wait_connecting);

         alertDialog = alertDialogBuilder.show();
    }

    public void dismissDialog() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }
}
