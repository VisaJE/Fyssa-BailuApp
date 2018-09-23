package com.movesense.mds.fyssabailu.model;

import com.google.gson.annotations.SerializedName;

public class EnergyGet {

    @SerializedName("Content")
    private Content content;
    public EnergyGet(Content content) {
        this.content = content;
    }
    protected class Content {
        @SerializedName("Percent")
        private int percent;
        @SerializedName("MilliVoltages")
        private int voltage;
        @SerializedName("InternalResistance")
        private int resistance;


        public Content(int percent, int voltage, int resistance) {
            this.percent = percent;
            this.voltage = voltage;
            this.resistance = resistance;
        }
    }
    public int getPercentage() {
        return content.percent;
    }
    public int getVoltage() {
        return content.voltage;
    }
    public int getResistance() {
        return content.resistance;
    }
}
