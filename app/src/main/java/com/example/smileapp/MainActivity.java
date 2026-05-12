package com.example.smileapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialCardView childCard = findViewById(R.id.child_card);
        MaterialCardView doctorCard = findViewById(R.id.doctor_card);

        childCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChildLoginActivity.class);
            startActivity(intent);
        });

        doctorCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DoctorLoginActivity.class);
            startActivity(intent);
        });
    }
}