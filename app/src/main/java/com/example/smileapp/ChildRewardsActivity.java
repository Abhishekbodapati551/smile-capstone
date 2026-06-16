package com.example.smileapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import java.util.Random;

public class ChildRewardsActivity extends AppCompatActivity {

    private AppDatabase db;
    private String userId;
    private TextView pointsText;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_rewards);

        db = AppDatabase.getInstance(this);
        userId = getIntent().getStringExtra("USER_ID");
        pointsText = findViewById(R.id.points_val);

        setupRewardButtons();
        loadPoints();
    }

    private void setupRewardButtons() {
        findViewById(R.id.btn_buy_star).setOnClickListener(v -> handlePurchase("Star Badge", 100));
        findViewById(R.id.btn_buy_pens).setOnClickListener(v -> handlePurchase("Pens Set", 200));
        findViewById(R.id.btn_buy_teddy).setOnClickListener(v -> handlePurchase("Teddy Bear", 300));
        findViewById(R.id.btn_buy_icecream).setOnClickListener(v -> handlePurchase("Ice Cream Kit", 500));
    }

    private void loadPoints() {
        if (userId == null) return;
        new Thread(() -> {
            try {
                SessionManager sm = new SessionManager(this);
                User freshUser = SupabaseAuthHelper.signInBlocking(sm.getSavedEmail(), sm.getSavedPassword());
                if (freshUser != null) {
                    db.appDao().insertUser(freshUser);
                    currentUser = freshUser;
                }
            } catch (Exception e) {
                Log.e("RewardsStore", "Sync failed: " + e.getMessage());
                currentUser = db.appDao().getUserById(userId);
            }

            runOnUiThread(() -> {
                if (currentUser != null && pointsText != null) {
                    pointsText.setText("⭐ " + currentUser.points);
                }
            });
        }).start();
    }

    private void handlePurchase(String itemName, int cost) {
        if (currentUser == null) return;

        if (currentUser.points < cost) {
            Toast.makeText(this, "Not enough points! Keep brushing to earn more.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Confirm Collection")
            .setMessage("Use " + cost + " points to collect " + itemName + "?")
            .setPositiveButton("Yes", (dialog, which) -> processRedemption(itemName, cost))
            .setNegativeButton("No", null)
            .show();
    }

    private void processRedemption(String itemName, int cost) {
        new Thread(() -> {
            int newPoints = currentUser.points - cost;
            boolean success = SupabaseAuthHelper.removePointsBlocking(currentUser.uid, newPoints);
            
            if (success) {
                // Update Local DB
                db.appDao().removePoints(currentUser.uid, cost);
                currentUser.points = newPoints;

                runOnUiThread(() -> {
                    pointsText.setText("⭐ " + newPoints);
                    showSuccessDialog(itemName);
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Transaction failed. Try again.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showSuccessDialog(String itemName) {
        String redemptionCode = generateRandomCode();
        new AlertDialog.Builder(this)
            .setTitle("Success! 🎉")
            .setMessage("You collected: " + itemName + "\n\nShow this code to your doctor to collect your reward:\n\nCODE: " + redemptionCode)
            .setPositiveButton("Got it!", null)
            .setCancelable(false)
            .show();
    }

    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random rnd = new Random();
        while (code.length() < 6) {
            code.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return code.toString();
    }
}
