package com.movesense.mds.fyssabailu.bailu_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import java.util.List;
import java.util.Locale;

public class FyssaGeocoder implements LocationListener {
    private final String TAG = "FyssaGeocoder/";
    private Geocoder geo;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private boolean enabled = true;

    @SuppressLint("MissingPermission")
    FyssaGeocoder(Context context, LocationManager logman) {

        Context context1 = context;

        LocationManager mLocationManager = logman;

        geo = new Geocoder(context1, Locale.getDefault());

        for (String i : mLocationManager.getProviders(true)) {
            Log.d(TAG, "Provider enabled: " + i);
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "STILL NO PERMISSION");
            return;

        }
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }



    String getLocationInfo() {

        String out = null;
        try {

            List<Address> addresses = geo.getFromLocation(latitude, longitude, 1);
            if (addresses.isEmpty()) {
                Log.d(TAG, "Current position:" + latitude + ", " + longitude);
                out = "";
            } else {
                    Log.d(TAG,  addresses.get(0).getThoroughfare() + "! " + addresses.get(0).getPostalCode() + ", locality " + addresses.get(0).getLocality() + ", " + addresses.get(0).getCountryName() + ", " +
                            addresses.get(0).getAddressLine(0) + ".");
                    out = addresses.get(0).getThoroughfare();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
        if (out == null) out = "No known location";
        return out;

    }

    double getLatitude() {return latitude;}
    double getLongitude() {return  longitude;}
    boolean isEnabled(){
        return enabled;
    }

    @Override
    public void onLocationChanged(final Location location) {
        Log.d(TAG, "onLocationChanged " + location.toString());
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        boolean isUpdated = true;
    }

    @Override
    public void onStatusChanged(String s, int status, Bundle b) {
        Log.d(TAG, "onStatusChanged " + s);
        enabled = status == LocationProvider.AVAILABLE;
    }

    @Override
    public void onProviderEnabled(String s) {
        Log.d(TAG, "onProviderEnabled " + s);
        enabled = true;
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d(TAG, "onProviderDisabled " + s);
        enabled = false;
    }
}
