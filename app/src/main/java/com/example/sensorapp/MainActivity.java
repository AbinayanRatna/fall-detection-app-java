package com.example.sensorapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    private MediaPlayer mediaPlayer;
    private TextView xAccValue, yAccValue, zAccValue;
    private TextView xGyroValue, yGyroValue, zGyroValue, phoneNumberView;
    private Vibrator vibrator;
    private Button settingButton;
    private String phoneNumber;
    private String isSmsButtonClicked;
    private Integer sensitivity;
    private static final int PERMISSION_RQST_SEND = 0;
    private float FREE_FALL_THRESHOLD ;
    private static final float free_fall_threshold_max=2f;
    private static final float free_fall_threshold_min=10f;
    private static final int FALL_DETECTION_WINDOW = 1;
    private float GYROSCOPE_THRESHOLD = 10f;
    private static final float GYROSCOPE_THRESHOLD_max = 10f;
    private static final float GYROSCOPE_THRESHOLD_min = 20f;
    private int fallCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initSensors();
    }

    private void initViews() {
        xAccValue = findViewById(R.id.xValue);
        yAccValue = findViewById(R.id.yValue);
        zAccValue = findViewById(R.id.zValue);
        xGyroValue = findViewById(R.id.xValueGyro);
        yGyroValue = findViewById(R.id.yValueGyro);
        zGyroValue = findViewById(R.id.zValueGyro);
        phoneNumberView = findViewById(R.id.phoneTv);
        settingButton = findViewById(R.id.settingsButton);

        Bundle bundle = getIntent().getExtras();
        phoneNumber = bundle != null ? bundle.getString("phone", "") : "";
        isSmsButtonClicked=bundle!= null?bundle.getString("switch",""):"";
        sensitivity=bundle!= null?bundle.getInt("sensitivity",50):null;

        settingButton.setOnClickListener(view -> {
            Intent intent=(new Intent(MainActivity.this, SettingsActivity.class));
            intent.putExtra("sensitivity",sensitivity);
            startActivity(intent);
        });

        if(sensitivity.equals(100)){
            FREE_FALL_THRESHOLD=free_fall_threshold_min;
        }else if(sensitivity.equals(0)){
            FREE_FALL_THRESHOLD=free_fall_threshold_max;
        }else{
            FREE_FALL_THRESHOLD=free_fall_threshold_max-((free_fall_threshold_max-free_fall_threshold_min)*((float)(sensitivity)/100));
            System.out.println("acc "+FREE_FALL_THRESHOLD);
        }

        if(sensitivity.equals(100)){
            GYROSCOPE_THRESHOLD=GYROSCOPE_THRESHOLD_min;
        }else if(sensitivity.equals(0)){
            GYROSCOPE_THRESHOLD=GYROSCOPE_THRESHOLD_max;
        }else{
            GYROSCOPE_THRESHOLD=GYROSCOPE_THRESHOLD_max-((GYROSCOPE_THRESHOLD_max-GYROSCOPE_THRESHOLD_min)*((float)(sensitivity)/100));
            System.out.println("gyr "+GYROSCOPE_THRESHOLD);
        }
        if (!phoneNumber.isEmpty()) {
            phoneNumberView.setText("Emergency contact: " + phoneNumber);
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mediaPlayer = MediaPlayer.create(this, R.raw.sound);
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean fallDetected = false;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                updateAccelerometerValues(event.values);
                fallDetected = isFreeFall(event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_GYROSCOPE:
                updateGyroscopeValues(event.values);
                if (Math.abs(event.values[0]) > GYROSCOPE_THRESHOLD || Math.abs(event.values[1]) > GYROSCOPE_THRESHOLD || Math.abs(event.values[2]) > GYROSCOPE_THRESHOLD) {
                    fallDetected = true;
                }
                break;
        }
        handleFallDetection(fallDetected);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void updateAccelerometerValues(float[] values) {
        xAccValue.setText(String.format("X: %.2f", values[0]));
        yAccValue.setText(String.format("Y: %.2f", values[1]));
        zAccValue.setText(String.format("Z: %.2f", values[2]));
    }

    private void updateGyroscopeValues(float[] values) {
        xGyroValue.setText(String.format("X: %.2f", values[0]));
        yGyroValue.setText(String.format("Y: %.2f", values[1]));
        zGyroValue.setText(String.format("Z: %.2f", values[2]));
    }

    private boolean isFreeFall(float x, float y, float z) {
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        return magnitude < FREE_FALL_THRESHOLD;
    }

    private void handleFallDetection(boolean fallDetected) {
        if (fallDetected) {
            fallCounter++;
        } else {
            fallCounter = 0;
        }
        if (fallCounter >= FALL_DETECTION_WINDOW) {
            triggerAlert();
            fallCounter = 0; // reset counter after detecting fall
        }
    }

    private void triggerAlert() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(2000); // Vibrate for 2000 milliseconds
        }
        if (mediaPlayer != null) {
            mediaPlayer.start(); // Play the fall sound
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            if(isSmsButtonClicked.equals("ON")) {
                sendSMSMessage(phoneNumber, "Phone dropped");
            }

        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                Toast.makeText(this, "SMS permission is needed to alert in case of fall", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_RQST_SEND);
            }
        }
    }

    private void sendSMSMessage(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        Toast.makeText(getApplicationContext(), "Emergency SMS sent.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_RQST_SEND && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendSMSMessage(phoneNumber, "Phone dropped");
        } else {
            Toast.makeText(getApplicationContext(), "SMS permission denied.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
