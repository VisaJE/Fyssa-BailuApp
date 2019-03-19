package com.movesense.mds.fyssabailu.bailu_app;

import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.model.FyssaPartyResponse;
import com.polidea.rxandroidble.RxBleDevice;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Semaphore;


class FyssaDeviceView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String LOG_TAG = FyssaDeviceView.class.getSimpleName();
    private final Long CLEAR_DELAY = 15L;

    private static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        DeviceViewHolder(View itemView) {
            super(itemView);

            textView = itemView.findViewById(R.id.text);
        }
    }

    private final Vector<String> devices;
    private final HashMap<String, String> infoMap;
    private HashMap<String, String> nameMap;
    private HashMap<String, Integer> scoreMap;
    private HashMap<String, Long> addTimeMap;
    private final Vector<String> partyTexts;
    private TimerTask timerTask;
    Timer timer;
    private Boolean isGoing = false;
    private void schedule() {
        if (!isGoing) {
            try {
                timer.schedule(timerTask, 3000, 3000);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unexpected stuff", e);
            }

        }
        isGoing = true;
    }
    public void stopTimer() {
        if(isGoing){
            try {
                timer.cancel();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unexpected cancel.", e);
            }

        }
        isGoing = false;
    }
    private Semaphore semaphore = new Semaphore(1, true);

    FyssaDeviceView() {

        devices = new Vector<>();
        infoMap = new HashMap<>();
        nameMap = new HashMap<>();
        addTimeMap = new HashMap<>();
        scoreMap = new HashMap<>();

        partyTexts = new Vector<>();
        setHasStableIds(true);
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp

                    try
                    {
                        semaphore.acquire(1);
                        for (String i : addTimeMap.keySet()) {
                            if (addTimeMap.get(i) < (System.currentTimeMillis() / 1000) - CLEAR_DELAY) {
                                Log.d(LOG_TAG, "Removing: " + ( (System.currentTimeMillis() / 1000) - CLEAR_DELAY) + ">" +addTimeMap.get(i));
                                addTimeMap.remove(i);
                                devices.remove(i);
                                infoMap.remove(i);
                                scoreMap.remove(i);
                                FyssaObserverActivity.enclosingClass.runOnUiThread(() -> notifyDataSetChanged());
                            }
                        }
                    }
                    catch (Exception ignored)
                    {
                    }
                    finally
                    {
                        semaphore.release(1);
                    }
            }

        };
        schedule();
    }

    void putDevice(String mac, String name) {
        try
        {
            semaphore.acquireUninterruptibly();
            nameMap.put(mac, name);
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

    boolean knowsDevice(String mac) {
        boolean res = false;
        try
        {
            semaphore.acquireUninterruptibly();
            res = nameMap.containsKey(mac);
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Muted");
        }
        finally
        {
            semaphore.release(1);
        }
        return res;
    }

    private String partyTime(int t) {
        if (t > 0) return " Partied for: " + partyTimeToString(t);
        else return "";
    }
    private String partyTimeToString(int t) {
        if (t == 0) return "0";
        if (t < 60) return "< 1 min";
        else {
            int mins = t/60;
            if (mins < 60) return "" + mins + " min";
            else {
                int hours = mins/60;
                mins = mins - hours*60;
                return "" + hours + " h " + mins + " min";
            }
        }
    }

    private String scoreToString(int score) {
        Log.d(LOG_TAG, "Current score " + score);
        if (score == 0) return "Null";
        if (score < 20) return Character.toString((char)0x03F5);
        if (score < 50) return "Casual.";
        if (score < 70) return "Fun.";
        if (score < 100) return "Nice.";
        if (score < 130) return "100!";
        if (score < 150) return "Woohoo!";
        if (score < 200) return "Amazing!!";
        if (score < 300) return "Over 9000!!!";
        if (score < 500) return "Next level shit.";
        if (score < 600) return "Total mayhem.";
        if (score < 700) return "Better than annihilation!";
        return "This app is not prepared for these party levels.";
    }

    private String getText(RxBleDevice device, int score, int timePartying) {
        String name = "Unknown player";
        if (nameMap.containsKey(device.getMacAddress())) name = nameMap.get(device.getMacAddress());
        return name + "\nParty level: " + scoreToString(score) + partyTime(timePartying);
    }


    public void handle(RxBleDevice device, int score, int timePartying) {
        try
        {
            semaphore.acquireUninterruptibly();
            if (devices.contains(device.getMacAddress())) {
                infoMap.remove(device.getMacAddress());
                infoMap.put(device.getMacAddress(), getText(device, score, timePartying));
                scoreMap.remove(device.getMacAddress());
                scoreMap.put(device.getMacAddress(), score);
                addTimeMap.remove(device.getMacAddress());
                addTimeMap.put(device.getMacAddress(), System.currentTimeMillis()/1000);
            }
            else {
                Log.d(LOG_TAG, "Adding device:" + device.getMacAddress());
                devices.add(device.getMacAddress());
                infoMap.put(device.getMacAddress(), getText(device, score, timePartying));
                scoreMap.put(device.getMacAddress(), score);
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

    private String getPartyText(FyssaPartyResponse.Party p){
        return "Parties at " + p.place + "!\n" + "People present: " + p.population +
                ". Party score: " + scoreToString(p.score) + "\n" + (
                        p.description.equals("None") ? "" : p.description +"\n") + "Last seen " +
                p.lastSeen.substring(0, p.lastSeen.length()-4) +".";
    }

    void addParties(FyssaPartyResponse.Party[] parties) {
        partyTexts.clear();
        for (FyssaPartyResponse.Party p : parties) {
            partyTexts.add(getPartyText(p));
        }
        notifyDataSetChanged();
    }

    Integer getScore() {
        Integer score = 0;
        try
        {
            semaphore.acquireUninterruptibly();

            for (String mac : devices) {
                score += scoreMap.get(mac);
            }
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Muted");
        }
        finally
        {
            semaphore.release(1);
        }
        return score;
    }

    Integer getPeopleCount() {
        int score = 0;
        try
        {
            semaphore.acquireUninterruptibly();
            score = devices.size();
        }
        catch (Exception e) { Log.d(LOG_TAG, "Muted"); }
        finally
        {
            semaphore.release(1);
        }
        return score;
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
            semaphore.acquireUninterruptibly(1);
            DeviceViewHolder deviceViewHolder = (DeviceViewHolder) holder;
            if (position < devices.size()) {
                String device = devices.get(position);
                String text = infoMap.get(device);
                Log.d(LOG_TAG, "onBind(): "+ text);
                if (deviceViewHolder.textView != null) deviceViewHolder.textView.setText(text);
            } else {
                if (deviceViewHolder.textView != null) deviceViewHolder.textView.setText(partyTexts.get(position-devices.size()));
            }
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

    // NOT THREAD SAFE
    @Override
    public long getItemId(int position) {
        int res;
        try{
            semaphore.acquireUninterruptibly();
            if (position >= devices.size()) {
                res = partyTexts.get(position-devices.size()).hashCode();
            } else res = devices.get(position).hashCode();
        } finally {
             semaphore.release();
        }
        return res;
    }

    @Override
    public int getItemCount() {
        return devices.size() + partyTexts.size();
    }
}
