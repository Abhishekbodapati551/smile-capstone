package com.example.smileapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.example.smileapp.models.Appointment;
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
    private FirebaseFirestore dbFirestore;
    private BroadcastReceiver refreshReceiver;
    private ListenerRegistration approvalsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);

        db = AppDatabase.getInstance(this);
        dbFirestore = FirebaseFirestore.getInstance();
        doctorId = getIntent().getStringExtra("USER_ID");

        if (doctorId == null) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, DoctorLoginActivity.class));
                finish();
                return;
            }
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
        syncWithFirestore();

        // Open Profile
        doctorNameText.setOnClickListener(v -> openProfile());
        doctorSubtitleText.setOnClickListener(v -> openProfile());

        findViewById(R.id.btn_manage_appointments_feature).setOnClickListener(v -> {
            Intent intent = new Intent(this, DoctorAppointmentManagerActivity.class);
            intent.putExtra("USER_ID", doctorId);
            startActivity(intent);
        });

        findViewById(R.id.logout_btn).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.appointments_card).setOnClickListener(v -> {
            Intent intent = new Intent(this, DoctorAppointmentManagerActivity.class);
            intent.putExtra("USER_ID", doctorId);
            startActivity(intent);
        });

        findViewById(R.id.pending_approvals_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctor = db.appDao().getUserById(doctorId);
                if (doctor != null && doctor.doctorId != null) {
                    Intent intent = new Intent(this, PendingApprovalsActivity.class);
                    intent.putExtra("DOCTOR_ID", doctor.doctorId); // Pass sequential ID
                    startActivity(intent);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Syncing doctor data... please try again in a moment.", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.pending_reviews_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctor = db.appDao().getUserById(doctorId);
                if (doctor != null && doctor.doctorId != null) {
                    Intent intent = new Intent(this, PendingReviewsActivity.class);
                    intent.putExtra("DOCTOR_ID", doctor.doctorId); // Pass sequential ID
                    startActivity(intent);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Syncing doctor data...", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.total_patients_card).setOnClickListener(v -> {
            new Thread(() -> {
                User doctor = db.appDao().getUserById(doctorId);
                if (doctor != null && doctor.doctorId != null) {
                    Intent intent = new Intent(this, PatientManagementActivity.class);
                    intent.putExtra("USER_ID", doctor.doctorId);
                    startActivity(intent);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Syncing doctor data...", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.btn_view_all_patients).setOnClickListener(v -> {
            new Thread(() -> {
                User doctor = db.appDao().getUserById(doctorId);
                if (doctor != null && doctor.doctorId != null) {
                    Intent intent = new Intent(this, PatientManagementActivity.class);
                    intent.putExtra("USER_ID", doctor.doctorId); // Pass sequential ID (PatientManagement expects this for Firestore query)
                    startActivity(intent);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Syncing doctor data...", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        // Refresh stats receiver
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Refresh local stats and sync with Firestore
                loadStats();
                syncWithFirestore();
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, new IntentFilter("com.example.smileapp.ACTION_REFRESH_DOCTOR_DASHBOARD"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, new IntentFilter("com.example.smileapp.ACTION_REFRESH_DOCTOR_DASHBOARD"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (approvalsListener != null) {
            approvalsListener.remove();
        }
        if (reviewsListener != null) {
            reviewsListener.remove();
        }
    }

    @Override
    protected void onDestroy() {
        if (refreshReceiver != null) {
            unregisterReceiver(refreshReceiver);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDoctorData();
        syncWithFirestore();
        startRealtimeApprovalsListener();
    }

    private void loadDoctorData() {
        if (doctorId == null) return;
        
        new Thread(() -> {
            User doctor = db.appDao().getUserById(doctorId);
            if (doctor != null) {
                runOnUiThread(() -> {
                    doctorNameText.setText("Welcome, Dr. " + doctor.name);
                    if (doctorIdDisplay != null) {
                        doctorIdDisplay.setText("Your Doctor ID: " + doctor.doctorId);
                    }
                    if (doctor.clinicName != null) {
                        doctorSubtitleText.setText(doctor.clinicName);
                    }
                });
            }
        }).start();
    }

    private void openProfile() {
        Intent intent = new Intent(this, DoctorProfileActivity.class);
        intent.putExtra("USER_ID", doctorId);
        startActivity(intent);
    }

    private void syncWithFirestore() {
        if (doctorId == null) return;

        // Step 1: Fetch this doctor's specific data to ensure sequential doctorId is synced
        dbFirestore.collection("users")
            .whereEqualTo("uid", doctorId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    String sequentialId = doc.getString("doctorId");
                    String name = doc.getString("name");

                    new Thread(() -> {
                        User existing = db.appDao().getUserById(doctorId);
                        if (existing != null) {
                            existing.doctorId = sequentialId;
                            existing.name = name;
                            db.appDao().updateUser(existing);
                            runOnUiThread(this::loadDoctorData);
                        }

                        // Step 2: Fetch all patients for this doctor using the SEQUENTIAL ID
                        if (sequentialId != null) {
                            dbFirestore.collection("users")
                                .whereEqualTo("doctorId", sequentialId)
                                .whereEqualTo("role", "child")
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    new Thread(() -> {
                                        for (DocumentSnapshot pDoc : queryDocumentSnapshots) {
                                            String pUid = pDoc.getString("uid");
                                            String pName = pDoc.getString("name");
                                            String pEmail = pDoc.getString("email");
                                            String pRole = pDoc.getString("role");
                                            Boolean isApproved = pDoc.getBoolean("isApproved");
                                            String pDoctorId = pDoc.getString("doctorId");
                                            Long points = pDoc.getLong("points");

                                            User pExisting = db.appDao().getUserById(pUid);
                                            if (pExisting == null) {
                                                User newUser = new User(pUid, pName, pEmail, "", pRole);
                                                newUser.isApproved = isApproved != null && isApproved;
                                                newUser.doctorId = pDoctorId;
                                                newUser.points = points != null ? points.intValue() : 0;
                                                db.appDao().insertUser(newUser);
                                            } else {
                                                pExisting.name = pName;
                                                pExisting.isApproved = isApproved != null && isApproved;
                                                pExisting.doctorId = pDoctorId;
                                                pExisting.points = points != null ? points.intValue() : 0;
                                                db.appDao().updateUser(pExisting);
                                            }
                                        }

                                        // Step 3: Fetch all brushing logs for this doctor
                                        dbFirestore.collection("brushing_logs")
                                            .whereEqualTo("doctorId", sequentialId)
                                            .get()
                                            .addOnSuccessListener(logSnapshots -> {
                                                new Thread(() -> {
                                                    for (DocumentSnapshot lDoc : logSnapshots) {
                                                        String lId = lDoc.getId();
                                                        String childId = lDoc.getString("childId");
                                                        String videoUri = lDoc.getString("videoUri");
                                                        Long timestamp = lDoc.getLong("timestamp");
                                                        Boolean approved = lDoc.getBoolean("approved");

                                                        BrushingLog lExisting = db.appDao().getBrushingLogByFirestoreId(lId);
                                                        if (lExisting == null) {
                                                            BrushingLog newLog = new BrushingLog(childId, videoUri, timestamp != null ? timestamp : 0);
                                                            newLog.firestoreId = lId;
                                                            newLog.approved = approved != null && approved;
                                                            db.appDao().insertBrushingLog(newLog);
                                                        } else {
                                                            lExisting.approved = approved != null && approved;
                                                            // We don't have an updateLog method in AppDao yet, but we can add it or just use approveBrushingLogByFirestoreId
                                                            if (lExisting.approved) {
                                                                db.appDao().approveBrushingLogByFirestoreId(lId);
                                                            }
                                                        }
                                                    }
                                                    runOnUiThread(this::loadStats);
                                                }).start();
                                            });
                                    }).start();
                                });
                        } else {
                            runOnUiThread(this::loadStats);
                        }
                    }).start();
                }
            });
    }

    private void loadStats() {
        if (doctorId == null) return;

        new Thread(() -> {
            // Retrieve doctor's sequential ID from local DB
            User doctor = db.appDao().getUserById(doctorId);
            String sequentialDoctorId = doctor != null ? doctor.doctorId : null;

            List<User> patients = db.appDao().getPatientsByDoctor(sequentialDoctorId != null ? sequentialDoctorId : doctorId);
            List<User> pendingAppr = db.appDao().getPendingChildren();
            List<BrushingLog> pendingRev = db.appDao().getPendingBrushingLogsForDoctor(sequentialDoctorId != null ? sequentialDoctorId : doctorId);
            List<Appointment> apps = db.appDao().getAppointmentsForDoctor(sequentialDoctorId != null ? sequentialDoctorId : doctorId);

            runOnUiThread(() -> {
                if (totalPatientsText != null) {
                    totalPatientsText.setText(String.valueOf(patients.size()));
                }
                if (pendingApprovalsText != null) {
                    // Count pending approvals for this doctor's sequential ID
                    int count = 0;
                    if (sequentialDoctorId != null) {
                        for (User u : pendingAppr) {
                            if (sequentialDoctorId.equals(u.doctorId)) {
                                count++;
                            }
                        }
                    }
                    pendingApprovalsText.setText(String.valueOf(count));
                }
                if (pendingReviewsText != null) {
                    pendingReviewsText.setText(String.valueOf(pendingRev.size()));
                }
                
                if (todaysApptsText != null) {
                    todaysApptsText.setText(String.valueOf(apps.size()));
                }

                dashboardApps.clear();
                dashboardApps.addAll(apps);
                appointmentsAdapter.notifyDataSetChanged();

                dashboardPatients.clear();
                dashboardPatients.addAll(patients);
                patientsAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private class DashboardListAdapter extends RecyclerView.Adapter<DashboardListAdapter.ViewHolder> {
        private boolean isAppointment;

        public DashboardListAdapter(boolean isAppointment) {
            this.isAppointment = isAppointment;
        }

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
                User patient = dashboardPatients.get(position);
                holder.title.setText(patient.name);
                holder.subtitle.setText("Points: " + patient.points);
            }
        }

        @Override
        public int getItemCount() {
            return isAppointment ? dashboardApps.size() : dashboardPatients.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
            }
        }
    }

    private ListenerRegistration reviewsListener;

    private void startRealtimeApprovalsListener() {
        new Thread(() -> {
            User doctor = db.appDao().getUserById(doctorId);
            if (doctor != null && doctor.doctorId != null) {
                String seqId = doctor.doctorId;
                runOnUiThread(() -> {
                    if (approvalsListener != null) approvalsListener.remove();
                    
                    approvalsListener = dbFirestore.collection("users")
                        .whereEqualTo("role", "child")
                        .whereEqualTo("doctorId", seqId)
                        .whereEqualTo("isApproved", false)
                        .addSnapshotListener((value, error) -> {
                            if (error != null) return;
                            if (value != null) {
                                // Trigger a sync to update local DB and stats
                                syncWithFirestore();
                            }
                        });

                    if (reviewsListener != null) reviewsListener.remove();
                    reviewsListener = dbFirestore.collection("brushing_logs")
                        .whereEqualTo("doctorId", seqId)
                        .whereEqualTo("approved", false)
                        .addSnapshotListener((value, error) -> {
                            if (error != null) return;
                            if (value != null) {
                                syncWithFirestore();
                            }
                        });
                });
            }
        }).start();
    }
}
