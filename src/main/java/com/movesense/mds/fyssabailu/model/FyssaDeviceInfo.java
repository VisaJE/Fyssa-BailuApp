package com.movesense.mds.fyssabailu.model;


import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;


public class FyssaDeviceInfo {

    @SerializedName("serial")
    private String serial;

    @SerializedName("SwVersion")
    private String swVersion;

    @SerializedName("addressInfo")
    private JsonArray addressInfo;

    public String getSerial() {
        return serial;
    }

    public String getSwVersion() {
        return swVersion;
    }

    public JsonArray getAddressInfo() {return addressInfo;}

    public String getDfuAddress() {
        return getAddressInfo().get(1).getAsJsonObject().get("address").getAsString();
    }
}
