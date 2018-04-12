package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user WHERE hash = :hash")
    User getUser(String hash);

    @Insert
    void insert(User... users);
}
