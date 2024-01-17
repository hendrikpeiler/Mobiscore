package de.hka.mobiscore.network;

import de.hka.mobiscore.objects.NextbikeApiResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface NextbikeApi {
    @GET("maps/nextbike-live.json")
    Call<NextbikeApiResponse> getNextbikeData(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("distance") int radius
    );
}
