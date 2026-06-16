package com.example.smileapp

import io.ktor.client.statement.bodyAsText
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.smileapp.models.User
import com.example.smileapp.models.BrushingLog
import com.example.smileapp.models.Appointment
import java.io.InputStream

object SupabaseAuthHelper {
    
    @JvmStatic
    fun signUpBlocking(name: String, email: String, pass: String, role: String, doctorId: String?): User {
        return runBlocking {
            val res = signUp(name, email, pass, role, doctorId)
            if (res.isSuccess) res.getOrThrow() else throw res.exceptionOrNull()!!
        }
    }

    @JvmStatic
    fun signInBlocking(email: String, pass: String): User {
        return runBlocking {
            val res = signIn(email, pass)
            if (res.isSuccess) res.getOrThrow() else throw res.exceptionOrNull()!!
        }
    }

    @JvmStatic
    fun uploadVideoBlocking(userId: String, inputStream: InputStream): String {
        return runBlocking {
            val client = SupabaseManager.getClient()
            val fileName = "${userId}_${System.currentTimeMillis()}.mp4"
            val bucket = client.storage["brushing_videos"]
            val bytes = inputStream.readBytes()
            bucket.upload(fileName, bytes)
            bucket.publicUrl(fileName)
        }
    }
    
    @JvmStatic
    fun saveLogBlocking(log: BrushingLog, user: User): Boolean {
        return runBlocking {
            try {
                val client = SupabaseManager.getClient()
                
                // 1. Removed: Points/Streak update from here. Moves to Approval Step.
                
                // 2. Insert log (Directly pass the log object for automatic serialization)
                // Use a copy without the 'id' field so Supabase SERIAL handles it
                val logToUpload = log.copy(id = 0) 
                client.postgrest["brushing_logs"].insert(logToUpload)
                
                true
            } catch (e: Exception) {
                val errorMsg = if (e is io.ktor.client.plugins.ResponseException) {
                    e.response.bodyAsText()
                } else e.message
                Log.e("SupabaseAuth", "Save log failed: $errorMsg")
                throw Exception(errorMsg)
            }
        }
    }

    @JvmStatic
    fun fetchPatientsBlocking(doctorId: String): List<User> {
        return runBlocking {
            SupabaseManager.getClient().postgrest["profiles"].select {
                filter { eq("doctor_id", doctorId); eq("role", "child") }
            }.decodeList<User>()
        }
    }

    @JvmStatic
    fun fetchPendingPatientsBlocking(doctorId: String): List<User> {
        return runBlocking {
            SupabaseManager.getClient().postgrest["profiles"].select {
                filter { 
                    eq("doctor_id", doctorId)
                    eq("role", "child")
                    eq("is_approved", false)
                }
            }.decodeList<User>()
        }
    }

    @JvmStatic
    fun fetchPendingBrushingLogsBlocking(doctorId: String): List<BrushingLog> {
        return runBlocking {
            try {
                val logs = SupabaseManager.getClient().postgrest["brushing_logs"].select {
                    filter {
                        eq("approved", false)
                    }
                }.decodeList<BrushingLog>()
                Log.d("SupabaseAuth", "Successfully fetched ${logs.size} pending logs")
                logs
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Fetch logs failed: ${e.message}")
                emptyList<BrushingLog>()
            }
        }
    }

    @JvmStatic
    fun fetchAppointmentsBlocking(doctorId: String): List<Appointment> {
        return runBlocking {
            SupabaseManager.getClient().postgrest["appointments"].select {
                filter { eq("doctor_id", doctorId) }
            }.decodeList<Appointment>()
        }
    }

    @JvmStatic
    fun saveAppointmentBlocking(app: Appointment): Boolean {
        return runBlocking {
            try {
                SupabaseManager.getClient().postgrest["appointments"].insert(app)
                true
            } catch (e: Exception) { false }
        }
    }

    @JvmStatic
    fun approveUserBlocking(uid: String): Boolean {
        return runBlocking {
            try {
                SupabaseManager.getClient().postgrest["profiles"].update({
                    set("is_approved", true)
                }) {
                    filter { eq("id", uid) }
                }
                true
            } catch (e: Exception) { false }
        }
    }

    @JvmStatic
    fun approveBrushingBlocking(logId: String, feedback: String): Boolean {
        return runBlocking {
            try {
                val client = SupabaseManager.getClient()
                val idInt = logId.toIntOrNull() ?: return@runBlocking false
                
                // CALL THE SERVER POWER-FUNCTION
                // This awards points and approves the log in one single step on the server
                val params = buildJsonObject {
                    put("p_log_id", idInt)
                    put("p_feedback", feedback)
                    put("p_points_to_add", 5)
                }
                
                client.postgrest.rpc("approve_and_reward", params)
                
                Log.d("SupabaseAuth", "SUCCESS: Server-side approval and reward completed for log $idInt")
                true
            } catch (e: Exception) { 
                Log.e("SupabaseAuth", "Server-side approval failed: ${e.message}")
                false 
            }
        }
    }

