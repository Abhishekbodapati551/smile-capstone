package com.example.smileapp.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String uid;
    public String name;
    public String email;
    public String password; // Added for local auth
    public String role; // "child" or "doctor"
    public String doctorId; // For child: link to doctor
    public boolean isApproved; // Added for doctor approval
    public String clinicName; // For doctors
    public String specialization; // For doctors
    public int points;
    public int streak;
    public int stars;

    public User() {}

    public User(String uid, String name, String email, String password, String role) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.isApproved = "doctor".equals(role); // Doctors are auto-approved, kids need approval
        this.points = 0;
        this.streak = 0;
        this.stars = 0;
    }
}
