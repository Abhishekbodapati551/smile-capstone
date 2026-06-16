package com.example.smileapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "brushing_logs")
data class BrushingLog(
    @PrimaryKey
    @SerialName("id")
    @JvmField var id: Int = 0,
    
    @SerialName("child_id")
    @JvmField var childId: String = "",
    
    @SerialName("child_name")
    @JvmField var childName: String? = null,
    
    @SerialName("doctor_id")
    @JvmField var doctorId: String? = null,
    
    @SerialName("video_url")
    @JvmField var videoUri: String = "",
    
    @SerialName("created_at")
    @JvmField var timestamp: Long = System.currentTimeMillis(),
    
    @JvmField var approved: Boolean = false,
    
    @SerialName("doctor_feedback")
    @JvmField var doctorFeedback: String? = null,
    
    @SerialName("firestore_id")
    @JvmField var firestoreId: String? = null
) {
    // Constructor for Java compatibility
    constructor(childId: String, videoUri: String, timestamp: Long) : this(
        id = 0,
        childId = childId,
        videoUri = videoUri,
        timestamp = timestamp
    )
}
