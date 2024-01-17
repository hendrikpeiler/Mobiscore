package de.hka.mobiscore.objects;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class NextbikeApiResponse {
    private List<NextbikeObject> bikes;


    @SerializedName("countries") public List<Country> countries;

    public List<Country> getCountries() {
        return countries;
    }
    /*public List<NextbikeObject> getNextbikes() {
        return bikes;
    }

    public void setNextbikes(List<NextbikeObject> bikes) {
        this.bikes = bikes;
    }*/
}
