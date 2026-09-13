# Bulk SMS Campaign & Dispatcher

This project is fully configured to compile an APK directly from the extracted ZIP that is guaranteed to run on **all Android devices running Android 10 (API 29) and higher** (as well as backwards compatible down to Android 7.0 / API 24).

---

## How to Compile the APK Directly from the ZIP

### Option A: Using Android Studio (Visual & Recommended)
1. **Extract the ZIP file** to any folder on your computer.
2. Open **Android Studio** and choose **Open** (or `File -> Open`), then select the extracted folder.
3. Wait for Gradle to finish initial sync (~1-2 minutes).
4. To build the installable APK:
   - Click the top menu: **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
   - Or click **Build** -> **Generate Signed Bundle / APK...** if you want your own custom signature.
5. When the build finishes, click the **"locate"** link in the pop-up notification at the bottom-right corner.
   The APK is generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B: Using the Command Line (Terminal / CMD)
In the extracted project directory, run:
- **On Windows:**
  ```cmd
  gradlew.bat assembleDebug
  ```
- **On macOS / Linux:**
  ```bash
  chmod +x gradlew
  ./gradlew assembleDebug
  ```
The universal APK will be immediately ready in:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Android 10+ (API 29+) Compatibility Checklist Built Into the Code

1. **Scoped Storage & File Access (Android 10+)**:
   - Uses the modern Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) for reading `.csv` and `.xlsx` files without needing legacy `READ_EXTERNAL_STORAGE` permissions that break on Android 10 and above.
2. **PendingIntent Mutable/Immutable Flags (Android 12+)**:
   - SMS delivery and sent callbacks adaptively apply `PendingIntent.FLAG_MUTABLE` on API 31+ so carrier delivery extras are received, and `FLAG_IMMUTABLE` on notification intents.
3. **Notification Runtime Permission (Android 13+)**:
   - Dynamically requests `POST_NOTIFICATIONS` at runtime on Android 13+ (API 33+), preventing crashes on modern devices while operating smoothly on Android 10, 11, and 12.
4. **Foreground Service Special Use (Android 14+)**:
   - Declares `FOREGROUND_SERVICE_SPECIAL_USE` alongside `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` so Android 14, 15, and 16 don't kill the batch SMS queue in the background.
5. **Universal Architecture & Hardware**:
   - Pure bytecode compatible with all CPU architectures (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
   - `android.hardware.telephony` is set to `required="false"` so package managers on all phone models, dual-SIM phones, and unlocked global devices will install without restrictions.
6. **Zero-Setup Release & Debug Builds**:
   - Both `assembleDebug` and `assembleRelease` compile immediately without requiring manual keystore configuration beforehand.