    @JvmStatic
    fun removePointsBlocking(uid: String, newPoints: Int): Boolean {
        return runBlocking {
            try {
                SupabaseManager.getClient().postgrest["profiles"].update({ set("points", newPoints) }) {
                    filter { eq("id", uid) }
                }
                true
            } catch (e: Exception) { false }
        }
    }

    @JvmStatic
    fun updateDoctorProfileBlocking(uid: String, newDocId: String, hospitalName: String): Boolean {
        return runBlocking {
            try {
                val client = SupabaseManager.getClient()
                // 1. Update the Profiles table
                client.postgrest["profiles"].update({
                    set("doctor_id", newDocId)
                    set("clinic_name", hospitalName)
                }) {
                    filter { eq("id", uid) }
                }
                
                // 2. IMPORTANT: Update the Auth User Metadata too, 
                // so the ID is preserved even if the DB is wiped
                client.auth.updateUser {
                    data = buildJsonObject {
                        put("doctor_id", newDocId)
                        put("clinic_name", hospitalName)
                    }
                }
                
                Log.d("SupabaseAuth", "Profile and Metadata updated successfully for $uid")
                true
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Update failed: ${e.message}")
                false
            }
        }
    }

    @JvmStatic
    fun listenForAppointments(userId: String, onNewAppointment: (Appointment) -> Unit) {
        val client = SupabaseManager.getClient()
        val channel = client.realtime.channel("public:appointments")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "appointments" }.onEach {
            val appt = it.decodeRecord<Appointment>()
            if (appt.childId == userId) onNewAppointment(appt)
        }.launchIn(GlobalScope)
        runBlocking { channel.subscribe() }
    }

    suspend fun signUp(name: String, email: String, pass: String, role: String, doctorId: String?): Result<User> {
        return withContext(Dispatchers.IO) {
            val client = SupabaseManager.getClient()
            try {
                // Register with metadata so the SQL trigger can create the profile correctly
                client.auth.signUpWith(Email) {
                    this.email = email
                    password = pass
                    data = buildJsonObject {
                        put("name", name)
                        put("role", role)
                        if (doctorId != null) put("doctor_id", doctorId)
                    }
                }

                val userUid = client.auth.currentUserOrNull()?.id 
                if (userUid == null) {
                    throw Exception("CHECK_EMAIL: Link sent to $email. Please click it and then login.")
                }

                // We try to re-fetch immediately, but we don't crash if it fails
                // because we will fix it during signIn
                try {
                    val profile = client.postgrest["profiles"].select { filter { eq("id", userUid) } }.decodeSingle<User>()
                    Result.success(profile)
                } catch (e: Exception) {
                    Result.success(User(userUid, name, email, pass, role).apply { this.isApproved = (role == "doctor") })
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "SignUp failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun signIn(email: String, pass: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient()
                client.auth.signInWith(Email) { this.email = email; password = pass }
                val currentUser = client.auth.currentUserOrNull() ?: throw Exception("Login failed")
                val userUid = currentUser.id

                // REFRESH PROFILE FROM DATABASE
                try {
                    val profile = client.postgrest["profiles"].select { filter { eq("id", userUid) } }.decodeSingle<User>()
                    profile.password = pass
                    
                    // If Doctor ID is still missing (very rare), we try to recover it
                    if (profile.role == "doctor" && profile.doctorId == null) {
                        Log.d("SupabaseAuth", "Doctor ID missing, attempting recovery...")
                        val meta = currentUser.userMetadata
                        val docId = meta?.get("doctor_id")?.toString()?.replace("\"", "")
                        profile.doctorId = docId
                    }
                    
                    Result.success(profile)
                } catch (e: Exception) {
                    Log.e("SupabaseAuth", "Profile missing during signIn. Recovering...")
                    // AUTO-FIX: Read from metadata
                    val meta = currentUser.userMetadata
                    val roleFromMeta = meta?.get("role")?.toString()?.replace("\"", "") ?: "doctor"
                    val nameFromMeta = meta?.get("name")?.toString()?.replace("\"", "") ?: email.split("@")[0]
                    val docIdFromMeta = meta?.get("doctor_id")?.toString()?.replace("\"", "")
                    val clinicFromMeta = meta?.get("clinic_name")?.toString()?.replace("\"", "")
                    
                    val fallback = User(userUid, nameFromMeta, email, pass, roleFromMeta).apply {
                        this.isApproved = true
                        this.doctorId = docIdFromMeta
                        this.clinicName = clinicFromMeta
                    }
                    
                    // Try one last time to save this to the DB so it's there next time
                    try {
                        val dbMap = mutableMapOf(
                            "id" to userUid, 
                            "name" to nameFromMeta, 
                            "email" to email, 
                            "role" to roleFromMeta, 
                            "is_approved" to true
                        )
                        if (docIdFromMeta != null) dbMap["doctor_id"] = docIdFromMeta
                        if (clinicFromMeta != null) dbMap["clinic_name"] = clinicFromMeta

                        client.postgrest["profiles"].insert(dbMap)
                    } catch (insErr: Exception) {}
                    
                    Result.success(fallback)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "SignIn failed", e)
                Result.failure(e)
            }
        }
    }
}
