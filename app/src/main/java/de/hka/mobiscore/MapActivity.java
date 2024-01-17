package de.hka.mobiscore;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.hka.mobiscore.network.EfaApiClient;
import de.hka.mobiscore.network.NextbikeApiClient;
import de.hka.mobiscore.objects.City;
import de.hka.mobiscore.objects.Country;
import de.hka.mobiscore.objects.EfaCoordResponse;
import de.hka.mobiscore.objects.NextbikeApiResponse;
import de.hka.mobiscore.objects.Place;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * @author Felix Wieland
 * @author Simon Jenkner
 * @author Sarah Frietsch
 * @author Joshua Kalemba
 * @author Hendrik Peiler
 */
public class MapActivity extends AppCompatActivity {

    private MapView mapView;

    GeoPoint positionPoint;

    private Marker positionMarker;

    private final List<GeoPoint> bikePointList = new ArrayList<>();

    private final List<GeoPoint> stopPointList = new ArrayList<>();

    Button calculateMobiscoreButton;

    private int antiLagProtection = 0;

    public int preferenceOfBike = 5;

    /**
     * Erstellt alle notwendigen Element, die für die App benötigt werden.
     * Genauere Erklärung ist im Programmtext zu finden
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Setzt die Karte in die App.
        setContentView(R.layout.activity_map);

        //Fragt den Server nach den Kartendaten.
        XYTileSource mapServer = new XYTileSource(
                "MapName",
                8,
                20,
                256,
                ".png",
                new String[]{"https://tileserver.svprod01.app/styles/default/"}
        );

        // Legt den String zur Authorisierung fest.
        String authorizationString = this.getMapServerAuthorizationString(
                "ws2223@hka",
                "LeevwBfDi#2027"
        );

        //Konfiguriert die Anfrage auf den Kartenserver.
        Configuration
                .getInstance()
                .getAdditionalHttpRequestProperties()
                .put("Authorization", authorizationString);



        // Füge den OnClickListener für den Menü-Button hinzu.
        Button btnOpenMenu = findViewById(R.id.btnOpenMenu);
        btnOpenMenu.setOnClickListener(v -> openSeekBarDialog());

        //Setzt einen onClickListener für den Button, der zur aktuellen Location führt.
        Button btnMoveToCurrentLocation = findViewById(R.id.btnMoveToCurrentLocation);
        btnMoveToCurrentLocation.setOnClickListener(v -> moveToCurrentLocation());

        //Macht, dass beim Drücken des Buttons der Mobility-Score berechnet und dargestellt wird.
        calculateMobiscoreButton = findViewById(R.id.btnCalculateMobiscore);
        calculateMobiscoreButton.setOnClickListener(v -> calculateAndDisplayMobilityScore());



        //Setzt die Karte vom Server in die App.
        this.mapView = this.findViewById(R.id.mapId);
        this.mapView.setTileSource(mapServer);

        //Wechsel von Buttons zu Multitouchzoom und setzt den maximalen Zoom auf 15 wegen Performance.
        this.mapView.setMultiTouchControls(true);
        this.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        this.mapView.setMinZoomLevel(15.0);

        //Setzt den aktuellen Standort. Wenn keiner geliefert wird, dann wird der Punkt (49.0069, 8.4037).
        Location lastKnownLocation = getLastKnownLocation();
        if (lastKnownLocation != null) {
            positionPoint = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        } else {
            positionPoint = new GeoPoint(49.0069, 8.4037);
        }

        //Setzt die Karte auf den aktuellen Standort
        IMapController mapController = mapView.getController();
        mapController.setCenter(positionPoint);

        //Fügt einen Marker für den Standort ein.
        positionMarker = new Marker(mapView);
        positionMarker.setPosition(positionPoint);
        positionMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        positionMarker.setTitle("Standort");
        positionMarker.setIcon(getResources().getDrawable(R.mipmap.ic_standort, getTheme()));
        this.mapView.getOverlays().add(positionMarker);


        //Regelt, was bei einer Veränderung des Kartenausschnitts passiert. Nämlich ein neues Laden neuer Geopunkte.
        this.mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                if (shouldLoadNewLocations()) {
                    loadClosestStops(mapView.getMapCenter(), 10000);
                    loadNextbikes(mapView.getMapCenter(), 10000);
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                if (shouldLoadNewLocations()) {
                    loadClosestStops(mapView.getMapCenter(), 10000);
                    loadNextbikes(mapView.getMapCenter(), 10000);
                }
                return false;
            }
        });

        //Lädt die Haltestellen und Nextbikes beim ersten mal öffnen der Karte
        loadClosestStops(positionPoint, 2000);
        loadNextbikes(positionPoint, 2000);

        /*Bewegt die Karte nochmal zum aktuellen Standort, was zu einem schnelleren Laden der Karte führt.
        Dies führt zu einem erneuten und deshalb schnelleren Laden der Karte.
         */
        moveToCurrentLocation();

    }

    /**
     * Ruft den Dienst {@link #initLocationListener()} auf, während die App läuft.
     */
    @Override
    protected void onResume() {
        super.onResume();

        //Fragt ab, welche Erlaubnisse sich bereits eingeholt wurden.
        String[] permission = new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        };

        //Wenn man die Erlaubnis bekommen hat, dann wird der Dienst die Location aktualisiert.
        Permissions.check(this, permission, null, null, new PermissionHandler() {
            @Override
            public void onGranted() {
                initLocationListener();

                Log.d("MapActivity", "onGranted");
            }

            @Override
            public void onDenied(Context context, ArrayList<String> deniedPermissions) {
                super.onDenied(context, deniedPermissions);

                Log.d("MapActivity", "onDenied");
            }
        });
    }

    /**
     * Ändert {@link #positionPoint}, wenn sich der Standort ändert und setzt den {@link #positionMarker} neu.
     */
    @SuppressLint("MissingPermission")
    private void initLocationListener() {


        LocationListener locationListener = location -> {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            positionPoint = new GeoPoint(latitude, longitude);

            positionMarker.setPosition(positionPoint);

        };

        //Legt fest, ab welcher Standortänderung der LocationManager anschlägt.
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, locationListener);

    }

    /**
     * Setzt Autorisierungsstring für den Zugriff auf den Kartenserver zusammen.
     *
     * @param username Benutzername, der für den Kartenserver genutzt wird.
     * @param password Passwort, das für den Kartenserver genutzt wird.
     * @return String, der für die weiter Verarbeitung von Benutzername und Passwort gentzt wird.
     */
    private String getMapServerAuthorizationString(String username, String password) {
        String authorizationString = String.format("%s:%s", username, password);
        return "Basic " + Base64.encodeToString(authorizationString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
    }

    /**
     * Fragt die Efa-API für alle ÖV-Stationen im Umkreis von einem bestimmten Radius um einen bestimmten IGeoPoint an.
     *
     * @param geoPoint Der IGeoPoint um welchen die Nextbikes gescuht werden. (IGeoPoint wegen der MapView)
     * @param radius   Der Radius in Meter in welchem um den geoPoint nach Nextbikes gesucht wird.
     */
    private void loadClosestStops(IGeoPoint geoPoint, int radius) {
        //Erstellt latitude und longitude für die einfachere Verwendung.
        double latitude = geoPoint.getLatitude();
        double longitude = geoPoint.getLongitude();

        //Fragt die Efa an
        Call<EfaCoordResponse> efaCall = EfaApiClient
                .getInstance()
                .getClient()
                .loadStopsWithinRadius(
                        EfaApiClient
                                .getInstance()
                                .createCoordinateString(
                                        latitude,
                                        longitude
                                ),
                        radius
                );

        //Verarbeitet die Daten, welche von der Efa geliefert werden.
        efaCall.enqueue(new Callback<EfaCoordResponse>() {
            @SuppressLint("UseCompatLoadingForDrawables")
            @Override
            public void onResponse(Call<EfaCoordResponse> call, Response<EfaCoordResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getLocations() != null) {
                    EfaCoordResponse efaCoordResponse = response.body();
                    Log.d("MapActivity", String.format("Response %d Locations", efaCoordResponse.getLocations().size()));
                    Log.d("MapActivity", String.format("Response for Location: %f, %f", latitude, longitude));

                    // Erstellt eine Liste mit allen GeoPunkten, die geliefert werden und fügt einen Marker auf der Karte hinzu.
                    for (de.hka.mobiscore.objects.Location location : efaCoordResponse.getLocations()) {
                        GeoPoint geoPoint = new GeoPoint(location.getCoordinates()[0], location.getCoordinates()[1], location.getProductClasses()[0]);

                        if (!stopPointList.contains(geoPoint)) {
                            stopPointList.add(geoPoint);
                            Marker stopMarker = new Marker(mapView);
                            stopMarker.setPosition(geoPoint);
                            stopMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                            stopMarker.setTitle(location.getProperties().stopNameWithPlace);

                            if (geoPoint.getAltitude() == 0) {
                                stopMarker.setIcon(getResources().getDrawable(R.mipmap.ic_train, getTheme()));

                            } else if (geoPoint.getAltitude() <= 4)
                                stopMarker.setIcon(getResources().getDrawable(R.mipmap.ic_tram, getTheme()));
                            else {
                                stopMarker.setIcon(getResources().getDrawable(R.mipmap.ic_bus_yellow, getTheme()));
                            }

                            mapView.getOverlays().add(stopMarker);
                        }
                    }

                    //Entfernen und erneutes Laden des Standortes, damit er als oberste Ebene angezeigt wird.
                    mapView.getOverlays().remove(positionMarker);
                    mapView.getOverlays().add(positionMarker);


                } else {
                    // Fehlermeldung bei leerer gelieferten Abfrage
                    Log.e("MapActivity", "API-Aufruf nicht erfolgreich. Code: " + response.code());
                }
            }

            //Fehlermeldung bei unerfolgreicher Abfrage
            @Override
            public void onFailure(Call<EfaCoordResponse> call, Throwable t) {
                Log.d("MapActivity", "Failure");
            }
        });
    }

    /**
     * Fragt die Nextbike-API für alle Nextbikes im Umkreis von einem bestimmten Radius um einen bestimmten IGeoPoint an.
     *
     * @param geoPoint Der IGeoPoint um welchen die Nextbikes gescuht werden.
     * @param radius   Der Radius in Meter in welchem um den geoPoint nach Nextbikes gesucht wird.
     */
    private void loadNextbikes(IGeoPoint geoPoint, int radius) {
        //Erstellt latitude und longitude für die einfachere Verwendung.
        double latitude = geoPoint.getLatitude();
        double longitude = geoPoint.getLongitude();

        //Fragt die Nextbike API
        Call<NextbikeApiResponse> nextbikeCall = NextbikeApiClient
                .getInstance()
                .getClient()
                .getNextbikeData(latitude, longitude, radius);

        // Erstellt eine Liste mit allen GeoPunkten, die geliefert werden und fügt einen Marker auf der Karte hinzu.
        nextbikeCall.enqueue(new Callback<NextbikeApiResponse>() {
            @SuppressLint("UseCompatLoadingForDrawables")
            @Override
            public void onResponse(Call<NextbikeApiResponse> call, Response<NextbikeApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCountries() != null) {

                    // Erstellt eine Liste mit allen GeoPunkten, die geliefert werden und fügt einen Marker auf der Karte hinzu.
                    for (Country country : response.body().getCountries()) {
                        for (City city : country.getCities()) {
                            for (Place place : city.getPlaces()) {
                                if (place.isSpot() || place.getBikeCount() > 0) {
                                    GeoPoint bikePoint = new GeoPoint(place.getLatitude(), place.getLongitude(), place.getBikeCount());
                                    if (!bikePointList.contains(bikePoint)) {
                                        bikePointList.add(bikePoint);

                                        Marker bikeMarker = new Marker(mapView);
                                        bikeMarker.setPosition(bikePoint);
                                        bikeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                                        if (place.isSpot()) {
                                            if (place.getBikeCount() != 1) {
                                                bikeMarker.setTitle(place.getName() + "\n" + place.getBikeCount() + " verfügbare Fahrräder");
                                            } else {
                                                bikeMarker.setTitle(place.getName() + "\n1 verfügbares Fahrrad");
                                            }
                                            bikeMarker.setIcon(getResources().getDrawable(R.mipmap.ic_radstation, getTheme()));
                                        } else {
                                            bikeMarker.setTitle(place.getName());
                                            bikeMarker.setIcon(getResources().getDrawable(R.mipmap.ic_rad, getTheme()));
                                        }


                                        mapView.getOverlays().add(bikeMarker);
                                    }
                                }
                            }
                        }
                    }
                    //Entfernen und erneutes Laden des Standortes, damit er als oberste Ebene angezeigt wird.
                    mapView.getOverlays().remove(positionMarker);
                    mapView.getOverlays().add(positionMarker);

                } else {
                    // Nicht zu verwertende Antwort
                    Log.e("MapActivity", "Nextbike API-Aufruf nicht erfolgreich. Code: " + response.code());
                    if (response.isSuccessful()) {
                        Log.e("MapActivity", "Antwort war erfolgreich");
                    } else {

                        Log.e("MapActivity", "Antwort war nicht erfolgreich");
                    }
                    if (response.body() != null) {
                        Log.e("MapActivity", "Antwort ist nicht leer");
                    } else {

                        Log.e("MapActivity", "Antwort ist leer");
                    }
                    if (response.body().getCountries() != null) {
                        Log.e("MapActivity", "Antwort liefert Nextbikes");
                    } else {

                        Log.e("MapActivity", "Antwort liefert keine Nextbikes");
                    }
                }
            }

            //Fehler beim Anfragen der API (Keine Antwort),
            @Override
            public void onFailure(Call<NextbikeApiResponse> call, Throwable t) {
                Log.e("MapActivity", "Nextbike API-Aufruf fehlgeschlagen.", t);
            }
        });
    }

    /**
     * Liefert, ob die APIs neue angefragt werden sollen abhängig von der Menge an Standortveränderungen, damit es nicht zu Lags kommt.
     * Nutzt dafür die Variable {@link #antiLagProtection}.
     *
     * @return Liefert ob die APIs neu angefragt werden.
     */
    private boolean shouldLoadNewLocations() {
        if (antiLagProtection >= 40) {
            antiLagProtection = 0;
            return true;
        } else {
            antiLagProtection++;
            return false;
        }
    }

    /**
     * Berechnet die Distanz zwischen zwei Geopunkten.
     *
     * @param point1 Erster Geopunkt von dem aus der Standort berechnet werden soll.
     * @param point2 Zweiter Geopunkt von dem aus der Standort zum ersten berechnet werden soll.
     * @return Dinstanz zwischen den beiden Punkten in Metern.
     */
    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        double lat1 = Math.toRadians(point1.getLatitude());
        double lon1 = Math.toRadians(point1.getLongitude());
        double lat2 = Math.toRadians(point2.getLatitude());
        double lon2 = Math.toRadians(point2.getLongitude());

        // Haversine-Formel
        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Erdradius in Metern (kann je nach Anwendung angepasst werden)
        double radius = 6371000;

        // Berechne die Entfernung
        return radius * c;
    }

    /**
     * Liefert den letzten Standort, der erfasst wurde
     *
     * @return Aktuellen Standort
     */
    private Location getLastKnownLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = locationManager.getBestProvider(criteria, false);

        try {
            if(bestProvider != null) {
                return locationManager.getLastKnownLocation(bestProvider);
            }else{
                return null;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Liefert die Anzahl an ÖV-Stationen in einem bestimmten Radius um den aktuellen Standort.
     *
     * @param radiusInMeters Radius in welchem ÖV-Stationen gezählt werden.
     * @return Anzahl an ÖV-Stationen.
     */
    private int countClosestStopsInRange(int radiusInMeters) {
        int stopsInRange = 0;
        for (GeoPoint geoPoint : stopPointList) {
            if (calculateDistance(positionPoint, geoPoint) <= radiusInMeters) {
                stopsInRange++;
            }
        }
        return stopsInRange;
    }

    /**
     * Liefert den besten Produkttyp einer ÖV-Station in einem bestimmten Radius um den aktuellen Standort.
     *
     * @param radiusInMeters Radius in welchem die ÖV-Stationen geprüft werden.
     * @return Den Besten Prdukttyp, also mit der niedrigsten Nummer.
     */
    private int getBestProductTypeInRange(int radiusInMeters) {
        int bestProductType = 20;
        for (GeoPoint geoPoint : stopPointList) {
            if (calculateDistance(positionPoint, geoPoint) <= radiusInMeters && geoPoint.getAltitude() < bestProductType) {
                bestProductType = (int) geoPoint.getAltitude();
            }
        }
        return bestProductType;
    }

    /**
     * Liefert die Anzahl an Nextbikes in einem bestimmten Radius um den aktuellen Standort.
     *
     * @param radiusInMeters Radius in welchem die Nextbikes gezählt werden.
     * @return Anzahl an Nextbikes.
     */
    private int countNextbikesInRange(int radiusInMeters) {
        int bikesInRange = 0;
        for (GeoPoint geoPoint : bikePointList) {
            if (calculateDistance(positionPoint, geoPoint) <= radiusInMeters) {
                bikesInRange += geoPoint.getAltitude();
            }
        }
        return bikesInRange;
    }

    /**
     * Berechnet den Mobilityscore und schreibt ihn auf den Bildschirm.
     */
    private void calculateAndDisplayMobilityScore() {

        //Verarbeietet die Präferenz die angegeben wurde.
        int preferenceOfOeV = 10 - preferenceOfBike;

        //Erstellt die Variable, die den MobilityScore darstellt.
        int mobilityScore = 0;

        //Berechnung der Punkte für ÖV-Stationenn im  näheren Umkreis.
        int[] stopRanges = {75, 150, 300, 500, 750};

        Log.d("MapActivity", "Mit einem Verhältnis von ÖV: " + preferenceOfOeV + " und Nextbike: " + preferenceOfBike + " wurde folgender Mobiscore berechnet:");

        for (int i = 0; i < stopRanges.length; i++) {
            if (countClosestStopsInRange(stopRanges[i]) >= 1) {
                int mayAddedPoints = (((stopRanges.length - i) * 5) * preferenceOfOeV) / 5;
                mobilityScore += mayAddedPoints;
                if (i > 0) {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Entfernung zur nächsten Bahnstation um "
                            + mayAddedPoints + " Punkte erhöht, weil die nächste Station zwischen " + stopRanges[i - 1] + " und "
                            + stopRanges[i] + " Meter entfernt ist.");
                } else {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Entfernung zur nächsten Bahnstation um "
                            + mayAddedPoints + " Punkte erhöht, weil die nächste Station weniger als " + stopRanges[i] + " Meter entfernt ist.");
                }
                break;
            }
        }

        //Berechnung der Punkte für die beste Station im Umkreis über die Produktklassen.
        int[] productClassLimit = {0, 2, 4, 7, 10};

        for (int i = 0; i < productClassLimit.length; i++) {
            if (getBestProductTypeInRange(stopRanges[stopRanges.length - 1]) <= productClassLimit[i]) {
                int mayAddedPoints = (((productClassLimit.length - i) * 5) * preferenceOfOeV) / 5;
                mobilityScore += mayAddedPoints;
                Log.d("MapActivity", "Der MobilityScore hat sich durch die beste Produktklasse einer Station im Umkreis von "
                        + stopRanges[stopRanges.length - 1] + " Meter um "
                        + mayAddedPoints + " Punkte erhöht, weil eine Station mindestens die Produktklasse " + productClassLimit[i] + " besitzt");
                break;
            }
        }

        //Berechnung der Punkte für Nextbikes je nach Umkreis.
        int[] bikeRanges = {50, 100, 200, 300, 500};

        for (int i = 0; i < bikeRanges.length; i++) {
            if (countNextbikesInRange(bikeRanges[i]) >= 1) {
                int mayAddedPoints = (((bikeRanges.length - i) * 5) * preferenceOfBike) / 5;
                mobilityScore += mayAddedPoints;
                if (i > 0) {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Entfernung zum nächsten Fahrrad um "
                            + mayAddedPoints + " Punkte erhöht, weil das nächste Fahrrad zwischen " + bikeRanges[i - 1] + " und "
                            + bikeRanges[i] + " Meter entfernt ist.");
                } else {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Entfernung zum nächsten Fahrrad um "
                            + mayAddedPoints + " Punkte erhöht, weil das nächste Fahrrad weniger als "
                            + bikeRanges[i] + " Meter entfernt ist.");
                }
                break;
            }
        }

        //Berechnung der Punkte die Anzahl der nextbikes in einem bestimmten Umkreis.
        int[] bikeCountRanges = {3, 6, 9, 12, 15};

        for (int i = bikeCountRanges.length - 1; i >= 0; i--) {
            if (countNextbikesInRange(bikeRanges[bikeRanges.length - 1]) >= bikeCountRanges[i]) {
                int mayAddedPoints = (((i + 1) * 5) * preferenceOfBike) / 5;
                mobilityScore += mayAddedPoints;

                if (i + 1 < bikeCountRanges.length) {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Anzahl der Fahrräder im Umkreis von " +
                            bikeRanges[bikeRanges.length - 1] + " Meter um " + mayAddedPoints + " Punkte erhöht, weil es mehr als "
                            + bikeCountRanges[i] + ", aber weniger als " + bikeCountRanges[i + 1] + " Fahrräder sind");
                } else {
                    Log.d("MapActivity", "Der MobilityScore hat sich durch die Anzahl der Fahrräder im Umkreis von " +
                            bikeRanges[bikeRanges.length - 1] + " Meter um " + mayAddedPoints + " Punkte erhöht, weil es mehr als "
                            + bikeCountRanges[i] + " Fahrräder sind");
                }
                break;
            }
        }

        // Zeige den Mobility-Score im Button an
        String mobilityScoreLabel = getString(R.string.mobility_score_label, String.valueOf(mobilityScore));
        calculateMobiscoreButton.setText(mobilityScoreLabel);
    }

    /**
     * Öffnet das Fenster in welchem mit der SeekBar präferenzen eingegeben werden können und wo die Informationen über die Berechnung zu finden sind.
     */
    private void openSeekBarDialog() {

        calculateMobiscoreButton.setText(R.string.default_mobiscore);
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_seekbar);

        // Setzt die aktuelle Einstellung für preferenceOfBike in den Dialog.
        SeekBar seekBar = dialog.findViewById(R.id.seekBar);
        seekBar.setProgress(preferenceOfBike);

        // Setzt den OK-Button-ClickListener.
        Button btnOK = dialog.findViewById(R.id.btnOK);
        btnOK.setOnClickListener(v -> {
            // Aktualisiert preferenceOfBike mit dem Wert aus der SeekBar.
            preferenceOfBike = seekBar.getProgress();
            // Schließt das Dialogfenster.
            dialog.dismiss();
        });

        // Zeigt das Dialogfenster an.
        dialog.show();
    }

    /**
     * Bewegt den Kartenausschnitt zum aktuellen Standort.
     */
    private void moveToCurrentLocation() {
        GeoPoint currentLocation = new GeoPoint(positionMarker.getPosition().getLatitude(), positionMarker.getPosition().getLongitude());
        IMapController mapController = mapView.getController();
        mapController.animateTo(currentLocation);
        mapController.setZoom(17.0);
    }
}