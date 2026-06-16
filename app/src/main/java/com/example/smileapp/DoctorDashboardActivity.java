package com.example.smileapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.example.smileapp.models.Appointment;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DoctorDashboardActivity extends AppCompatActivity {

    private AppDatabase db;
    private String doctorId;
    private TextView doctorNameText, doctorSubtitleText, doctorIdDisplay, totalPatientsText, todaysApptsText, pendingApprovalsText, pendingReviewsText;
    private RecyclerView appointmentsRecycler, patientsRecycler;
    private DashboardListAdapter appointmentsAdapter, patientsAdapter;
    private List<Appointment> dashboardApps = new ArrayList<>();
    private List<User> dashboardPatients = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);

        db = AppDatabase.getInstance(this);
        doctorId = getIntent().getStringExtra("USER_ID");

        if (doctorId == null) {
            startActivity(new Intent(this, DoctorLoginActivity.class));
            finish();
            return;
        }

        doctorNameText = findViewById(R.id.doctor_name);
        doctorSubtitleText = findViewById(R.id.doctor_subtitle);
        doctorIdDisplay = findViewById(R.id.doctor_id_display);
        totalPatientsText = findViewById(R.id.total_patients_text);
        todaysApptsText = findViewById(R.id.todays_appts_text);
        pendingApprovalsText = findViewById(R.id.pending_approvals_text);
        pendingReviewsText = findViewById(R.id.pending_reviews_text);

        appointmentsRecycler = findViewById(R.id.appointments_mini_recycler);
        patientsRecycler = findViewById(R.id.recent_patients_recycler);
        
        appointmentsRecycler.setLayoutManager(new LinearLayoutManager(this));
        patientsRecycler.setLayoutManager(new LinearLayoutManager(this));
        
        appointmentsAdapter = new DashboardListAdapter(true);
        patientsAdapter = new DashboardListAdapter(false);
        
        appointmentsRecycler.setAdapter(appointmentsAdapter);
        patientsRecycler.setAdapter(patientsAdapter);

        loadDoctorData();
        syncWithSupabase();

        findViewById(R.id.logout_btn).setOnClickListener(v -> {
            new SessionManager(this).logoutUser();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.total_patients_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctorUser = db.appDao().getUserById(doctorId);
                if (doctorUser != null && doctorUser.doctorId != null) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, PatientManagementActivity.class);
                        intent.putExtra("DOCTOR_ID", doctorUser.doctorId);
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Doctor ID not set.", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.btn_view_all_patients).setOnClickListener(v -> {
            new Thread(() -> {
                User doctorUser = db.appDao().getUserById(doctorId);
                if (doctorUser != null && doctorUser.doctorId != null) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, PatientManagementActivity.class);
                        intent.putExtra("DOCTOR_ID", doctorUser.doctorId);
                        startActivity(intent);
                    });
                }
            }).start();
        });

        findViewById(R.id.appointments_card).setOnClickListener(v -> {
            Intent intent = new Intent(this, DoctorAppointmentManagerActivity.class);
            intent.putExtra("USER_ID", doctorId);
            startActivity(intent);
        });

        findViewById(R.id.pending_approvals_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctorUser = db.appDao().getUserById(doctorId);
                if (doctorUser != null && doctorUser.doctorId != null) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, PendingApprovalsActivity.class);
                        intent.putExtra("DOCTOR_ID", doctorUser.doctorId); // Pass the 4-digit ID
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Doctor ID not set. Please go to Profile.", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.pending_reviews_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctorUser = db.appDao().getUserById(doctorId);
                if (doctorUser != null && doctorUser.doctorId != null) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, PendingReviewsActivity.class);
                        intent.putExtra("DOCTOR_ID", doctorUser.doctorId);
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Doctor ID not set.", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.btn_edit_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, DoctorProfileActivity.class);
            intent.putExtra("USER_ID", doctorId);
            startActivity(intent);
        });
    }

    private void syncWithSupabase() {
        new Thread(() -> {
            try {
                User doctor = db.appDao().getUserById(doctorId);
                if (doctor != null && doctor.doctorId != null) {
                    List<User> patients = SupabaseAuthHelper.fetchPatientsBlocking(doctor.doctorId);
                    for (User p : patients) db.appDao().insertUser(p);
                    
                    List<Appointment> apps = SupabaseAuthHelper.fetchAppointmentsBlocking(doctor.doctorId);
                    for (Appointment a : apps) db.appDao().insertAppointment(a);
                    
                    runOnUiThread(this::loadStats);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadDoctorData() {
        new Thread(() -> {
            // 1. Show local data INSTANTLY for speed
            User currentLocal = db.appDao().getUserById(doctorId);
            if (currentLocal != null) {
                updateDoctorUI(currentLocal);
            }

            // 2. Refresh from Supabase in the background only if needed
            try {
                SessionManager sm = new SessionManager(this);
                if (sm.getSavedEmail() != null && sm.getSavedPassword() != null) {
                    // Optimized fetch: Just get the profile row, don't re-authenticate
                    User freshData = SupabaseAuthHelper.signInBlocking(sm.getSavedEmail(), sm.getSavedPassword());
                    
                    if (freshData != null) {
                        db.appDao().insertUser(freshData);
                        runOnUiThread(() -> updateDoctorUI(freshData));
                    }
                }
            } catch (Exception e) {
                Log.e("DoctorDashboard", "Background sync failed", e);
            }
            
            loadStats();
        }).start();
    }

    private void updateDoctorUI(User doctor) {
        doctorNameText.setText("Welcome, Dr. " + doctor.name);
        if (doctorIdDisplay != null) {
            String displayId = (doctor.doctorId != null && !doctor.doctorId.equals("null")) ? doctor.doctorId : "Not Set (Go to Profile)";
            doctorIdDisplay.setText("Your Doctor ID: " + displayId);
        }
        if (doctor.clinicName != null && !doctor.clinicName.isEmpty()) {
            doctorSubtitleText.setText(doctor.clinicName);
        } else {
            doctorSubtitleText.setText("Hospital Name Not Set");
        }
    }

    private void loadStats() {
        new Thread(() -> {
            User doctor = db.appDao().getUserById(doctorId);
            if (doctor == null || doctor.doctorId == null) return;
            
            String seqId = doctor.doctorId;

            // FORCE SYNC FROM SUPABASE
            try {
                // Fetch latest patients
                List<User> latestPatients = SupabaseAuthHelper.fetchPatientsBlocking(seqId);
                for (User p : latestPatients) db.appDao().insertUser(p);

                // Fetch latest pending reviews
                List<BrushingLog> latestLogs = SupabaseAuthHelper.fetchPendingBrushingLogsBlocking(seqId);
                Log.d("DoctorDashboard", "Fetched " + latestLogs.size() + " logs for doctor " + seqId);
                for (BrushingLog log : latestLogs) {
                    log.doctorId = seqId;
                    db.appDao().insertBrushingLog(log);
                }
                
                // Fetch latest appointments
                List<Appointment> latestApps = SupabaseAuthHelper.fetchAppointmentsBlocking(seqId);
                for (Appointment a : latestApps) db.appDao().insertAppointment(a);

            } catch (Exception e) {
                Log.e("DoctorDashboard", "Stats force sync failed", e);
            }
            
            List<User> patients = db.appDao().getPatientsByDoctor(seqId);
            List<Appointment> apps = db.appDao().getAppointmentsForDoctor(seqId);
            List<User> pendingAppr = db.appDao().getPendingChildren();
            List<BrushingLog> pendingRev = db.appDao().getPendingBrushingLogsForDoctor(seqId);

            List<User> finalPatients = patients;
            runOnUiThread(() -> {
                totalPatientsText.setText(String.valueOf(finalPatients.size()));
                todaysApptsText.setText(String.valueOf(apps.size()));
                int pendingCount = 0;
                List<BrushingLog> uniquePending = new ArrayList<>();
                for (BrushingLog log : pendingRev) {
                    boolean found = false;
                    for (BrushingLog unique : uniquePending) {
                        if (unique.id == log.id) { found = true; break; }
                    }
                    if (!found) {
                        uniquePending.add(log);
                        pendingCount++;
                    }
                }
                pendingReviewsText.setText(String.valueOf(pendingCount));
                
                int pendingApprCount = 0;
                for (User u : pendingAppr) if (seqId.equals(u.doctorId)) pendingApprCount++;
                pendingApprovalsText.setText(String.valueOf(pendingApprCount));

                dashboardApps.clear();
                dashboardApps.addAll(apps);
                appointmentsAdapter.notifyDataSetChanged();

                dashboardPatients.clear();
                dashboardPatients.addAll(finalPatients);
                patientsAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private class DashboardListAdapter extends RecyclerView.Adapter<DashboardListAdapter.ViewHolder> {
        private boolean isAppointment;
        public DashboardListAdapter(boolean isAppointment) { this.isAppointment = isAppointment; }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dashboard_list, parent, false);
            return new ViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (isAppointment) {
                Appointment app = dashboardApps.get(position);
                holder.title.setText(app.childName);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                holder.subtitle.setText(app.type + " - " + sdf.format(new Date(app.date)));
            } else {
                User p = dashboardPatients.get(position);
                holder.title.setText(p.name);
                holder.subtitle.setText("Points: " + p.points);
            }
        }
        @Override
        public int getItemCount() { return isAppointment ? dashboardApps.size() : dashboardPatients.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDoctorData(); // Refresh when returning from profile
    }
}
