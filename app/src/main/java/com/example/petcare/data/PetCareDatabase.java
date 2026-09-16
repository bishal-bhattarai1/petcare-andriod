package com.example.petcare.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {UserEntity.class}, version = 1, exportSchema = false)
public abstract class PetCareDatabase extends RoomDatabase {
    public abstract UserDao userDao();

    private static volatile PetCareDatabase INSTANCE;

    public static PetCareDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (PetCareDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    PetCareDatabase.class, "petcare_database")
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}