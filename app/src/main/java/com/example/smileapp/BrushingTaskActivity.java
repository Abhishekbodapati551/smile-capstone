package com.example.smileapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smileapp.database.AppDatabase;
import com.example.smileapp.models.BrushingLog;
import com.example.smileapp.models.User;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import android.net.Uri;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrushingTaskActivity extends AppCompatActivity {

    private static final String TAG = "BrushingTaskActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ? 
                Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private PreviewView viewFinder;
    private TextView timerText, recordingStatus;
    private ExtendedFloatingActionButton recordButton;
    private ProgressBar uploadProgress;

    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private ExecutorService cameraExecutor;

    private AppDatabase db;
    private String userId;
    private CountDownTimer countDownTimer;
    private static final long BRUSHING_TIME_MS = 120000; // 2 minutes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brushing_task);

        db = AppDatabase.getInstance(this);
        userId = getIntent().getStringExtra("USER_ID");

        viewFinder = findViewById(R.id.viewFinder);
        timerText = findViewById(R.id.timer_text);
        recordingStatus = findViewById(R.id.recording_status);
        recordButton = findViewById(R.id.record_button);
        uploadProgress = findViewById(R.id.upload_progress);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        recordButton.setOnClickListener(v -> toggleRecording());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleRecording() {
        if (recording != null) {
            stopRecording();
            return;
        }

        startRecording();
    }

    private void startRecording() {
        String name = "SmileApp-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        recording = videoCapture.getOutput()
                .prepareRecording(this, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        recordButton.setText("Finish Brushing");
                        recordButton.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_media_pause));
                        recordingStatus.setText("RECORDING");
                        recordingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
                        startTimer();
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
                        if (!finalizeEvent.hasError()) {
                            saveLog(finalizeEvent.getOutputResults().getOutputUri().toString());
                        } else {
                            if (recording != null) recording.close();
                            recording = null;
                            Log.e(TAG, "Video recording error: " + finalizeEvent.getError());
                        }
                    }
                });
    }

    private void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording = null;
            if (countDownTimer != null) countDownTimer.cancel();
        }
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(BRUSHING_TIME_MS, 1000) {
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }

            public void onFinish() {
                timerText.setText("00:00");
                stopRecording();
            }
        }.start();
    }

    private void saveLog(String localUriStr) {
        uploadProgress.setVisibility(View.VISIBLE);
        Log.d(TAG, "Starting Local Host upload for user: " + userId);
        
        LocalStorageManager storageManager = new LocalStorageManager(this);
        
        // Convert Uri to File for the uploader
        Uri videoUri = Uri.parse(localUriStr);
        String realPath = FileUtils.getPath(this, videoUri);
        
        if (realPath != null) {
            java.io.File videoFile = new java.io.File(realPath);
            
            storageManager.uploadVideo(videoFile, cloudUrl -> {
                if (cloudUrl != null) {
                    Log.d(TAG, "Uploaded to Local Host: " + cloudUrl);
                    
                    // Create the log object with the local server URL
                    BrushingLog log = new BrushingLog(userId, cloudUrl, System.currentTimeMillis());
                    
                    // Save to local Room database ONLY (No internet needed for this part)
                    new Thread(() -> {
                        db.appDao().insertBrushingLog(log);
                        runOnUiThread(() -> {
                            uploadProgress.setVisibility(View.GONE);
                            Toast.makeText(this, "Video saved to Local Host storage!", Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }).start();
                } else {
                    runOnUiThread(() -> {
                        uploadProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "Upload to Local Host failed. Run 'adb reverse tcp:9100 tcp:9100'!", Toast.LENGTH_LONG).show();
                    });
                }
                return null;
            });
        } else {
            uploadProgress.setVisibility(View.GONE);
            Toast.makeText(this, "Could not find video file path.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
