package ca.polymtl.inf8405.tp2;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Asks the user to take a picture before starting the "game".
 */
public class LoginActivity extends AppCompatActivity {

    private Bitmap avatar;
    private String hash;
    private byte[] bytes;
    private Float originalBatteryLevel = null;
    private Float currentBatteryLevel = null;
    private long originalReceivedBytes = 0;
    private long currentReceivedBytes = 0;
    private long originalTransmittedBytes = 0;
    private long currentTransmittedBytes = 0;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manageBattery();
        originalReceivedBytes = TrafficStats.getTotalRxBytes();
        originalTransmittedBytes = TrafficStats.getTotalTxBytes();
        currentReceivedBytes = originalReceivedBytes;
        currentTransmittedBytes = originalTransmittedBytes;
        handler.postDelayed(new NetworkInfoManager(), 1000);
        new CheckUserExistsTask(this).execute();
        setContentView(R.layout.activity_login);
    }

    private class NetworkInfoManager implements Runnable {
        public void run() {
            currentReceivedBytes = TrafficStats.getTotalRxBytes();
            currentTransmittedBytes = TrafficStats.getTotalTxBytes();
            updateTitle();
            handler.postDelayed(new NetworkInfoManager(), 1000);
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateTitle() {
        setTitle(String.format("Pile %.0f%%, %s down, %s up",
                100*(originalBatteryLevel - currentBatteryLevel),
                Formatter.formatShortFileSize(this, currentReceivedBytes - originalReceivedBytes),
                Formatter.formatShortFileSize(this, currentTransmittedBytes - originalTransmittedBytes)));
    }

    private void manageBattery() {
        registerReceiver(new BroadcastReceiver() {
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
     * Called when the user taps on the take picture button.
     * Starts taking a picture.
     * @param view Unused.
     */
    public void takePicture(View view) {
        findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
        startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0);
    }

    /**
     * Called when the user has taken a picture.
     * Displays the picture.
     * @param requestCode ID of the request. Should be 0.
     * @param resultCode Result code.
     * @param intent Contains the data of the picture.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                avatar = (Bitmap) extras.get("data");
                ((ImageView) findViewById(R.id.avatarImageView)).setImageBitmap(avatar);
                findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Called when the user confirms his avatar.
     * A new user is created with the hash and the bytes of the chosen avatar.
     * @param view Not used.
     */
    public void confirm(View view) {
        bytes = bitmapToBytes(avatar);
        hash = hash(bytes);
        new AddUsersTask(this).execute(new User(hash, bytes, 1));
    }

    /**
     * Converts a bitmap into its representation in bytes.
     * @param bitmap Bitmap.
     * @return Representation in bytes of the bitmap.
     */
    private static byte[] bitmapToBytes(final Bitmap bitmap) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
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
     * Asynchronously checks if a user is already in the database.
     */
    private static class CheckUserExistsTask extends AsyncTask<Void, Void, User> {
        private final WeakReference<LoginActivity> context;

        CheckUserExistsTask(final LoginActivity context) {
            this.context = new WeakReference<>(context);
        }

        /**
         * Checks if there is a user in the database.
         * @param voids Users to add.
         * @return Nothing.
         */
        @Override
        protected User doInBackground(Void... voids) {
            return UserDatabase.getInstance(context.get()).getUserDao().getSelf();
        }

        /**
         * After the users have been added, go to the MapActivity if there's already a database entry.
         * @param user User found from the local database.
         */
        @Override
        protected void onPostExecute(User user) {
            if (user == null) {
                return;
            }
            Intent intent = new Intent(context.get(), MapActivity.class);
            intent.putExtra("hash", user.getHash());
            intent.putExtra("bytes", user.getImage());
            intent.putExtra("originalBattery", context.get().originalBatteryLevel);
            intent.putExtra("currentBattery", context.get().currentBatteryLevel);
            intent.putExtra("originalReceived", context.get().originalReceivedBytes);
            intent.putExtra("currentReceived", context.get().currentReceivedBytes);
            intent.putExtra("originalTransmitted", context.get().originalTransmittedBytes);
            intent.putExtra("currentTransmitted", context.get().currentTransmittedBytes);
            context.get().startActivity(intent);
        }
    }

    /**
     * Asynchronously adds users to the database.
     */
    private static class AddUsersTask extends AsyncTask<User, Void, Void> {
        private final WeakReference<LoginActivity> context;

        AddUsersTask(final LoginActivity context) {
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

        /**
         * After the users have been added, go to the MapActivity.
         * @param result Not used.
         */
        @Override
        protected void onPostExecute(Void result) {
            Intent intent = new Intent(context.get(), MapActivity.class);
            intent.putExtra("hash", context.get().hash);
            intent.putExtra("bytes", context.get().bytes);
            intent.putExtra("originalBattery", context.get().originalBatteryLevel);
            intent.putExtra("currentBattery", context.get().currentBatteryLevel);
            intent.putExtra("originalReceived", context.get().originalReceivedBytes);
            intent.putExtra("currentReceived", context.get().currentReceivedBytes);
            intent.putExtra("originalTransmitted", context.get().originalTransmittedBytes);
            intent.putExtra("currentTransmitted", context.get().currentTransmittedBytes);
            context.get().startActivity(intent);
        }
    }
}
