package com.example.smileapp;

import android.os.Bundle;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.List;

public class PendingApprovalsActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView pendingRecycler, totalPatientsRecycler;
    private UserAdapter pendingAdapter, totalAdapter;
    private String doctorId; 
    private List<User> pendingUsers = new ArrayList<>();
    private List<User> approvedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approvals);

        db = AppDatabase.getInstance(this);
        doctorId = getIntent().getStringExtra("DOCTOR_ID");

        if (doctorId == null) {
            Toast.makeText(this, "Doctor ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pendingRecycler = findViewById(R.id.pending_recycler_view);
        totalPatientsRecycler = findViewById(R.id.total_patients_recycler);

        pendingRecycler.setLayoutManager(new LinearLayoutManager(this));
        totalPatientsRecycler.setLayoutManager(new LinearLayoutManager(this));

        pendingAdapter = new UserAdapter(pendingUsers, true);
        totalAdapter = new UserAdapter(approvedUsers, false);

        pendingRecycler.setAdapter(pendingAdapter);
        totalPatientsRecycler.setAdapter(totalAdapter);

        loadAllData();
    }

    private void loadAllData() {
        new Thread(() -> {
            try {
                // 1. Fetch from Supabase
                List<User> latestPending = SupabaseAuthHelper.fetchPendingPatientsBlocking(doctorId);
                List<User> latestApproved = SupabaseAuthHelper.fetchPatientsBlocking(doctorId);
                
                // 2. Sync Local DB
                for (User u : latestPending) db.appDao().insertUser(u);
                for (User u : latestApproved) db.appDao().insertUser(u);

                runOnUiThread(() -> {
                    pendingUsers.clear();
                    pendingUsers.addAll(latestPending);
                    pendingAdapter.notifyDataSetChanged();

                    approvedUsers.clear();
                    approvedUsers.addAll(latestApproved);
                    totalAdapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Log.e("PendingApprovals", "Fetch failed", e);
                // Fallback to local
                List<User> allLocal = db.appDao().getPatientsByDoctor(doctorId);
                List<User> pendingLocal = db.appDao().getPendingChildren();
                
                runOnUiThread(() -> {
                    approvedUsers.clear();
                    approvedUsers.addAll(allLocal);
                    totalAdapter.notifyDataSetChanged();

                    pendingUsers.clear();
                    for (User u : pendingLocal) if (doctorId.equals(u.doctorId)) pendingUsers.add(u);
                    pendingAdapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private List<User> users;
        private boolean isPending;

        public UserAdapter(List<User> users, boolean isPending) {
            this.users = users;
            this.isPending = isPending;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_user, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User u = users.get(position);
            holder.nameText.setText(u.name);
            holder.emailText.setText(u.email);
            
            if (isPending) {
                holder.approveBtn.setVisibility(View.VISIBLE);
                holder.approveBtn.setOnClickListener(v -> {
                    holder.approveBtn.setEnabled(false);
                    new Thread(() -> {
                        if (SupabaseAuthHelper.approveUserBlocking(u.uid)) {
                            db.appDao().approveUser(u.uid);
                            runOnUiThread(() -> {
                                Toast.makeText(PendingApprovalsActivity.this, "Patient Approved!", Toast.LENGTH_SHORT).show();
                                loadAllData(); // Refresh both lists
                            });
                        } else {
                            runOnUiThread(() -> {
                                holder.approveBtn.setEnabled(true);
                                Toast.makeText(PendingApprovalsActivity.this, "Approval failed.", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                });
            } else {
                holder.approveBtn.setVisibility(View.GONE);
                holder.nameText.setText(u.name + " (Verified)");
            }
        }

        @Override
        public int getItemCount() { return users.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, emailText; MaterialButton approveBtn;
            public ViewHolder(@NonNull View v) {
                super(v);
                nameText = v.findViewById(R.id.user_name);
                emailText = v.findViewById(R.id.user_email);
                approveBtn = v.findViewById(R.id.approve_button);
            }
        }
    }
}
