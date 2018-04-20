package ca.polymtl.inf8405.tp2;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

/**
 * Represents the user database. Singleton.
 */
@Database(entities = { User.class }, version = 1, exportSchema = false)
public abstract class UserDatabase extends RoomDatabase {
    /**
     * Returns the user DAO.
     * @return user DAO.
     */
    public abstract UserDao getUserDao();

    /**
     * Instance of the database.
     */
    private static volatile UserDatabase instance;

    /**
     * Return the instance of the database. Creates it first if it doesn't exist.
     * @param context context
     * @return instance of the database
     */
    static synchronized  UserDatabase getInstance(Context context) {
        if (instance == null) {
            instance = create(context);
        }
        return instance;
    }

    /**
     * Create the database.
     * @param context context
     * @return new instance of the database
     */
    private static UserDatabase create(final Context context) {
        return Room.databaseBuilder(
                context,
                UserDatabase.class,
                "userDatabase.db"
        ).build();
    }
}
