package com.example.smileapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PendingReviewsActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView recyclerView;
    private ReviewsAdapter adapter;
    private String sequentialDoctorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_reviews);

        db = AppDatabase.getInstance(this);
        sequentialDoctorId = getIntent().getStringExtra("DOCTOR_ID");
        
        recyclerView = findViewById(R.id.reviews_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadReviews();
    }

    private void loadReviews() {
        if (sequentialDoctorId == null) {
            Toast.makeText(this, "No Doctor ID provided", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            List<BrushingLog> logs = db.appDao().getPendingBrushingLogsForDoctor(sequentialDoctorId);
            runOnUiThread(() -> {
                adapter = new ReviewsAdapter(logs);
                recyclerView.setAdapter(adapter);
            });
        }).start();
    }

    private class ReviewsAdapter extends RecyclerView.Adapter<ReviewsAdapter.ViewHolder> {
        private List<BrushingLog> logs;

        public ReviewsAdapter(List<BrushingLog> logs) {
            this.logs = logs;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_review, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BrushingLog log = logs.get(position);
            
            // Fetch child name
            new Thread(() -> {
                User child = db.appDao().getUserById(log.childId);
                runOnUiThread(() -> {
                    if (child != null) holder.nameText.setText(child.name);
                });
            }).start();

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            holder.timeText.setText(sdf.format(new Date(log.timestamp)));

            holder.viewBtn.setOnClickListener(v -> {
                // Open system video player for now
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(log.videoUri), "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                try {
                    startActivity(intent);
                    // Also provide a button to approve in a real app, 
                    // for now we'll just approve it when they click review
                    approveLog(log, position);
                } catch (Exception e) {
                    Toast.makeText(PendingReviewsActivity.this, "Error playing video", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void approveLog(BrushingLog log, int position) {
            new Thread(() -> {
                db.appDao().approveBrushingLog(log.id);
                
                // Sync to Firestore if available
                if (log.firestoreId != null) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("brushing_logs").document(log.firestoreId)
                        .update("approved", true);
                }

                runOnUiThread(() -> {
                    Toast.makeText(PendingReviewsActivity.this, "Session Approved!", Toast.LENGTH_SHORT).show();
                    logs.remove(position);
                    notifyItemRemoved(position);
                    // Notify Dashboard to refresh
                    Intent refreshIntent = new Intent("com.example.smileapp.ACTION_REFRESH_DOCTOR_DASHBOARD");
                    refreshIntent.setPackage(getPackageName());
                    sendBroadcast(refreshIntent);
                });
            }).start();
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, timeText;
            MaterialButton viewBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.child_name);
                timeText = itemView.findViewById(R.id.timestamp);
                viewBtn = itemView.findViewById(R.id.view_video_button);
            }
        }
    }
}
