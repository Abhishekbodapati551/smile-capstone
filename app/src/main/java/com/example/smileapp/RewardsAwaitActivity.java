package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class RewardsAwaitActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards_await);

        String userId = getIntent().getStringExtra("USER_ID");

        MaterialButton startBrushingBtn = findViewById(R.id.btn_start_brushing);
        startBrushingBtn.setOnClickListener(v -> {
            Intent intent = new Intent(RewardsAwaitActivity.this, ChildDashboardActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
            finish();
        });
    }
}
