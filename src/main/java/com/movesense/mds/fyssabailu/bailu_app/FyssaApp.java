package com.movesense.mds.fyssabailu.bailu_app;

import android.app.Application;
import android.content.Context;

import com.movesense.mds.fyssabailu.MdsRx;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.RxBle;
import com.movesense.mds.fyssabailu.Util;
import com.movesense.mds.fyssabailu.tool.MemoryTools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Application for making all initializations
 */
public class FyssaApp extends Application {

    private MemoryTools memoryTools;
    // Accepted versions
    private static final String deviceVersions[] = {"0.1.1.BA","0.1.1.BS"} ;
    public static final Boolean isSupported(String deviceVersion) {
        for (String i : deviceVersions) {
            if (i.equals(deviceVersion)) return true;
        }
        return false;
    }
    public static final String SERVER_THRESHOLD_URL = "http://82.130.33.5:5000/bailu/threshold";
    public static final String SERVER_INSERT_URL = "http://82.130.33.5:5000/bailu/name/insert";
    public static final String SERVER_GET_URL = "http://82.130.33.5:5000/bailu/name/";


    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize RxBleWrapper
        RxBle.Instance.initialize(this);

        // Copy necessary configuration file to proper place
        copyRawResourceToFile(R.raw.kompostisettings, "KompostiSettings.xml");

        // Initialize MDS
        MdsRx.Instance.initialize(this);

        memoryTools = new MemoryTools(this);

    }

    @Override
    public void onTerminate() {
        super.onTerminate();

    }

    public MemoryTools getMemoryTools() {
        return memoryTools;
    }

    /**
     * Copy raw resource file to file.
     *
     * @param resourceId Resource id.
     * @param fileName   Target file name.
     */
    private void copyRawResourceToFile(int resourceId, String fileName) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = getResources().openRawResource(resourceId);
            out = openFileOutput(fileName, Context.MODE_PRIVATE);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not copy configuration file to: " + fileName);
        } finally {
            Util.safeClose(out);
            Util.safeClose(in);
        }
    }
}
