package com.example.smileapp;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class PatientManagementActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView recyclerView;
    private PatientAdapter adapter;
    private String doctorId;
    private List<User> patientList = new ArrayList<>();
    private FirebaseFirestore dbFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_management);

        db = AppDatabase.getInstance(this);
        dbFirestore = FirebaseFirestore.getInstance();
        doctorId = getIntent().getStringExtra("USER_ID");

        recyclerView = findViewById(R.id.patient_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PatientAdapter(patientList);
        recyclerView.setAdapter(adapter);

        loadPatients();
    }

    private void loadPatients() {
        if (doctorId == null) {
            Toast.makeText(this, "Error: Doctor ID missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sync from Firestore first to ensure we have all patients
        dbFirestore.collection("users")
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("role", "child")
            .whereEqualTo("isApproved", true)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                new Thread(() -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String uid = doc.getString("uid");
                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        Long points = doc.getLong("points");

                        User existing = db.appDao().getUserById(uid);
                        if (existing == null) {
                            User newUser = new User(uid, name, email, "", "child");
                            newUser.isApproved = true;
                            newUser.doctorId = doctorId;
                            newUser.points = points != null ? points.intValue() : 0;
                            db.appDao().insertUser(newUser);
                        } else {
                            existing.name = name;
                            existing.points = points != null ? points.intValue() : 0;
                            db.appDao().updateUser(existing);
                        }
                    }
                    
                    // Now load from local DB for UI
                    List<User> patients = db.appDao().getPatientsByDoctor(doctorId);
                    runOnUiThread(() -> {
                        patientList.clear();
                        patientList.addAll(patients);
                        adapter.notifyDataSetChanged();
                        if (patientList.isEmpty()) {
                            Toast.makeText(PatientManagementActivity.this, "No approved patients found", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            })
            .addOnFailureListener(e -> {
                // Fallback to local DB if sync fails
                new Thread(() -> {
                    List<User> patients = db.appDao().getPatientsByDoctor(doctorId);
                    runOnUiThread(() -> {
                        patientList.clear();
                        patientList.addAll(patients);
                        adapter.notifyDataSetChanged();
                    });
                }).start();
            });
    }

    private void showRemovePointsDialog(User patient, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove Points from " + patient.name);
        builder.setMessage("Enter the number of points to remove:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Remove", (dialog, which) -> {
            String value = input.getText().toString();
            if (!value.isEmpty()) {
                int pointsToRemove = Integer.parseInt(value);
                if (pointsToRemove > 0) {
                    // Update Firestore first
                    int newPoints = patient.points - pointsToRemove;
                    if (newPoints < 0) newPoints = 0;
                    
                    final int finalNewPoints = newPoints;
                    dbFirestore.collection("users").document(patient.uid)
                        .update("points", finalNewPoints)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                db.appDao().removePoints(patient.uid, pointsToRemove);
                                runOnUiThread(() -> {
                                    Toast.makeText(this, pointsToRemove + " points removed!", Toast.LENGTH_SHORT).show();
                                    loadPatients(); // Refresh list
                                });
                            }).start();
                        });
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.ViewHolder> {
        private List<User> patients;

        public PatientAdapter(List<User> patients) {
            this.patients = patients;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User patient = patients.get(position);
            holder.nameText.setText(patient.name);
            holder.pointsText.setText("Points: " + patient.points);
            holder.removeBtn.setOnClickListener(v -> showRemovePointsDialog(patient, position));
        }

        @Override
        public int getItemCount() {
            return patients.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, pointsText;
            View removeBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.patient_name);
                pointsText = itemView.findViewById(R.id.patient_points);
                removeBtn = itemView.findViewById(R.id.remove_points_button);
            }
        }
    }
}
