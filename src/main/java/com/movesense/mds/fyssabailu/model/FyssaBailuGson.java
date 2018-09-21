package com.movesense.mds.fyssabailu.model;


import com.google.gson.annotations.SerializedName;

public class FyssaBailuGson {

    @SerializedName("fyssaBailuConfig")
    private final FyssaBailuConfig fyssaBailuConfig;

    public FyssaBailuGson(FyssaBailuConfig fbc) {
        fyssaBailuConfig = fbc;
    }

    public static class FyssaBailuConfig {

        @SerializedName("time")
        public final int time;
        @SerializedName("threshold")
        public final int threshold;

        public FyssaBailuConfig(int time, int threshold) {
            this.time = time;
            this.threshold = threshold;
        }
    }
}
