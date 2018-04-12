package ca.polymtl.inf8405.tp2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
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
        final byte[] bytes = bitmapToBytes(avatar);
        hash = hash(bytes);
        new AddUsersTask(this).execute(new User(hash, bytes));
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
     * Asynchronously adds users to the database.
     */
    private static class AddUsersTask extends AsyncTask<User, Void, Void> {
        private final WeakReference<LoginActivity> context;

        AddUsersTask(final LoginActivity context) {
            this.context = new WeakReference<>(context);
        }

        /**
         * Adds a user to the database.
         * @param users Users to add.
         * @return Nothing.
         */
        @Override
        protected Void doInBackground(User... users) {
            UserDatabase.getInstance(context.get()).getUserDao().insert(users);
            return null;
        }

        /**
         * After the user has been added, go to the MapActivity.
         * @param result Not used.
         */
        @Override
        protected void onPostExecute(Void result) {
            Intent intent = new Intent(context.get(), MapActivity.class);
            intent.putExtra("hash", context.get().hash);
            context.get().startActivity(intent);
        }
    }
}
