package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;

public class ChildLoginActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_login);

        db = AppDatabase.getInstance(this);
        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        TextInputEditText emailEdit = findViewById(R.id.username_edit_text);
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

                        // Verify role and approval in Firestore
                        dbFirestore.collection("users").document(uid).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String role = documentSnapshot.getString("role");
                                    Boolean isApproved = documentSnapshot.getBoolean("isApproved");
                                    
                                    if ("child".equals(role)) {
                                        if (isApproved != null && isApproved) {
                                            // Update local DB
                                            new Thread(() -> {
                                                User existingUser = db.appDao().getUserById(uid);
                                                if (existingUser == null) {
                                                    User user = new User(uid, documentSnapshot.getString("name"), email, password, role);
                                                    user.isApproved = true;
                                                    user.doctorId = documentSnapshot.getString("doctorId");
                                                    db.appDao().insertUser(user);
                                                } else {
                                                    existingUser.name = documentSnapshot.getString("name");
                                                    existingUser.email = email;
                                                    existingUser.password = password;
                                                    existingUser.isApproved = true;
                                                    existingUser.doctorId = documentSnapshot.getString("doctorId");
                                                    db.appDao().updateUser(existingUser);
                                                }
                                                
                                                runOnUiThread(() -> {
                                                    Intent intent = new Intent(ChildLoginActivity.this, ChildDashboardActivity.class);
                                                    intent.putExtra("USER_ID", uid);
                                                    startActivity(intent);
                                                    finish();
                                                });
                                            }).start();
                                        } else {
                                            mAuth.signOut();
                                            loginButton.setEnabled(true);
                                            progressBar.setVisibility(android.view.View.GONE);
                                            Toast.makeText(this, "Account pending doctor approval", Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        mAuth.signOut();
                                        loginButton.setEnabled(true);
                                        progressBar.setVisibility(android.view.View.GONE);
                                        Toast.makeText(this, "This account is not a child account", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    mAuth.signOut();
                                    loginButton.setEnabled(true);
                                    progressBar.setVisibility(android.view.View.GONE);
                                    Toast.makeText(this, "User data not found in Firestore. Please register again.", Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                mAuth.signOut();
                                loginButton.setEnabled(true);
                                progressBar.setVisibility(android.view.View.GONE);
                                Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                    } else {
                        loginButton.setEnabled(true);
                        progressBar.setVisibility(android.view.View.GONE);
                        Toast.makeText(this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        });

        findViewById(R.id.signup_link).setOnClickListener(v -> {
            startActivity(new Intent(ChildLoginActivity.this, RegisterActivity.class));
        });

        findViewById(R.id.forgot_password_text).setOnClickListener(v -> {
            startActivity(new Intent(ChildLoginActivity.this, ForgotPasswordActivity.class));
        });
    }
}