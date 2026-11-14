package org.geoalert.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.Locale;

/**
 * GeoSensorEngine: High-reliability multi-modal sensor monitoring engine.
 *
 * Implements real-time analysis for:
 * 1. Seismic / ground vibration detection
 * 2. Fall and rapid displacement impact detection
 * 3. Gravitational tilt deviation monitoring
 * 4. Proximity / debris burial occlusion sensing
 */
public class GeoSensorEngine implements SensorEventListener {

    private static final String TAG = "GeoSensorEngine";

    // Sensor thresholds
    private static final double FREE_FALL_THRESHOLD_MS2 = 5.0;
    private static final double IMPACT_THRESHOLD_MS2 = 16.5;
    private static final double SEISMIC_DEFAULT_THRESHOLD_MS2 = 13.0;
    private static final double TILT_ALERT_THRESHOLD_DEG = 8.0;

    // Timing parameters
    private static final long IMPACT_WINDOW_MS = 1200; // time window between free fall and impact
    private static final long SEISMIC_COOLDOWN_MS = 5000;

    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor proximity;
    private final Sensor gyroscope;

    private OnHazardDetectedListener listener;
    private TextToSpeech textToSpeech;
    private Ringtone alarmRingtone;

    // State tracking
    private boolean isMonitoring = false;
    private boolean freeFallDetected = false;
    private long freeFallTimestamp = 0;
    private long lastSeismicTimestamp = 0;

    // Baseline gravity reference for tilt calculations
    private double refGravX = 0.0;
    private double refGravY = 0.0;
    private double refGravZ = 9.81;
    private boolean hasBaseline = false;

    public interface OnHazardDetectedListener {
        void onSeismicVibration(double maxAxisVal, double totalMagnitude);
        void onFallImpact(double freeFallMag, double impactMag);
        void onTiltDisplacement(double tiltDegrees);
        void onBurialOcclusion(boolean isBuried);
    }

    public GeoSensorEngine(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        initAudioAndTTS();
    }

    private void initAudioAndTTS() {
        try {
            Uri alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            this.alarmRingtone = RingtoneManager.getRingtone(context, alertUri);
            if (this.alarmRingtone != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                this.alarmRingtone.setAudioAttributes(audioAttributes);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing alarm ringtone", e);
        }

        try {
            this.textToSpeech = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                    textToSpeech.setLanguage(Locale.ENGLISH);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TextToSpeech", e);
        }
    }

    public void setHazardListener(OnHazardDetectedListener listener) {
        this.listener = listener;
    }

    public synchronized void startMonitoring() {
        if (isMonitoring) return;

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (proximity != null) {
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }

        isMonitoring = true;
        Log.i(TAG, "GeoSensorEngine monitoring started.");
    }

    public synchronized void stopMonitoring() {
        if (!isMonitoring) return;

        sensorManager.unregisterListener(this);
        isMonitoring = false;
        stopAlarm();
        Log.i(TAG, "GeoSensorEngine monitoring stopped.");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometerData(event.values[0], event.values[1], event.values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            processProximityData(event.values[0], event.sensor.getMaximumRange());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    private void processAccelerometerData(float ax, float ay, float az) {
        double magnitude = Math.sqrt(ax * ax + ay * ay + az * az);
        long now = SystemClock.elapsedRealtime();

        // 1. Initialize or track gravity baseline reference
        if (!hasBaseline) {
            refGravX = ax;
            refGravY = ay;
            refGravZ = az;
            hasBaseline = true;
        } else {
            // Low-pass exponential moving average filter for gravity baseline
            double alpha = 0.05;
            refGravX = alpha * ax + (1.0 - alpha) * refGravX;
            refGravY = alpha * ay + (1.0 - alpha) * refGravY;
            refGravZ = alpha * az + (1.0 - alpha) * refGravZ;
        }

        // 2. Gravitational Tilt Angle Calculation
        double refMag = Math.sqrt(refGravX * refGravX + refGravY * refGravY + refGravZ * refGravZ);
        if (refMag > 0.001 && magnitude > 0.001) {
            double dot = (ax * refGravX + ay * refGravY + az * refGravZ);
            double cosTheta = Math.max(-1.0, Math.min(1.0, dot / (magnitude * refMag)));
            double tiltDegrees = Math.toDegrees(Math.acos(cosTheta));

            if (tiltDegrees >= TILT_ALERT_THRESHOLD_DEG && listener != null) {
                listener.onTiltDisplacement(tiltDegrees);
            }
        }

        // 3. Fall & Sudden Displacement Detection (Two-Phase State Machine)
        if (magnitude <= FREE_FALL_THRESHOLD_MS2) {
            freeFallDetected = true;
            freeFallTimestamp = now;
        }

        if (freeFallDetected) {
            if (now - freeFallTimestamp <= IMPACT_WINDOW_MS) {
                if (magnitude >= IMPACT_THRESHOLD_MS2) {
                    freeFallDetected = false;
                    if (listener != null) {
                        listener.onFallImpact(FREE_FALL_THRESHOLD_MS2, magnitude);
                    }
                }
            } else {
                // Window expired without impact
                freeFallDetected = false;
            }
        }

        // 4. Seismic / Ground Vibration Detection
        double maxAxis = Math.max(Math.abs(ax), Math.max(Math.abs(ay), Math.abs(az)));
        if (maxAxis >= SEISMIC_DEFAULT_THRESHOLD_MS2) {
            if (now - lastSeismicTimestamp >= SEISMIC_COOLDOWN_MS) {
                lastSeismicTimestamp = now;
                if (listener != null) {
                    listener.onSeismicVibration(maxAxis, magnitude);
                }
            }
        }
    }

    private void processProximityData(float distance, float maxRange) {
        boolean isBuried = (distance < Math.min(maxRange, 1.0f));
        if (listener != null) {
            listener.onBurialOcclusion(isBuried);
        }
    }

    // Audio and Alert Utilities
    public void playAlarm() {
        try {
            if (alarmRingtone != null && !alarmRingtone.isPlaying()) {
                alarmRingtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alarm", e);
        }
    }

    public void stopAlarm() {
        try {
            if (alarmRingtone != null && alarmRingtone.isPlaying()) {
                alarmRingtone.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop alarm", e);
        }
    }

    public void speakWarning(String text) {
        try {
            if (textToSpeech != null) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GeoAlertTTS");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to speak TTS warning", e);
        }
    }

    public boolean sendEmergencySMS(String recipientNumber, String alertType, Location location) {
        if (recipientNumber == null || recipientNumber.trim().isEmpty()) {
            return false;
        }
        try {
            SmsManager smsManager = SmsManager.getDefault();
            String locString = "Location unavailable";
            if (location != null) {
                locString = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                        location.getLatitude(), location.getLongitude());
            }

            String message = String.format(Locale.US,
                    "[GeoAlert Emergency] %s detected! Map: %s",
                    alertType, locString);

            smsManager.sendTextMessage(recipientNumber, null, message, null, null);
            Log.i(TAG, "Emergency SMS dispatched to " + recipientNumber);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send emergency SMS", e);
            return false;
        }
    }

    public void destroy() {
        stopMonitoring();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
