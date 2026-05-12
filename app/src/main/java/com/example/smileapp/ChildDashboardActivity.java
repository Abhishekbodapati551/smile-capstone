package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.card.MaterialCardView;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Random;

public class ChildDashboardActivity extends AppCompatActivity {

    private AppDatabase db;
    private String userId;
    private TextView welcomeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        db = AppDatabase.getInstance(this);
        userId = getIntent().getStringExtra("USER_ID");
        welcomeText = findViewById(R.id.welcome_user);

        loadUserData();

        android.view.View appointmentsBtn = findViewById(R.id.btn_appointments);
        appointmentsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ChildDashboardActivity.this, ChildAppointmentsActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

        MaterialCardView brushBtn = findViewById(R.id.btn_brush_timer);
        brushBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ChildDashboardActivity.this, BrushingTipsActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

        TextView rewardsBtn = findViewById(R.id.btn_rewards);
        rewardsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ChildDashboardActivity.this, ChildRewardsActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

        findViewById(R.id.logout_btn).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.daily_quote_btn).setOnClickListener(v -> showDailyTip());
    }

    private void showDailyTip() {
        String[] tips = {
            "Brush for at least 2 minutes twice a day!",
            "Don't forget to brush your tongue for fresh breath!",
            "Flossing helps remove food stuck between your teeth.",
            "Drink plenty of water to keep your mouth clean!",
            "Eating apples and carrots can help clean your teeth naturally.",
            "Replace your toothbrush every 3 months!",
            "Visit your dentist twice a year for a bright smile!"
        };
        
        String randomTip = tips[new Random().nextInt(tips.length)];
        
        new AlertDialog.Builder(this)
            .setTitle("🦷 Daily Dental Tip")
            .setMessage(randomTip)
            .setPositiveButton("Got it!", null)
            .show();
    }

    private void loadUserData() {
        if (userId == null) return;
        new Thread(() -> {
            User user = db.appDao().getUserById(userId);
            if (user != null) {
                runOnUiThread(() -> {
                    welcomeText.setText(getString(R.string.dashboard_welcome, user.name));
                });
            }
        }).start();
    }
}