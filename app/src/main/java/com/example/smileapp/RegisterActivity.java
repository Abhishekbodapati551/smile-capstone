package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private String selectedRole = "child";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);
        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();

        MaterialButtonToggleGroup roleToggle = findViewById(R.id.role_toggle_group);
        TextInputLayout doctorIdLayout = findViewById(R.id.doctor_id_layout);
        TextInputEditText nameEdit = findViewById(R.id.name_edit_text);
        TextInputEditText emailEdit = findViewById(R.id.email_edit_text);
        TextInputEditText passwordEdit = findViewById(R.id.password_edit_text);
        TextInputEditText doctorIdEdit = findViewById(R.id.doctor_id_edit_text);

        roleToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_child_role) {
                    selectedRole = "child";
                    doctorIdLayout.setVisibility(View.VISIBLE);
                } else {
                    selectedRole = "doctor";
                    doctorIdLayout.setVisibility(View.GONE);
                }
            }
        });

        findViewById(R.id.register_button).setOnClickListener(v -> {
            v.setEnabled(false); // Prevent double clicks
            String name = nameEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();
            String doctorId = doctorIdEdit.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
                return;
            }

            Toast.makeText(this, "Starting registration...", Toast.LENGTH_SHORT).show();

            if (selectedRole.equals("child")) {
                // ... same child logic ...
                String finalDoctorId = doctorId.trim();
                dbFirestore.collection("users")
                    .whereEqualTo("doctorId", finalDoctorId)
                    .whereEqualTo("role", "doctor")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            performRegistration(name, email, password, finalDoctorId);
                        } else {
                            Toast.makeText(this, "Doctor ID " + finalDoctorId + " not found.", Toast.LENGTH_LONG).show();
                            v.setEnabled(true);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Connection Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        v.setEnabled(true);
                    });
            } else {
                performRegistration(name, email, password, null);
            }
        });

        findViewById(R.id.login_link).setOnClickListener(v -> finish());
    }

    private void performRegistration(String name, String email, String password, String doctorId) {
        if (selectedRole.equals("doctor")) {
            // Sequential ID logic for Doctors
            dbFirestore.runTransaction(transaction -> {
                com.google.firebase.firestore.DocumentReference counterRef = dbFirestore.collection("counters").document("doctor_id");
                com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(counterRef);
                
                long nextId = 1176; // Start new registrations from 1176
                if (snapshot.exists() && snapshot.contains("last_id")) {
                    Long currentId = snapshot.getLong("last_id");
                    if (currentId != null) {
                        nextId = currentId + 1;
                    }
                }
                
                transaction.set(counterRef, java.util.Collections.singletonMap("last_id", nextId));
                return String.valueOf(nextId);
            }).addOnSuccessListener(sequentialId -> {
                createAuthUser(name, email, password, sequentialId);
            }).addOnFailureListener(e -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Database ID Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Log the error for better debugging
                    android.util.Log.e("RegisterActivity", "Transaction failed", e);
                });
            });
        } else {
            // Children use their Firebase UID, but linked to a doctor's sequential ID
            createAuthUser(name, email, password, doctorId);
        }
    }

    private void createAuthUser(String name, String email, String password, String doctorIdOrSequentialId) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    String uid = mAuth.getCurrentUser().getUid();
                    String finalDoctorIdForRecord = doctorIdOrSequentialId;

                    // Create user object for Firestore
                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("name", name);
                    user.put("email", email);
                    user.put("role", selectedRole);
                    user.put("isApproved", false); // Admin must approve in console
                    user.put("points", 0);
                    user.put("streak", 0);
                    user.put("stars", 0);
                    user.put("doctorId", finalDoctorIdForRecord);

                    // For Doctors, we use their Sequential ID as the Document ID
                    String documentId = selectedRole.equals("doctor") ? finalDoctorIdForRecord : uid;

                    dbFirestore.collection("users").document(documentId)
                        .set(user)
                        .addOnSuccessListener(aVoid -> {
                            // Sync to local DB
                            new Thread(() -> {
                                User localUser = new User(uid, name, email, password, selectedRole);
                                localUser.doctorId = finalDoctorIdForRecord;
                                localUser.isApproved = false;
                                db.appDao().insertUser(localUser);
                                
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Registration Success! Waiting for Admin approval.", Toast.LENGTH_LONG).show();
                                    finish();
                                });
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Firestore Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                findViewById(R.id.register_button).setEnabled(true);
                            });
                        });
                } else {
                    runOnUiThread(() -> {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown Auth Error";
                        Toast.makeText(this, "Auth Failed: " + error, Toast.LENGTH_LONG).show();
                        findViewById(R.id.register_button).setEnabled(true);
                    });
                }
            });
    }
}
