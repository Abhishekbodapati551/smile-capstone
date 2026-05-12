package com.example.smileapp.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "appointments")
public class Appointment {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String childId;
    public String childName;
    public String doctorId;
    public long date; // Storing as timestamp for simplicity in Room
    public String type;
    public String status; // "upcoming", "completed"

    public Appointment() {}

    public Appointment(String childId, String childName, String doctorId, long date, String type) {
        this.childId = childId;
        this.childName = childName;
        this.doctorId = doctorId;
        this.date = date;
        this.type = type;
        this.status = "upcoming";
    }
}
