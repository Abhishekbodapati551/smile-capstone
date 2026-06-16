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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DoctorAppointmentManagerActivity extends AppCompatActivity {

    private AppDatabase db;
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
        new Thread(() -> myPatients = db.appDao().getPatientsByDoctor(sequentialDoctorId)).start();
    }

    private void loadAppointments() {
        if (sequentialDoctorId == null) return;
        new Thread(() -> {
            List<Appointment> apps = db.appDao().getAppointmentsForDoctor(sequentialDoctorId);
            runOnUiThread(() -> {
                appointmentList.clear();
                appointmentList.addAll(apps);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void showAddAppointmentDialog() {
        if (myPatients.isEmpty()) {
            Toast.makeText(this, "No patients found.", Toast.LENGTH_SHORT).show();
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
        List<String> names = new ArrayList<>();
        for (User u : myPatients) names.add(u.name);
        patientSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names));

        pickDateBtn.setOnClickListener(v -> {
            new DatePickerDialog(this, (v1, y, m, d) -> {
                calendar.set(y, m, d);
                new TimePickerDialog(this, (v2, h, min) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, h);
                    calendar.set(Calendar.MINUTE, min);
                    dateText.setText(new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(calendar.getTime()));
                }, 10, 0, false).show();
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        builder.setPositiveButton("Schedule", (dialog, which) -> {
            String name = patientSpinner.getText().toString();
            String type = typeEdit.getText().toString().trim();
            User p = null;
            for (User u : myPatients) if (u.name.equals(name)) { p = u; break; }

            if (p != null && !type.isEmpty()) {
                Appointment app = new Appointment(p.uid, p.name, sequentialDoctorId, calendar.getTimeInMillis(), type);
                saveAppointment(app);
            }
        });
        builder.setNegativeButton("Cancel", null).show();
    }

    private void saveAppointment(Appointment app) {
        new Thread(() -> {
            boolean success = SupabaseAuthHelper.saveAppointmentBlocking(app);
            if (success) {
                db.appDao().insertAppointment(app);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Scheduled!", Toast.LENGTH_SHORT).show();
                    loadAppointments();
                });
            }
        }).start();
    }

    private class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {
        private List<Appointment> apps;
        public AppointmentAdapter(List<Appointment> apps) { this.apps = apps; }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Appointment a = apps.get(position);
            holder.pName.setText(a.childName);
            holder.type.setText(a.type);
            holder.date.setText(new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(new Date(a.date)));
        }
        @Override
        public int getItemCount() { return apps.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView pName, type, date;
            public ViewHolder(@NonNull View v) {
                super(v);
                pName = v.findViewById(R.id.patient_name);
                type = v.findViewById(R.id.appointment_type);
                date = v.findViewById(R.id.appointment_date);
            }
        }
    }
}
