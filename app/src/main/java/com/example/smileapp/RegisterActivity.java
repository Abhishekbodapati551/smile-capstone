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
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private AppDatabase db;
    private String selectedRole = "child";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);

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
            v.setEnabled(false);
            String name = nameEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();
            String doctorId = doctorIdEdit.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
                return;
            }

            if (selectedRole.equals("child") && doctorId.isEmpty()) {
                Toast.makeText(this, "Doctor ID is required for patients!", Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
                return;
            }

            Toast.makeText(this, "Starting registration...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                try {
                    // 1. If child, validate doctorId (using postgrest)
                    String finalDoctorId = selectedRole.equals("child") ? doctorId : null;
                    
                    // Supabase doesn't have transactions for ID counters the same way as Firebase, 
                    // but we can handle it with an edge function or just simple logic for now.
                    // For the "1176" logic, we'll keep it simple for this migration.
                    
                    User registeredUser = SupabaseAuthHelper.signUpBlocking(name, email, password, selectedRole, finalDoctorId);
                    
                    // 2. Save locally
                    db.appDao().insertUser(registeredUser);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Success! Please go to the Login screen and enter your details.", Toast.LENGTH_LONG).show();
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Registration Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        v.setEnabled(true);
                    });
                }
            }).start();
        });

        findViewById(R.id.login_link).setOnClickListener(v -> finish());
    }
}
