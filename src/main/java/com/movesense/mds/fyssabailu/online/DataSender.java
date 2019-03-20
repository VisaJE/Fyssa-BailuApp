package com.movesense.mds.fyssabailu.online;

import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.StringRequest;

import java.io.File;

public class DataSender {
    private final String TAG = DataSender.class.getSimpleName();
    private RequestQueue mRequestQueue;
    private Cache cache;
    private DataUser context;


    public DataSender(File cacheD, DataUser context) {
        this.context = context;
        // Instantiate the cache
        cache = new DiskBasedCache(cacheD, 1024 * 1024); // 1MB cap

        // Set up the network to use HttpURLConnection as the HTTP client.
        Network network = new BasicNetwork(new HurlStack());

        // Instantiate the RequestQueue with the cache and network.
        mRequestQueue = new RequestQueue(cache, network);
        // Start the queue
        mRequestQueue.start();
    }

    public void post(String url) {
        // Formulate the request and handle the response.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d(TAG, "Got response: " + response);
                    context.onPostSuccess(response);
                },
                error -> {
                    Log.e(TAG, "Fail!", error);
                    context.onPostError(error);
                });
        // Add the request to the RequestQueue.
        mRequestQueue.add(stringRequest);
    }
    public void get(String url) {
        // Formulate the request and handle the response.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    Log.d(TAG, "Got response: " +response);
                    context.onGetSuccess(response);
                },
                error -> {
                    Log.e(TAG, "Fail!", error);
                    context.onGetError(error);
                });
        // Add the request to the RequestQueue.
        mRequestQueue.add(stringRequest);
    }
}

