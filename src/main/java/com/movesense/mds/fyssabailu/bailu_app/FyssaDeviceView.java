package com.movesense.mds.fyssabailu.bailu_app;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.movesense.mds.fyssabailu.R;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import rx.Observable;
import rx.subjects.PublishSubject;


class FyssaDeviceView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String LOG_TAG = FyssaDeviceView.class.getSimpleName();
    private final Integer CLEAR_DELAY = 5000;

    private static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        DeviceViewHolder(View itemView) {
            super(itemView);

            textView = (TextView) itemView.findViewById(R.id.text);
        }
    }

    private final Vector<String> devices;
    private final HashMap<String, String> infoMap;
    public HashMap<String, String> nameMap;
    private Hashtable<String, Long> addTimeMap;
    TimerTask timerTask;
    public Timer timer;
    Semaphore semaphore = new Semaphore(1, true);

    FyssaDeviceView() {

        devices = new Vector<>();
        infoMap = new HashMap<>();
        nameMap = new HashMap<>();
        addTimeMap = new Hashtable<>();
        setHasStableIds(true);
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                for (String i : addTimeMap.keySet()) {
                    try
                    {
                        semaphore.acquire(1);
                        if (addTimeMap.get(i) > (System.currentTimeMillis()/1000)-CLEAR_DELAY) {
                            addTimeMap.remove(i);
                            devices.remove(i);
                            infoMap.remove(i);
                            notifyDataSetChanged();
                        }
                    }
                    catch (Exception e)
                    {
                        Log.e(LOG_TAG, "Muted", e);
                    }
                    finally
                    {
                        semaphore.release(1);
                    }

                }
            }

        };
        timer.schedule(timerTask, 5000, 5000);
    }



    private String getText(RxBleDevice device, int score, int timePartying) {
        String name = "Unknown player";
        if (nameMap.containsKey(device.getMacAddress())) name = nameMap.get(device.getMacAddress());
        return name + "\nParty level:" + score + " Time spent partying: " + timePartying;
    }


    public void handle(RxBleDevice device, int score, int timePartying) {
        try
        {
            semaphore.acquire(1);
            if (devices.contains(device.getMacAddress())) {
                infoMap.remove(device.getMacAddress());
                infoMap.put(device.getMacAddress(), getText(device, score, timePartying));
                addTimeMap.put(device.getMacAddress(), System.currentTimeMillis()/1000);
            }
            else {
                Log.d(LOG_TAG, "Adding device:" + device.getMacAddress());
                devices.add(device.getMacAddress());
                infoMap.put(device.getMacAddress(), getText(device, score, timePartying));
                addTimeMap.remove(device.getMacAddress());
                addTimeMap.put(device.getMacAddress(), System.currentTimeMillis()/1000);
            }
            notifyDataSetChanged();
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Muted");
        }
        finally
        {
            semaphore.release(1);
        }
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.scanned_device_item, parent, false);
        final DeviceViewHolder viewHolder = new DeviceViewHolder(view);
/*
        // Listen for clicks
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = viewHolder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {

                }
            }
        });*/

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        try
        {
            semaphore.acquire(1);
            DeviceViewHolder deviceViewHolder = (DeviceViewHolder) holder;
            String device = devices.get(position);
            String text = infoMap.get(device);
            Log.d(LOG_TAG, "onBind(): "+ text);
            if (deviceViewHolder.textView != null) deviceViewHolder.textView.setText(text);

        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Muted");
        }
        finally
        {
            semaphore.release(1);
        }
    }

    @Override
    public long getItemId(int position) {
        return devices.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }
}
