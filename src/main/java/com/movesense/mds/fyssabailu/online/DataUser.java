package com.movesense.mds.fyssabailu.online;

import com.android.volley.VolleyError;

public interface DataUser {
    void onGetSuccess(String response);
    void onGetError(VolleyError error);
    void onPostSuccess(String response);
    void onPostError(VolleyError error);
}