package com.example.smileapp;

import android.content.Intent;
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
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class PendingApprovalsActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView recyclerView;
    private PendingAdapter adapter;
    private FirebaseFirestore dbFirestore;
    private String doctorId;
    private List<User> pendingUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approvals);

        db = AppDatabase.getInstance(this);
        dbFirestore = FirebaseFirestore.getInstance();
        // Retrieve the doctor ID (sequential ID) passed from the dashboard
        doctorId = getIntent().getStringExtra("DOCTOR_ID");
        if (doctorId == null) {
            Toast.makeText(this, "Error: Doctor ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recyclerView = findViewById(R.id.pending_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PendingAdapter(pendingUsers);
        recyclerView.setAdapter(adapter);

        // Directly load pending users using the provided doctorId (sequential ID)
        loadPendingUsers();
    }

    private void loadPendingUsers() {
        dbFirestore.collection("users")
            .whereEqualTo("role", "child")
            .whereEqualTo("isApproved", false)
            .whereEqualTo("doctorId", doctorId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                pendingUsers.clear();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    User user = new User(
                        doc.getString("uid"),
                        doc.getString("name"),
                        doc.getString("email"),
                        "", 
                        "child"
                    );
                    user.doctorId = doctorId;
                    user.isApproved = false;
                    pendingUsers.add(user);
                    
                    new Thread(() -> {
                        if (db.appDao().getUserById(user.uid) == null) {
                            db.appDao().insertUser(user);
                        }
                    }).start();
                }
                adapter.notifyDataSetChanged();
                if (pendingUsers.isEmpty()) {
                    Toast.makeText(this, "No pending approvals found for your ID", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Error fetching pending users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.ViewHolder> {
        private List<User> users;

        public PendingAdapter(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            holder.nameText.setText(user.name);
            holder.emailText.setText(user.email);
            holder.approveBtn.setOnClickListener(v -> {
                // IMPORTANT: When approving, we must use the document ID of the user.
                // For children, the document ID is their UID.
                dbFirestore.collection("users").document(user.uid)
                        .update("isApproved", true)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                db.appDao().approveUser(user.uid);
                                runOnUiThread(() -> {
                                    Toast.makeText(PendingApprovalsActivity.this, "User Approved!", Toast.LENGTH_SHORT).show();
                                    users.remove(position);
                                    notifyItemRemoved(position);

                                    // Notify Dashboard to refresh
                                    Intent refreshIntent = new Intent("com.example.smileapp.ACTION_REFRESH_DOCTOR_DASHBOARD");
                                    refreshIntent.setPackage(getPackageName());
                                    sendBroadcast(refreshIntent);
                                });
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(PendingApprovalsActivity.this, "Failed to approve in cloud: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, emailText;
            MaterialButton approveBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.user_name);
                emailText = itemView.findViewById(R.id.user_email);
                approveBtn = itemView.findViewById(R.id.approve_button);
            }
        }
    }
}
