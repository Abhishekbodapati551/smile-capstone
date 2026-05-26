package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.example.smileapp.models.Appointment;
import com.google.android.material.card.MaterialCardView;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class ChildDashboardActivity extends AppCompatActivity {

    private AppDatabase db;
    private String userId;
    private TextView welcomeText;
    private FirebaseFirestore dbFirestore;
    private ListenerRegistration appointmentListener;
    private MaterialCardView apptNotificationCard;
    private TextView apptDetailsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        db = AppDatabase.getInstance(this);
        dbFirestore = FirebaseFirestore.getInstance();
        userId = getIntent().getStringExtra("USER_ID");
        
        if (userId == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        welcomeText = findViewById(R.id.welcome_user);
        
        apptNotificationCard = findViewById(R.id.appointment_notification_card);
        apptDetailsText = findViewById(R.id.appt_notification_details);
        ImageButton closeNotificationBtn = findViewById(R.id.close_notification_btn);

        if (closeNotificationBtn != null) {
            closeNotificationBtn.setOnClickListener(v -> apptNotificationCard.setVisibility(View.GONE));
        }

        loadUserData();
        startAppointmentListener();

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

    private void startAppointmentListener() {
        if (userId == null) return;

        // Listen for the most recent appointment for this child
        appointmentListener = dbFirestore.collection("appointments")
            .whereEqualTo("childId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener((value, error) -> {
                if (error != null) return;
                if (value != null && !value.isEmpty()) {
                    Appointment app = value.getDocuments().get(0).toObject(Appointment.class);
                    if (app != null && app.date > System.currentTimeMillis()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd 'at' hh:mm a", Locale.getDefault());
                        String formattedDate = sdf.format(new Date(app.date));
                        
                        runOnUiThread(() -> {
                            apptDetailsText.setText(app.type + ": " + formattedDate);
                            apptNotificationCard.setVisibility(View.VISIBLE);
                        });
                        
                        // Also update local DB
                        new Thread(() -> {
                            // Check if already exists locally to avoid duplicates if necessary
                            db.appDao().insertAppointment(app);
                        }).start();
                    }
                }
            });
    }

    @Override
    protected void onDestroy() {
        if (appointmentListener != null) {
            appointmentListener.remove();
        }
        super.onDestroy();
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