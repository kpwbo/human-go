package ca.polymtl.inf8405.tp2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener, NfcAdapter.CreateNdefMessageCallback {
    private String hash;
    private static final String API_URL = "http://159.89.54.202/nearby";
    private MapView mapView;
    private GoogleMap gMap;
    private FusedLocationProviderClient locationClient;
    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    private double lat = 0;
    private double lng = 0;
    private final List<Marker> userMarkers = new ArrayList<>();
    private Marker userMarker;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        hash = getIntent().getStringExtra("hash");

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(mapViewBundle);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            System.out.println("NFC is not available");
        }
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        getPermissions();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor == lightSensor && gMap != null) {
            if (event.values[0] < 10) {
                gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.night_mode));
            } else {
                gMap.setMapStyle(null);
            }
        }
    }

    private void getPermissions() {
        final String[] requiredPermissions = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET, Manifest.permission.NFC };
        final List<String> toAskPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                toAskPermissions.add(permission);
            }
        }
        if (toAskPermissions.size() > 0) {
            ActivityCompat.requestPermissions(this, toAskPermissions.toArray(new String[toAskPermissions.size()]), 0);
        } else {
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    System.err.println("Permission denied");
                }
            }
            mapView.getMapAsync(this);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        nfcAdapter.setNdefPushMessageCallback(this, this);
        getLocation();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String text = "wot";
        return new NdefMessage(new NdefRecord[] {
           NdefRecord.createMime("application/ca.polymtl.inf8405.tp2", text.getBytes())
        });
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setFastestInterval(1000);
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    if (userMarker != null) {
                        userMarker.remove();
                    }
                    lat = location.getLatitude();
                    lng = location.getLongitude();
                    final MarkerOptions options = new MarkerOptions();
                    options.position(new LatLng(lat, lng));
                    options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                    userMarker = gMap.addMarker(options);
                }
                new GetNearbyUsers(MapActivity.this).execute(lat, lng);
            }
        };

        locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
        }
        mapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    private void processIntent(Intent intent) {
        Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) messages[0];
        System.out.println("got : " + new String(msg.getRecords()[0].getPayload()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
        sensorManager.unregisterListener(this);
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    static class GetNearbyUsers extends AsyncTask<Double, Void, String> {
        private final WeakReference<MapActivity> context;

        GetNearbyUsers(final MapActivity context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        protected String doInBackground(Double... coordinates) {
            try {
                final URL url = new URL(API_URL);
                final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setUseCaches(false);
                urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                final OutputStream os = urlConnection.getOutputStream();
                final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write("hash=" + context.get().hash + "&lat=" + coordinates[0] + "&lng=" + coordinates[1]);
                writer.flush();
                writer.close();
                os.close();
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    final StringBuilder bodyBuilder = new StringBuilder();
                    final BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        bodyBuilder.append(line);
                    }
                    return bodyBuilder.toString();
                } else {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    for (final Marker userMarker : context.get().userMarkers) {
                        userMarker.remove();
                    }
                    final JSONArray users = new JSONArray(result);
                    for (int i = 0; i < users.length(); ++i) {
                        final JSONObject user = users.getJSONObject(i);
                        final double lat = user.getDouble("lat");
                        final double lng = user.getDouble("lng");
                        final LatLng coordinates = new LatLng(lat ,lng);
                        context.get().userMarkers.add(context.get().gMap.addMarker(new MarkerOptions().position(coordinates)));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}