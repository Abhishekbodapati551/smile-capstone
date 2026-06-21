package com.example.smileapp

import android.content.Context
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.PutObjectRequest
import java.io.File
import kotlin.concurrent.thread

class LocalStorageManager(context: Context) {

    // Using 127.0.0.1 with 'adb reverse tcp:9100 tcp:9100' for the most stable connection
    private val endPoint = "http://127.0.0.1:9100"
    private val bucketName = "smile-videos"
    private val accessKey = "admin"
    private val secretKey = "password123"

    private val s3Client: AmazonS3Client by lazy {
        val credentials = BasicAWSCredentials(accessKey, secretKey)
        val client = AmazonS3Client(credentials)
        client.setEndpoint(endPoint)
        client
    }

    fun uploadVideo(videoFile: File, onComplete: (String?) -> Unit) {
        thread {
            try {
                val fileName = "video_${System.currentTimeMillis()}.mp4"
                val request = PutObjectRequest(bucketName, fileName, videoFile)
                
                // Make the video accessible locally
                request.withCannedAcl(CannedAccessControlList.PublicRead)
                
                s3Client.putObject(request)
                
                val videoUrl = "$endPoint/$bucketName/$fileName"
                Log.d("LocalStorage", "Upload Success: $videoUrl")
                onComplete(videoUrl)
            } catch (e: Exception) {
                Log.e("LocalStorage", "Upload Failed", e)
                onComplete(null)
            }
        }
    }
}
