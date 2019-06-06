package com.movesense.mds.fyssabailu.bailu_app;

import android.app.Application;
import android.content.Context;

import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.Util;
import com.movesense.mds.fyssabailu.bluetooth.MdsRx;
import com.movesense.mds.fyssabailu.bluetooth.RxBle;
import com.movesense.mds.fyssabailu.tool.MemoryTools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Application for making all initializations
 */
public class FyssaApp extends Application {

    public static final String SERVER_THRESHOLD_URL = "http://104.248.244.199:5000/bailu/threshold";
    public static final String SERVER_INSERT_URL = "http://104.248.244.199:5000/bailu/name/insert";
    public static final String SERVER_GET_URL = "http://104.248.244.199:5000/bailu/name/";
    public static final String SERVER_GET_PARTY_URL = "http://104.248.244.199:5000/bailu/parties";
    // Accepted versions
    private static final String deviceVersions[] = {"1.1.2.BA", "1.1.2.BS"};
    private MemoryTools memoryTools;

    public static Boolean isSupported(String deviceVersion) {
        for (String i : deviceVersions) {
            if (i.equals(deviceVersion)) return true;
        }
        return false;
    }

    public static Boolean hasBootloader(String deviceVersion) {
        return deviceVersion.charAt(2) == '1' &&
                (deviceVersion.contains("BA") || deviceVersion.contains("BS") ||
                        deviceVersion.contains("HW") || deviceVersion.contains("IMU"));
    }

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
