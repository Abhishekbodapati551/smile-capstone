package com.example.smileapp.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "brushing_logs")
public class BrushingLog {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String childId;
    public String videoUri; // Storing local URI
    public long timestamp;
    public boolean approved;
    public String firestoreId;
    public String doctorFeedback;

    public BrushingLog(String childId, String videoUri, long timestamp) {
        this.childId = childId;
        this.videoUri = videoUri;
        this.timestamp = timestamp;
        this.approved = false;
    }
}