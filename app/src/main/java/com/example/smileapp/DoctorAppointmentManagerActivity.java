package com.example.smileapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.Appointment;
import com.example.smileapp.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DoctorAppointmentManagerActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseFirestore dbFirestore;
    private String doctorUid;
    private String sequentialDoctorId;
    private RecyclerView recyclerView;
    private AppointmentAdapter adapter;
    private List<Appointment> appointmentList = new ArrayList<>();
    private List<User> myPatients = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_appointment_manager);

        db = AppDatabase.getInstance(this);
        dbFirestore = FirebaseFirestore.getInstance();
        doctorUid = getIntent().getStringExtra("USER_ID");

        recyclerView = findViewById(R.id.appointments_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppointmentAdapter(appointmentList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.add_appointment_btn).setOnClickListener(v -> showAddAppointmentDialog());

        loadDoctorData();
    }

    private void loadDoctorData() {
        new Thread(() -> {
            User doctor = db.appDao().getUserById(doctorUid);
            if (doctor != null) {
                sequentialDoctorId = doctor.doctorId;
                loadPatients();
                loadAppointments();
            }
        }).start();
    }

    private void loadPatients() {
        if (sequentialDoctorId == null) return;
        new Thread(() -> {
            myPatients = db.appDao().getPatientsByDoctor(sequentialDoctorId);
        }).start();
    }

    private void loadAppointments() {
        if (sequentialDoctorId == null) return;
        new Thread(() -> {
            List<Appointment> apps = db.appDao().getAppointmentsForDoctor(sequentialDoctorId);
            runOnUiThread(() -> {
                appointmentList.clear();
                appointmentList.addAll(apps);
                adapter.notifyDataSetChanged();
                updateStats();
            });
        }).start();
    }

    private void updateStats() {
        // Find stats textviews in layout and update them
        // For now just printing log or toast if needed
    }

    private void showAddAppointmentDialog() {
        if (myPatients.isEmpty()) {
            Toast.makeText(this, "No patients found. Please approve some patients first.", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_appointment, null);
        builder.setView(view);

        AutoCompleteTextView patientSpinner = view.findViewById(R.id.patient_selector);
        TextInputEditText typeEdit = view.findViewById(R.id.appointment_type_edit);
        TextView dateText = view.findViewById(R.id.selected_date_text);
        MaterialButton pickDateBtn = view.findViewById(R.id.pick_date_btn);

        final Calendar calendar = Calendar.getInstance();
        
        // Set up patient names for spinner
        List<String> patientNames = new ArrayList<>();
        for (User u : myPatients) patientNames.add(u.name);
        ArrayAdapter<String> patientAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, patientNames);
        patientSpinner.setAdapter(patientAdapter);

        pickDateBtn.setOnClickListener(v -> {
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                
                new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
                    dateText.setText(sdf.format(calendar.getTime()));
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
                
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        builder.setPositiveButton("Schedule", (dialog, which) -> {
            String selectedPatientName = patientSpinner.getText().toString();
            String type = typeEdit.getText().toString().trim();
            
            User selectedPatient = null;
            for (User u : myPatients) {
                if (u.name.equals(selectedPatientName)) {
                    selectedPatient = u;
                    break;
                }
            }

            if (selectedPatient != null && !type.isEmpty() && !dateText.getText().toString().equals("No date selected")) {
                Appointment app = new Appointment(selectedPatient.uid, selectedPatient.name, sequentialDoctorId, calendar.getTimeInMillis(), type);
                saveAppointment(app);
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveAppointment(Appointment app) {
        new Thread(() -> {
            db.appDao().insertAppointment(app);
            
            // Sync with Firestore so patient gets a notification
            dbFirestore.collection("appointments").add(app)
                .addOnSuccessListener(docRef -> {
                    // Update the appointment with the Firestore ID if needed
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Appointment Scheduled & Synced!", Toast.LENGTH_SHORT).show();
                        loadAppointments();
                    });
                });
        }).start();
    }

    private void rescheduleAppointment(Appointment app) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(app.date);

        new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);

                long newDate = calendar.getTimeInMillis();
                new Thread(() -> {
                    app.date = newDate;
                    db.appDao().updateAppointment(app);
                    
                    // Update in Firestore
                    dbFirestore.collection("appointments")
                        .whereEqualTo("childId", app.childId)
                        .whereEqualTo("date", app.date) // This might be tricky if not exact, better use a firestoreId
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!querySnapshot.isEmpty()) {
                                querySnapshot.getDocuments().get(0).getReference().update("date", newDate);
                            }
                        });

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Appointment Rescheduled!", Toast.LENGTH_SHORT).show();
                        loadAppointments();
                    });
                }).start();

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {
        private List<Appointment> apps;

        public AppointmentAdapter(List<Appointment> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Appointment app = apps.get(position);
            holder.patientName.setText(app.childName);
            holder.type.setText(app.type);
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
            holder.date.setText(sdf.format(new Date(app.date)));

            holder.rescheduleBtn.setOnClickListener(v -> rescheduleAppointment(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView patientName, type, date;
            MaterialButton rescheduleBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                patientName = itemView.findViewById(R.id.patient_name);
                type = itemView.findViewById(R.id.appointment_type);
                date = itemView.findViewById(R.id.appointment_date);
                rescheduleBtn = itemView.findViewById(R.id.reschedule_btn);
            }
        }
    }
}
