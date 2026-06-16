package com.example.smileapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true)
    @JvmField var id: Int = 0,
    
    @SerialName("child_id")
    @JvmField var childId: String = "",
    
    @SerialName("child_name")
    @JvmField var childName: String = "",
    
    @SerialName("doctor_id")
    @JvmField var doctorId: String = "",
    
    @SerialName("appt_date")
    @JvmField var date: Long = 0,
    
    @JvmField var type: String = "",
    @JvmField var status: String = "upcoming"
) {
    // Constructor for Java compatibility
    constructor(childId: String, childName: String, doctorId: String, date: Long, type: String) : this(
        id = 0,
        childId = childId,
        childName = childName,
        doctorId = doctorId,
        date = date,
        type = type,
        status = "upcoming"
    )
}
