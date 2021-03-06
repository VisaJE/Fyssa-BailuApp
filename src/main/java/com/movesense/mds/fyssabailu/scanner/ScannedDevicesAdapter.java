package com.movesense.mds.fyssabailu.scanner;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.movesense.mds.fyssabailu.R;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Adapter for scanned devices
 */
class ScannedDevicesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String LOG_TAG = ScannedDevicesAdapter.class.getSimpleName();
    private String address;
    private static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        DeviceViewHolder(View itemView) {
            super(itemView);

            textView = itemView.findViewById(R.id.text);
        }
    }

    private final List<RxBleDevice> devices;
    private final PublishSubject<RxBleDevice> deviceSelectionSubject;
    private boolean notDfuScan;

    ScannedDevicesAdapter(boolean notDfuScan, String address)                                                                                                                                       {
        this.address = address;
        this.notDfuScan = notDfuScan;
        devices = new ArrayList<>();
        deviceSelectionSubject = PublishSubject.create();

        setHasStableIds(true);
    }

    /**
     * Provide this adapter with a new RxBleScanResult from which the user may select
     * a device. Calling this does not always change the underlying model as
     * no duplicates are kept.
     *
     * @param scanResult New scan result to add to this adapter
     */
    void handleScanResult(RxBleScanResult scanResult) {
        RxBleDevice device = scanResult.getBleDevice();
        Log.d(LOG_TAG, "Scanned Device Name : " + device.getName() + " Address: " + device.getMacAddress());

        // Show only Movesense devices on the list
        if (notDfuScan) {
            if (device.getName() != null && device.getName().contains("Movesense") && (address.equals("") || device.getMacAddress().equals(address))) {

                // Check for duplicates
                for (RxBleDevice d : devices) {
                    if (d.getMacAddress().equals(device.getMacAddress())) {
                        return;
                    }
                }
                // It was not a duplicate
                devices.add(device);
                notifyDataSetChanged();
            }
        } else if (device.getName() != null && device.getName().contains("DfuTarg")) {
            // Check for duplicates
            for (RxBleDevice d : devices) {
                if (d.getMacAddress().equals(device.getMacAddress())) {
                    return;
                }
            }

            // It was not a duplicate
            devices.add(device);
            notifyDataSetChanged();
        }
    }


    /**
     * Gets an Observable that emits all clicks on devices in the list
     *
     * @return Observable emitting selected devices
     */
    Observable<RxBleDevice> deviceSelectionObservable() {
        return deviceSelectionSubject.asObservable();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.scanned_device_item, parent, false);
        final DeviceViewHolder viewHolder = new DeviceViewHolder(view);

        // Listen for clicks
        viewHolder.itemView.setOnClickListener(view1 -> {
            int pos = viewHolder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                deviceSelectionSubject.onNext(devices.get(pos));
            }
        });

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        DeviceViewHolder deviceViewHolder = (DeviceViewHolder) holder;
        RxBleDevice device = devices.get(position);

        deviceViewHolder.textView.setText(device.getName());
    }

    @Override
    public long getItemId(int position) {
        return devices.get(position).getMacAddress().hashCode();
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }
}
