package de.hka.mobiscore.objects;

import com.google.gson.annotations.SerializedName;

public class Location {

    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("coord")
    public double[] coordinates;

    @SerializedName("productClasses")
    public int[] productClasses;

    @SerializedName("properties")
    public LocationProperties properties;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double[] getCoordinates() {
        return coordinates;
    }

    public int[] getProductClasses() {
        return productClasses;
    }

    public LocationProperties getProperties() {
        return properties;
    }
}
