package de.hka.mobiscore.objects;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Place {
    @SerializedName("uid")
    private int uid;

    @SerializedName("lat")
    private double latitude;

    @SerializedName("lng")
    private double longitude;

    @SerializedName("bike_list")
    private List<Bike> bikes;

    @SerializedName("name")
    private String name;

    @SerializedName("bikes_available_to_rent")
    private int bikeCount;

    @SerializedName("spot")
    private boolean spot;

    // Add other necessary fields based on your needs

    public int getUid() {
        return uid;
    }

    public String getName() {return name; }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getBikeCount() {
        return bikeCount;
    }

    public boolean isSpot() {
        return spot;
    }

    private class Bike{

    }
}

