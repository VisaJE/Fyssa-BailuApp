
package com.movesense.mds.fyssabailu.update_app;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuController;

/**
 * A controller class allows you to pause, resume or abort the DFU operation in a easy way.
 * <p>Keep in mind that there may be only one DFU operation at a time, and other instances of a DfuServiceController (for example obtained with a previous DFU)
 * will work for all DFU processes, but the {@link #isPaused()} and {@link #isAborted()} methods may report incorrect values.</p>
 * <p>Added in DFU Library version 1.0.2.</p>
 */
public class DfuServiceController implements DfuController {
    private LocalBroadcastManager mBroadcastManager;
    private boolean mPaused;
    private boolean mAborted;

    /* package */ DfuServiceController(final Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    @Override
    public void pause() {
        Log.d("Service", "Pausetettu");
        if (!mAborted && !mPaused) {
            mPaused = true;
            final Intent pauseAction = new Intent(DfuBaseService.BROADCAST_ACTION);
            pauseAction.putExtra(DfuBaseService.EXTRA_ACTION, DfuBaseService.ACTION_PAUSE);
            mBroadcastManager.sendBroadcast(pauseAction);
        }
    }

    @Override
    public void resume() {
        Log.d("Service", "Resumettu");
        if (!mAborted && mPaused) {
            mPaused = false;
            final Intent pauseAction = new Intent(DfuBaseService.BROADCAST_ACTION);
            pauseAction.putExtra(DfuBaseService.EXTRA_ACTION, DfuBaseService.ACTION_RESUME);
            mBroadcastManager.sendBroadcast(pauseAction);
        }
    }

    @Override
    public void abort() {
        Log.d("Service", "Aborttii");
        if (!mAborted) {
            mAborted = true;
            mPaused = false;
            final Intent pauseAction = new Intent(DfuBaseService.BROADCAST_ACTION);
            pauseAction.putExtra(DfuBaseService.EXTRA_ACTION, DfuBaseService.ACTION_ABORT);
            mBroadcastManager.sendBroadcast(pauseAction);
        }
    }

    /**
     * Returns true if the DFU operation was paused.
     * It can be now resumed using {@link #resume()}.
     */
    public boolean isPaused() {
        return mPaused;
    }

    /**
     * Returns true if DFU was aborted.
     */
    public boolean isAborted() {
        return mAborted;
    }
}
