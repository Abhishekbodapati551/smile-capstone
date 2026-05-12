package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class BrushingTipsActivity extends AppCompatActivity {

    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brushing_tips);

        userId = getIntent().getStringExtra("USER_ID");

        findViewById(R.id.btn_start).setOnClickListener(v -> {
            Intent intent = new Intent(BrushingTipsActivity.this, BrushingTaskActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
            finish();
        });
    }
}
