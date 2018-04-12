package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user WHERE isSelf = 1")
    User getSelf();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(User... users);

    @Query("SELECT COUNT(*) FROM user")
    int getCount();
	
	@Query("SELECT COUNT(*) FROM user WHERE hash = :hash")
	int checkUserExists(String hash);
}
