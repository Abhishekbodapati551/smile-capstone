package com.example.smileapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.smileapp.models.Appointment;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import java.util.List;

@Dao
public interface AppDao {
    @Insert
    void insertUser(User user);

    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    User login(String email, String password);

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    User getUserById(String uid);

    @Update
    void updateUser(User user);

    @Insert
    void insertAppointment(Appointment appointment);

    @Update
    void updateAppointment(Appointment appointment);

    @Query("DELETE FROM appointments WHERE id = :id")
    void deleteAppointment(int id);

    @Query("SELECT * FROM appointments WHERE childId = :childId")
    List<Appointment> getAppointmentsForChild(String childId);

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId")
    List<Appointment> getAppointmentsForDoctor(String doctorId);

    @Insert
    void insertBrushingLog(BrushingLog log);

    @Query("SELECT * FROM brushing_logs WHERE childId = :childId")
    List<BrushingLog> getBrushingLogsForChild(String childId);

    @Query("SELECT * FROM brushing_logs WHERE approved = 0")
    List<BrushingLog> getPendingBrushingLogs();

    // Get pending brushing logs for children of a specific doctor
    @Query("SELECT * FROM brushing_logs WHERE approved = 0 AND childId IN (SELECT uid FROM users WHERE doctorId = :doctorId AND role = 'child')")
    List<BrushingLog> getPendingBrushingLogsForDoctor(String doctorId);

    @Query("UPDATE brushing_logs SET approved = 1 WHERE id = :logId")
    void approveBrushingLog(int logId);

    @Query("SELECT * FROM brushing_logs WHERE firestoreId = :firestoreId LIMIT 1")
    BrushingLog getBrushingLogByFirestoreId(String firestoreId);

    @Query("UPDATE brushing_logs SET approved = 1 WHERE firestoreId = :firestoreId")
    void approveBrushingLogByFirestoreId(String firestoreId);

    @Query("SELECT * FROM users WHERE role = 'child' AND isApproved = 0")
    List<User> getPendingChildren();

    @Query("UPDATE users SET isApproved = 1 WHERE uid = :uid")
    void approveUser(String uid);

    @Query("SELECT * FROM users WHERE doctorId = :doctorId AND role = 'child' AND isApproved = 1")
    List<User> getPatientsByDoctor(String doctorId);

    @Query("UPDATE users SET points = points - :pointsToRemove WHERE uid = :uid")
    void removePoints(String uid, int pointsToRemove);

    @Query("SELECT SUM(points) FROM users WHERE doctorId = :doctorId AND role = 'child'")
    int getTotalPointsAwardedByDoctor(String doctorId);
}
