package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity
public class User {
    @PrimaryKey @NonNull
    private final String hash;
    private final byte[] image;

    User(@NonNull String hash, byte[] image) {
        this.hash = hash;
        this.image = image;
    }

    @NonNull
    String getHash() {
        return hash;
    }

    byte[] getImage() {
        return image;
    }
}
