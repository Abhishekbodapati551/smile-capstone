package com.example.smileapp;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;

public class DoctorProfileActivity extends AppCompatActivity {

    private AppDatabase db;
    private String userUid;
    private User doctor;

    private TextView profileName, profileSpecialization, doctorIdText;
    private EditText clinicNameEdit, emailEdit, doctorIdEdit;
    private MaterialButton saveButton;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        db = AppDatabase.getInstance(this);
        userUid = getIntent().getStringExtra("USER_ID");

        profileName = findViewById(R.id.profile_name);
        profileSpecialization = findViewById(R.id.profile_specialization);
        doctorIdText = findViewById(R.id.doctor_id_text);
        clinicNameEdit = findViewById(R.id.clinic_name_edit);
        emailEdit = findViewById(R.id.email_edit);
        doctorIdEdit = findViewById(R.id.doctor_id_edit);
        saveButton = findViewById(R.id.save_profile_button);
        backButton = findViewById(R.id.back_button);

        loadDoctorData();

        backButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void loadDoctorData() {
        if (userUid == null) return;
        new Thread(() -> {
            doctor = db.appDao().getUserById(userUid);
            if (doctor != null) {
                runOnUiThread(() -> {
                    profileName.setText("Dr. " + doctor.name);
                    profileSpecialization.setText(doctor.specialization != null ? doctor.specialization : "Specialist");
                    if (doctorIdText != null) {
                        doctorIdText.setText("Current ID: " + (doctor.doctorId != null ? doctor.doctorId : "Not Set"));
                    }
                    if (doctorIdEdit != null) {
                        doctorIdEdit.setText(doctor.doctorId != null ? doctor.doctorId : "");
                    }
                    clinicNameEdit.setText(doctor.clinicName != null ? doctor.clinicName : "");
                    emailEdit.setText(doctor.email);
                });
            }
        }).start();
    }

    private void saveProfile() {
        if (doctor == null) return;

        String newClinicName = clinicNameEdit.getText().toString().trim();
        String newDocId = doctorIdEdit.getText().toString().trim();

        if (newClinicName.isEmpty()) {
            Toast.makeText(this, "Clinic name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newDocId.length() != 4) {
            Toast.makeText(this, "Doctor ID must be 4 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            // 1. Update Supabase
            boolean success = SupabaseAuthHelper.updateDoctorProfileBlocking(doctor.uid, newDocId, newClinicName);
            
            if (success) {
                // 2. Update Local DB
                doctor.clinicName = newClinicName;
                doctor.doctorId = newDocId;
                db.appDao().updateUser(doctor);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Profile and ID updated successfully!", Toast.LENGTH_LONG).show();
                    loadDoctorData(); // Refresh UI
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Update failed. Check your connection.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
