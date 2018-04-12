package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity
public class User {
    @PrimaryKey @NonNull
    private final String hash;
    private final byte[] image;
    private final int isSelf;

    User(@NonNull String hash, byte[] image, int isSelf) {
        this.hash = hash;
        this.image = image;
        this.isSelf = isSelf;
    }

    @NonNull
    String getHash() {
        return hash;
    }

    byte[] getImage() {
        return image;
    }

    int getIsSelf() {
        return isSelf;
    }
}
