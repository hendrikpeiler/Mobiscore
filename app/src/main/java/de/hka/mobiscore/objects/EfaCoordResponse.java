package de.hka.mobiscore.objects;

import de.hka.mobiscore.objects.Location;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EfaCoordResponse {

    @SerializedName("versions")
    public String version;

    @SerializedName("locations")
    public List<Location> locations;

    public String getVersion() {
        return version;
    }

    public List<Location> getLocations() {
        return locations;
    }

}
