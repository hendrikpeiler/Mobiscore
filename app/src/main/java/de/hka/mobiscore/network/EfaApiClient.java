package de.hka.mobiscore.network;

import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EfaApiClient {

    private static EfaApiClient instance;

    private Retrofit retrofit;

    public static EfaApiClient getInstance() {
        if (instance == null){
            instance = new EfaApiClient();
        }

        return instance;
    }

    public EfaApiClient() {
        this.retrofit = new Retrofit.Builder()
                .baseUrl("https://www.bwegt.de/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public String createCoordinateString(double latitude, double longitude) {
        return String.format(Locale.ENGLISH, "%f:%f:WGS84[dd.ddddd]", longitude, latitude);
    }

    public EfaApi getClient() {
        return this.retrofit.create(EfaApi.class);
    }

}
