package com.movesense.mds.fyssabailu.model;

import com.google.gson.annotations.SerializedName;

public class FyssaPartyResponse {

    @SerializedName("parties")
    private Party[] parties;

    public class Party {
        @SerializedName("description")
        public String description;

        @SerializedName("length")
        public Double partyTime;

        @SerializedName("place")
        public String place;

        @SerializedName("population")
        public Integer population;

        @SerializedName("score")
        public Integer score;

        @SerializedName("timeStarted")
        public String timeStarted;

        @SerializedName("lastSeen")
        public String lastSeen;
    }

    public Party[] getParties() {
        return parties;
    }
}
