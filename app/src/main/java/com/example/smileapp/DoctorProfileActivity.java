package com.example.smileapp;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;

public class DoctorProfileActivity extends AppCompatActivity {

    private AppDatabase db;
    private String doctorId;
    private User doctor;

    private TextView profileName, profileSpecialization, doctorIdText;
    private EditText clinicNameEdit, emailEdit;
    private MaterialButton saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        db = AppDatabase.getInstance(this);
        doctorId = getIntent().getStringExtra("USER_ID");

        profileName = findViewById(R.id.profile_name);
        profileSpecialization = findViewById(R.id.profile_specialization);
        doctorIdText = findViewById(R.id.doctor_id_text);
        clinicNameEdit = findViewById(R.id.clinic_name_edit);
        emailEdit = findViewById(R.id.email_edit);
        saveButton = findViewById(R.id.save_profile_button);

        loadDoctorData();

        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void loadDoctorData() {
        if (doctorId == null) return;
        new Thread(() -> {
            doctor = db.appDao().getUserById(doctorId);
            if (doctor != null) {
                runOnUiThread(() -> {
                    profileName.setText("Dr. " + doctor.name);
                    profileSpecialization.setText(doctor.specialization != null ? doctor.specialization : "Specialist");
                    if (doctorIdText != null) {
                        doctorIdText.setText("Your Doctor ID: " + doctorId);
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
        if (newClinicName.isEmpty()) {
            Toast.makeText(this, "Clinic name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        doctor.clinicName = newClinicName;
        new Thread(() -> {
            db.appDao().updateUser(doctor);
            runOnUiThread(() -> {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}
