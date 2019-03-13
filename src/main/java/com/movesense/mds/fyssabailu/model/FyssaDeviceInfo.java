package com.movesense.mds.fyssabailu.model;


import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;


public class FyssaDeviceInfo {

    @SerializedName("Content")
    private Content content;

    protected class Content {
        @SerializedName("serial")
        private String serial;

        @SerializedName("SwVersion")
        private String swVersion;

        @SerializedName("addressInfo")
        private JsonArray addressInfo;
    }



    public String getSerial() {
        return content.serial;
    }

    public String getSwVersion() {
        return content.swVersion;
    }

    public Content getContent() { return content;}
    public JsonArray getAddressInfo() {return content.addressInfo;}

    public String getDfuAddress() {
        return getAddressInfo().get(0).getAsJsonObject().get("address").getAsString();
    }
}
