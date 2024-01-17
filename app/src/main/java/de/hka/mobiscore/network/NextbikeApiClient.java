package de.hka.mobiscore.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NextbikeApiClient {
    private static final String BASE_URL = "https://api.nextbike.net/";

    private static NextbikeApiClient instance;
    private final NextbikeApi nextbikeApiInterface;

    private NextbikeApiClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        nextbikeApiInterface = retrofit.create(NextbikeApi.class);
    }

    public static NextbikeApiClient getInstance() {
        if (instance == null) {
            instance = new NextbikeApiClient();
        }
        return instance;
    }

    public NextbikeApi getClient() {
        return nextbikeApiInterface;
    }
}

