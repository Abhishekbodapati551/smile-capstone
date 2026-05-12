package com.example.smileapp.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.smileapp.models.Appointment;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;

@Database(entities = {User.class, Appointment.class, BrushingLog.class}, version = 8)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public abstract AppDao appDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "smile_app_db")
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}
