package de.hka.mobiscore.objects;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Country {
    @SerializedName("cities")
    private List<City> cities;

    public List<City> getCities() {
        return cities;
    }

    public void setCities(List<City> cities) {
        this.cities = cities;
    }
}
