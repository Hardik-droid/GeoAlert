# GeoAlert: Real-Time Geological Hazard & Emergency Sensing System

**GeoAlert** is an Android emergency response and hazard detection platform designed to detect life-threatening geological and kinematic events in real time. It monitors ground motion, seismic vibrations, sudden falls, and debris burial using on-device physical sensors, immediately sounding audio alarms, dispatching emergency SMS alerts with GPS coordinates, and logging forensic telemetry.

---

## Key Capabilities

### 1. Seismic & Ground Vibration Monitoring
- Continuously samples the 3-axis accelerometer at high frequency.
- Computes total vector magnitude $|a| = \sqrt{a_x^2 + a_y^2 + a_z^2}$ and axis-wise peak accelerations.
- Detects anomalous seismic vibrations exceeding calibrated thresholds.
- Correlates multi-device event records within a geographic radius via cloud telemetry.

### 2. Fall & Impact Detection
- Implements a two-phase state machine:
  1. **Free Fall Phase**: detects weightlessness / free fall when vector magnitude drops below $5.0 \text{ m/s}^2$.
  2. **Impact Phase**: validates sudden decelerative collision when vector magnitude spikes above $16.5 \text{ m/s}^2$ within a $1,200 \text{ ms}$ window.
- Initiates an automated confirmation countdown timer with audible warning to prevent false positives before escalation.

### 3. Gravitational Tilt & Slope Displacement
- Analyzes low-pass filtered gravity baseline vectors:
  $$\theta = \arccos\left(\frac{\mathbf{a} \cdot \mathbf{g}_{\text{ref}}}{|\mathbf{a}| \cdot |\mathbf{g}_{\text{ref}}|}\right)$$
- Detects angular ground subsidence and slope displacement exceeding safe operating boundaries.

### 4. Proximity & Debris Burial Detection
- Monitors optical proximity sensors to detect debris burial, mudflow engulfment, or structural collapse entrapment.

### 5. Multi-Channel Emergency Dispatch
- **Automated SMS Dispatch**: sends immediate distress messages to designated emergency contacts containing live Google Maps location URLs (`https://maps.google.com/?q=lat,lng`) and timestamps.
- **Siren & Text-to-Speech (TTS)**: plays audible siren alarms via Android `RingtoneManager` and speaks voice instructions via `TextToSpeech`.
- **Dual Location Engine**: combines GPS and Cellular/Wi-Fi Network providers with automatic fallback to last known coordinates.

### 6. Dual-Tier Data Logging
- **Local SQLite Storage**: logs every vibration, fall, and incident locally on-device for air-gapped operation.
- **Cloud Synchronization**: synchronizes event records and incident photos with Firebase Realtime Database and Cloud Storage.

---

## Architecture & Project Structure

```
GeoAlert/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions, activities, hardware features
│   │   ├── java/org/geoalert/app/
│   │   │   ├── GeoSensorEngine.java     # Multi-modal sensor monitoring engine
│   │   │   ├── MainActivity.java        # Registration & authentication entry point
│   │   │   ├── Menu.java                # Main dashboard & active monitoring loop
│   │   │   ├── PossibleFall.java        # Fall confirmation state machine
│   │   │   ├── FallDetected.java        # Escalation & emergency dispatch UI
│   │   │   ├── EarthquakeRes.java       # Seismic event details & geographic map
│   │   │   ├── EarthquakeList.java      # Historical seismic log viewer
│   │   │   ├── FireReport.java          # Camera incident reporting with photo upload
│   │   │   ├── SettingsMenu.java        # Threshold, language & contact configuration
│   │   │   └── dbHelper.java            # Local SQLite schema & CRUD operations
│   │   └── res/                         # UI layouts, colors, vector drawables
│   ├── build.gradle                     # Android application dependencies & SDK config
│   └── google-services.json             # Firebase configuration template
├── build.gradle                         # Top-level Gradle build configuration
└── settings.gradle                      # Project module definitions (rootProject: GeoAlert)
```

---

## Hardware Permissions Required

GeoAlert utilizes standard Android hardware sensors and telephony:
- `android.permission.ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: High-accuracy emergency dispatch coordinates.
- `android.permission.SEND_SMS`: Emergency SMS alert transmission.
- `android.permission.CAMERA` & `READ_EXTERNAL_STORAGE`: Photographic incident documentation.
- `android.permission.INTERNET` & `ACCESS_NETWORK_STATE`: Telemetry sync and remote alert verification.
- `android.permission.MODIFY_AUDIO_SETTINGS`: Emergency siren playback override.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK Platform 29+ (Min SDK 23, Target SDK 29+)
- Java Development Kit (JDK 8 or JDK 11)

### Build Instructions

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Hardik-droid/GeoAlert.git
   cd GeoAlert
   ```

2. **Configure Firebase (Optional)**:
   - To enable cloud sync, replace `app/google-services.json` with your Firebase project credentials.
   - For standalone local operation, the app functions offline utilizing local SQLite storage.

3. **Build via Gradle**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on physical test device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## License & Attribution

Based on initial prototypes by [GP2Engine](https://github.com/GP2Engine/SmartAlert) (Tasos Kollias). Rebranded, refactored, and modernized under **GeoAlert** for advanced geological hazard monitoring, gravitational tilt tracking, and automated emergency response.
