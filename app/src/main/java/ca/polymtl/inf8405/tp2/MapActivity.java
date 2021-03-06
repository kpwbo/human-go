package ca.polymtl.inf8405.tp2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.TrafficStats;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a map to the user.
 */
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener, NfcAdapter.CreateNdefMessageCallback {
    /**
     * Hash of the avatar of the user.
     */
    private String hash;
    /**
     * Bytes of the avatar of the user.
     */
    private byte[] bytes;
    /**
     * URL of the API.
     */
    private static final String API_URL = "http://159.89.54.202/nearby";
    /**
     * Reference to the map view.
     */
    private MapView mapView;
    /**
     * Reference to the google map.
     */
    private GoogleMap gMap;
    /**
     * Location Client used to get location.
     */
    private FusedLocationProviderClient locationClient;
    /**
     * Key used to bundle the map view.
     */
    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    /**
     * Latitude of the user.
     */
    private double lat = 0;
    /**
     * Longitude of the user.
     */
    private double lng = 0;
    /**
     * Markers of the other users.
     */
    private final List<Marker> userMarkers = new ArrayList<>();
    /**
     * Marker of the user.
     */
    private Marker userMarker;
    /**
     * Sensor Manager.
     */
    private SensorManager sensorManager;
    /**
     * Sensor used to get light information.
     */
    private Sensor lightSensor;
    /**
     * NFC adapater.
     */
    private NfcAdapter nfcAdapter;
    /**
     * Used to capture NFC events.
     */
    private PendingIntent pendingIntent;
    /**
     * Original level of the battery.
     */
    private Float originalBatteryLevel = null;
    /**
     * Current level of the battery.
     */
    private Float currentBatteryLevel = null;
    /**
     * Original count of the received bytes.
     */
    private long originalReceivedBytes = 0;
    /**
     * Current count of the received bytes.
     */
    private long currentReceivedBytes = 0;
    /**
     * Original count of the transmitted bytes.
     */
    private long originalTransmittedBytes = 0;
    /**
     * Current count of the transmitted bytes.
     */
    private long currentTransmittedBytes = 0;
    /**
     * Handler used to schedule network information gathering.
     */
    private Handler handler = new Handler();

    /**
     * Called when the activity is started.
     * Gets the information from the previous activity.
     * Sets up battery, network, location, light, and NFC management.
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        hash = getIntent().getStringExtra("hash");
        bytes = getIntent().getByteArrayExtra("bytes");
        originalBatteryLevel = (Float) getIntent().getSerializableExtra("originalBattery");
        currentBatteryLevel = (Float) getIntent().getSerializableExtra("currentBattery");
        originalReceivedBytes = getIntent().getLongExtra("originalReceived", 0);
        currentReceivedBytes = getIntent().getLongExtra("currentReceived", 0);
        originalTransmittedBytes = getIntent().getLongExtra("originalTransmitted", 0);
        currentTransmittedBytes = getIntent().getLongExtra("currentTransmitted", 0);

        manageBattery();
        handler.postDelayed(new NetworkInfoManager(), 1000);
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

    /**
     * Update the title with battery and network information.
     */
    @SuppressLint("DefaultLocale")
    private void updateTitle() {
        setTitle(String.format("Pile %.0f%%, %s down, %s up",
                100*(originalBatteryLevel - currentBatteryLevel),
                Formatter.formatShortFileSize(this, currentReceivedBytes - originalReceivedBytes),
                Formatter.formatShortFileSize(this, currentTransmittedBytes - originalTransmittedBytes)));
    }

    /**
     * Runnable that retrieves network information every second.
     */
    private class NetworkInfoManager implements Runnable {
        /**
         * Retrieves network information and updates the title.
         */
        public void run() {
            currentReceivedBytes = TrafficStats.getTotalRxBytes();
            currentTransmittedBytes = TrafficStats.getTotalTxBytes();
            updateTitle();
            handler.postDelayed(new NetworkInfoManager(), 1000);
        }
    }

    /**
     * Sets up a receiver of battery changes.
     */
    private void manageBattery() {
        registerReceiver(new BroadcastReceiver() {
            /**
             * Called when the battery level changes. Updates the battery information and the title.
             * @param context
             * @param intent
             */
            @Override
            public void onReceive(Context context, Intent intent) {
                final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                currentBatteryLevel = level / (float) scale;
                if (originalBatteryLevel == null) {
                    originalBatteryLevel = currentBatteryLevel;
                }
                updateTitle();
            }
        }, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    /**
     * Called when sensor accuracy changes. Doesn't do anything.
     * @param sensor not used
     * @param accuracy not used
     */
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing
    }

    /**
     * Called when the sensor's value has changed.
     * Changes the style of the map depending on the value of the light sensor.
     * @param event event containing the relevant information.
     */
    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor == lightSensor && gMap != null) {
            if (event.values[0] < 1) {
                gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.night_mode));
            } else {
                gMap.setMapStyle(null);
            }
        }
    }

    /**
     * Gets necessary permissions if needed. Then gets the map.
     */
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

    /**
     * Called when the permissions have been granted or denied. Gets the map.
     * @param requestCode code of the request
     * @param permissions not used
     * @param grantResults results of the request
     */
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

    /**
     * Called when the map is ready. Sets up NFC and gets the location.
     * @param googleMap google map
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        nfcAdapter.setNdefPushMessageCallback(this, this);
        getLocation();
    }

    /**
     * Called when an NFC transmission is requested. Sends the bytes of the avatar.
     * @param event not used
     * @return the created NdefMessage
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        return new NdefMessage(new NdefRecord[] {
           NdefRecord.createMime("application/ca.polymtl.inf8405.tp2", bytes)
        });
    }

    /**
     * Sets up a receiver for location changes every second.
     */
    @SuppressLint("MissingPermission")
    private void getLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setFastestInterval(1000);
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            /**
             * Callend when the location changes. Updates the user marker on the map, then gets nearby users.
             * @param locationResult new location
             */
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

    /**
     * Called when the instance needs to be saved.
     * @param outState out state
     */
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

    /**
     * Called when there is a new intent.
     * @param intent new intent
     */
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    /**
     * Called when the activity resumes.
     */
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

    /**
     * Called when information came from NFC. Reads the bytes, hashes them and puts the information in the database.
     * @param intent Information
     */
    private void processIntent(Intent intent) {
        Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) messages[0];
        byte[] otherUserBytes = msg.getRecords()[0].getPayload();
        String hash = hash(otherUserBytes);
        new AddUsersTask(this).execute(new User(hash, otherUserBytes, 0));
    }

    /**
     * Hashes (MD5) an array of bytes.
     * @param bytes Bytes to hash.
     * @return Hash.
     */
    private static String hash(final byte[] bytes) {
        try {
            final StringBuilder sb = new StringBuilder();
            for (byte hashByte : MessageDigest.getInstance("MD5").digest(bytes)) {
                sb.append(Integer.toHexString((hashByte & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * Called when the activity is started.
     */
    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    /**
     * Called when the activity is stopped.
     */
    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    /**
     * Called when the activity is paused.
     */
    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
        sensorManager.unregisterListener(this);
        nfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * Called when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    /**
     * Called when the activity has low memory.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    /**
     * Makes a call to the API to get nearby users and updates the map with the results.
     */
    static class GetNearbyUsers extends AsyncTask<Double, Void, String> {
        /**
         * Reference to the context.
         */
        private final WeakReference<MapActivity> context;

        /**
         * Constructor.
         * @param context reference to the context
         */
        GetNearbyUsers(final MapActivity context) {
            this.context = new WeakReference<>(context);
        }

        /**
         * Make an API call to get nearby users.
         * @param coordinates Coordinates of the user.
         * @return Result of the API call.
         */
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

        /**
         * Updates the markers on the map depending on the result of the API call.
         * @param result result of the API call
         */
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
						new PlaceUserMarkerTask(context.get(), user).execute();
					}
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Asynchronously adds users to the database.
     */
    private static class AddUsersTask extends AsyncTask<User, Void, Void> {
        /**
         * Reference to the context.
         */
        private final WeakReference<MapActivity> context;

        /**
         * Constructor.
         * @param context reference to the context
         */
        AddUsersTask(final MapActivity context) {
            this.context = new WeakReference<>(context);
        }

        /**
         * Adds users to the database.
         * @param users Users to add.
         * @return Nothing.
         */
        @Override
        protected Void doInBackground(User... users) {
            UserDatabase.getInstance(context.get()).getUserDao().insert(users);
            return null;
        }
    }
	
	/**
     * Asynchronously adds user marker to the map
     */
    private static class PlaceUserMarkerTask extends AsyncTask<Void, Void, Integer> {
        /**
         * Reference to the context.
         */
        private final WeakReference<MapActivity> context;
        /**
         * user to put on the map
         */
		private final JSONObject user;

        /**
         * Constructor.
         * @param context reference to the context
         * @param user user to put on the map
         */
        PlaceUserMarkerTask(final MapActivity context, final JSONObject user) {
            this.context = new WeakReference<>(context);
			this.user = user;
        }

        /**
         * Check if a user hash exists in the database.
         * @param voids nothing
         * @return 1 if the user exists in the dabase, 0 otherwise.
         */
        @Override
        protected Integer doInBackground(Void... voids) {
            try {
                final String hash = user.getString("hash");
                return UserDatabase.getInstance(context.get()).getUserDao().checkUserExists(hash);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
		
		/**
         * Place a marker on the map for a user with a specific color
         * @param result 1 if the user exists and should be yellow, 0 otherwise and should be red.
         */
		@Override
        protected void onPostExecute(Integer result) {
		    if (result != null) {
                try {
                    final double lat = user.getDouble("lat");
                    final double lng = user.getDouble("lng");
                    final MarkerOptions options = new MarkerOptions();
                    options.position(new LatLng(lat, lng));
                    if (result == 0) {
                        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    } else {
                        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                    }
                    this.context.get().userMarkers.add(context.get().gMap.addMarker(options));

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
