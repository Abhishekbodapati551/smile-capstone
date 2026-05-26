package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class DoctorLoginActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_login);

        db = AppDatabase.getInstance(this);
        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        TextInputEditText emailEdit = findViewById(R.id.email_edit_text);
        TextInputEditText passwordEdit = findViewById(R.id.password_edit_text);
        MaterialButton loginButton = findViewById(R.id.login_button);
        android.widget.ProgressBar progressBar = findViewById(R.id.login_progress);

        // Autofill saved credentials
        emailEdit.setText(sessionManager.getSavedEmail());
        passwordEdit.setText(sessionManager.getSavedPassword());

        loginButton.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress and disable button
            loginButton.setEnabled(false);
            progressBar.setVisibility(android.view.View.VISIBLE);

            mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();
                        
                        // Save credentials for next time
                        sessionManager.saveCredentials(email, password);

                        // Look up doctor by email or UID to find their sequential ID
                        dbFirestore.collection("users")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (!querySnapshot.isEmpty()) {
                                    DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
                                    handleDoctorLoginSuccess(documentSnapshot, uid, email, password);
                                } else {
                                    // Fallback: try to fetch document by UID directly
                                    dbFirestore.collection("users")
                                        .whereEqualTo("uid", uid)
                                        .get()
                                        .addOnSuccessListener(uidSnapshot -> {
                                            if (!uidSnapshot.isEmpty()) {
                                                handleDoctorLoginSuccess(uidSnapshot.getDocuments().get(0), uid, email, password);
                                            } else {
                                                mAuth.signOut();
                                                loginButton.setEnabled(true);
                                                progressBar.setVisibility(android.view.View.GONE);
                                                Toast.makeText(this, "Doctor data not found in Firestore. Please register again.", Toast.LENGTH_LONG).show();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            mAuth.signOut();
                                            loginButton.setEnabled(true);
                                            progressBar.setVisibility(android.view.View.GONE);
                                            Toast.makeText(this, "Firestore Error (UID Lookup): " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });
                                }
                            })
                            .addOnFailureListener(e -> {
                                mAuth.signOut();
                                loginButton.setEnabled(true);
                                progressBar.setVisibility(android.view.View.GONE);
                                Toast.makeText(this, "Firestore Error (Email Lookup): " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                    } else {
                        loginButton.setEnabled(true);
                        progressBar.setVisibility(android.view.View.GONE);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown Auth Error";
                        Toast.makeText(this, "Login Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                        android.util.Log.e("DoctorLogin", "Auth Error", task.getException());
                    }
                });
        });

        findViewById(R.id.register_link).setOnClickListener(v -> {
            startActivity(new Intent(DoctorLoginActivity.this, RegisterActivity.class));
        });

        findViewById(R.id.forgot_password_text).setOnClickListener(v -> {
            startActivity(new Intent(DoctorLoginActivity.this, ForgotPasswordActivity.class));
        });
    }

    private void handleDoctorLoginSuccess(DocumentSnapshot documentSnapshot, String uid, String email, String password) {
        String role = documentSnapshot.getString("role");
        Boolean isApproved = documentSnapshot.getBoolean("isApproved");

        if ("doctor".equals(role)) {
            if (isApproved != null && isApproved) {
                String sequentialId = documentSnapshot.getString("doctorId");
                // Update local DB
                new Thread(() -> {
                    User existingUser = db.appDao().getUserById(uid);
                    if (existingUser == null) {
                        User user = new User(uid, documentSnapshot.getString("name"), email, password, role);
                        user.doctorId = sequentialId;
                        user.isApproved = true;
                        db.appDao().insertUser(user);
                    } else {
                        existingUser.name = documentSnapshot.getString("name");
                        existingUser.email = email;
                        existingUser.password = password;
                        existingUser.doctorId = sequentialId;
                        existingUser.isApproved = true;
                        db.appDao().updateUser(existingUser);
                    }
                    
                    runOnUiThread(() -> {
                        Intent intent = new Intent(DoctorLoginActivity.this, DoctorDashboardActivity.class);
                        intent.putExtra("USER_ID", uid);
                        startActivity(intent);
                        finish();
                    });
                }).start();
            } else {
                mAuth.signOut();
                findViewById(R.id.login_button).setEnabled(true);
                findViewById(R.id.login_progress).setVisibility(android.view.View.GONE);
                Toast.makeText(this, "Account pending admin approval", Toast.LENGTH_LONG).show();
            }
        } else {
            mAuth.signOut();
            findViewById(R.id.login_button).setEnabled(true);
            findViewById(R.id.login_progress).setVisibility(android.view.View.GONE);
            Toast.makeText(this, "This account is not a doctor account", Toast.LENGTH_SHORT).show();
        }
    }
}