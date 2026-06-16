package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class DoctorLoginActivity extends AppCompatActivity {

    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_login);

        db = AppDatabase.getInstance(this);
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

            new Thread(() -> {
                try {
                    User user = SupabaseAuthHelper.signInBlocking(email, password);
                    
                    if ("doctor".equals(user.role)) {
                        if (user.isApproved) {
                            // Update local DB
                            db.appDao().insertUser(user);
                            
                            sessionManager.saveCredentials(email, password);
                            sessionManager.createLoginSession(user.uid, user.role);

                            runOnUiThread(() -> {
                                Intent intent = new Intent(DoctorLoginActivity.this, DoctorDashboardActivity.class);
                                intent.putExtra("USER_ID", user.uid);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> {
                                loginButton.setEnabled(true);
                                progressBar.setVisibility(android.view.View.GONE);
                                Toast.makeText(this, "Account pending admin approval", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            loginButton.setEnabled(true);
                            progressBar.setVisibility(android.view.View.GONE);
                            Toast.makeText(this, "This is not a doctor account", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        loginButton.setEnabled(true);
                        progressBar.setVisibility(android.view.View.GONE);
                        Toast.makeText(this, "Login Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });

        findViewById(R.id.register_link).setOnClickListener(v -> {
            startActivity(new Intent(DoctorLoginActivity.this, RegisterActivity.class));
        });

        findViewById(R.id.forgot_password_text).setOnClickListener(v -> {
            startActivity(new Intent(DoctorLoginActivity.this, ForgotPasswordActivity.class));
        });
    }
}