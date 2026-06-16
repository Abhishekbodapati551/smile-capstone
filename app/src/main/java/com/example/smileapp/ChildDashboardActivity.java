package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChildDashboardActivity extends AppCompatActivity {

    private AppDatabase db;
    private String userId;
    private TextView welcomeText, streakText, taskDescriptionText, taskStatusText;
    private MaterialCardView apptNotificationCard, feedbackCard;
    private TextView apptDetailsText, feedbackText;
    private ProgressBar taskProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        db = AppDatabase.getInstance(this);
        userId = getIntent().getStringExtra("USER_ID");
        
        welcomeText = findViewById(R.id.welcome_user);
        streakText = findViewById(R.id.streak_val);
        taskProgressBar = findViewById(R.id.task_progress);
        taskDescriptionText = findViewById(R.id.task_description);
        taskStatusText = findViewById(R.id.task_status);
        
        apptNotificationCard = findViewById(R.id.appointment_notification_card);
        apptDetailsText = findViewById(R.id.appt_notification_details);
        ImageButton closeNotificationBtn = findViewById(R.id.close_notification_btn);

        feedbackCard = findViewById(R.id.doctor_feedback_card);
        feedbackText = findViewById(R.id.doctor_feedback_text);
        ImageButton closeFeedbackBtn = findViewById(R.id.close_feedback_btn);

        if (closeNotificationBtn != null) {
            closeNotificationBtn.setOnClickListener(v -> apptNotificationCard.setVisibility(View.GONE));
        }

        if (closeFeedbackBtn != null) {
            closeFeedbackBtn.setOnClickListener(v -> feedbackCard.setVisibility(View.GONE));
        }

        loadUserData();
        startSupabaseListeners();

        findViewById(R.id.btn_brush_timer).setOnClickListener(v -> {
            Intent intent = new Intent(this, BrushingTipsActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

        findViewById(R.id.btn_rewards).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChildRewardsActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

        findViewById(R.id.logout_btn).setOnClickListener(v -> {
            new SessionManager(this).logoutUser();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void startSupabaseListeners() {
        if (userId == null) return;
        SupabaseAuthHelper.listenForAppointments(userId, appt -> {
            if (appt.date > System.currentTimeMillis()) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd 'at' hh:mm a", Locale.getDefault());
                String formattedDate = sdf.format(new Date(appt.date));
                runOnUiThread(() -> {
                    apptDetailsText.setText(appt.type + ": " + formattedDate);
                    apptNotificationCard.setVisibility(View.VISIBLE);
                });
                new Thread(() -> db.appDao().insertAppointment(appt)).start();
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private void loadUserData() {
        if (userId == null) return;
        new Thread(() -> {
            // 1. Show local data INSTANTLY
            User user = db.appDao().getUserById(userId);
            if (user != null) {
                runOnUiThread(() -> updateChildUI(user));
            }

            // 2. Sync in background
            try {
                SessionManager sm = new SessionManager(this);
                if (sm.getSavedEmail() != null && sm.getSavedPassword() != null) {
                    User freshUser = SupabaseAuthHelper.signInBlocking(sm.getSavedEmail(), sm.getSavedPassword());
                    if (freshUser != null) {
                        db.appDao().insertUser(freshUser);
                        runOnUiThread(() -> updateChildUI(freshUser));
                    }
                }
            } catch (Exception e) {
                Log.e("ChildDashboard", "Sync failed", e);
            }
            
            refreshDailyProgress();
        }).start();
    }

    private void updateChildUI(User user) {
        welcomeText.setText("Hi, " + user.name + "!");
        if (streakText != null) {
            streakText.setText(user.streak + " Days");
        }
    }

    private void refreshDailyProgress() {
        // Get today's start and end timestamps
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfDay = cal.getTimeInMillis();

        List<BrushingLog> logsToday = db.appDao().getApprovedBrushingLogsForChildToday(userId, startOfDay, endOfDay);
        int sessionsDone = logsToday.size();

        runOnUiThread(() -> {
            // Update Task Progress
            if (taskProgressBar != null) {
                taskProgressBar.setMax(2);
                taskProgressBar.setProgress(sessionsDone);
            }

            if (taskDescriptionText != null) {
                taskDescriptionText.setText("Brush twice daily (2 mins each)");
            }

            if (taskStatusText != null) {
                if (sessionsDone == 0) {
                    taskStatusText.setText("0/2 sessions completed");
                } else if (sessionsDone == 1) {
                    taskStatusText.setText("1/2 sessions completed (50%)");
                } else {
                    taskStatusText.setText("All tasks done! (100%)");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
    }
}
