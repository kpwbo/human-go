package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

/**
 * Represents a user of the application.
 */
@Entity
public class User {
    /**
     * Hash of the avatar of the user.
     */
    @PrimaryKey @NonNull
    private final String hash;
    /**
     * Bytes of the avatar of the user.
     */
    private final byte[] image;
    /**
     * 1 if the user is the owner of this instance of the application. 0 otherwise.
     */
    private final int isSelf;

    /**
     * Constructor.
     * @param hash Hash of the avatar.
     * @param image Bytes of the avatar.
     * @param isSelf 1 if the user is the owner. 0 otherwise.
     */
    User(@NonNull String hash, byte[] image, int isSelf) {
        this.hash = hash;
        this.image = image;
        this.isSelf = isSelf;
    }

    /**
     * Return the hash of the user.
     * @return hash of the user.
     */
    @NonNull
    String getHash() {
        return hash;
    }

    /**
     * Return the bytes of the user.
     * @return bytes of the user.
     */
    byte[] getImage() {
        return image;
    }

    /**
     * Return 1 if the user is the owner. 0 otherwise.
     * @return 1 if the user is the owner. 0 otherwise.
     */
    int getIsSelf() {
        return isSelf;
    }
}
