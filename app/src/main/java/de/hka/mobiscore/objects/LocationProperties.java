package de.hka.mobiscore.objects;

import com.google.gson.annotations.SerializedName;

public class LocationProperties {

    @SerializedName("distance")
    public double distance;

    @SerializedName("STOP_NAME_WITH_PLACE")
    public String stopNameWithPlace;

    public double getDistance() {
        return distance;
    }

    public String getStopNameWithPlace() {
        return stopNameWithPlace;
    }
}
