package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

/**
 * DAO for the user database.
 */
@Dao
public interface UserDao {
    /**
     * Get the owner of the instance of the application.
     * @return owner.
     */
    @Query("SELECT * FROM user WHERE isSelf = 1")
    User getSelf();

    /**
     * Insert users in the database. Ignore rows with conflicts.
     * @param users Users to add.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(User... users);

    /**
     * Return 1 if the user exists. 0 otherwise.
     * @param hash Hash of the user.
     * @return 1 if the user exists. 0 otherwise.
     */
	@Query("SELECT COUNT(*) FROM user WHERE hash = :hash")
	int checkUserExists(String hash);
}
