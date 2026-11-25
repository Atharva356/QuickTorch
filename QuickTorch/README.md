# QuickTorch (Android)

An Android flashlight utility triggered by **Power Button Triple-Click**, **Volume Up + Down Combo**, or a **Double-Chop Shake Gesture**, with anti-mistake sensors and full background reliability.

## Features
- **Power Button Triple-Click:** Detects 3 rapid screen ON/OFF toggles within a customizable window (0.8s–2.5s).
- **Volume Up + Down Combo:** Uses an Accessibility Service to toggle cleanly without colliding with Emergency SOS.
- **Double-Chop / Shake Gesture:** Accelerometer-based chop gesture with Firm / Medium / Sensitive thresholds.
- **Pocket Protection:** Proximity sensor blocks accidental triggers inside pockets or bags.
- **Lock-Screen / Screen-Off Only Mode:** Prevents misfires while gaming or using apps.
- **Haptic Confirmation:** Distinct vibration patterns for ON, OFF, and Blocked states.
- **Modes & Brightness:** Steady, adjustable Strobe (1–10 Hz), SOS Morse blink, and Android 13+ variable torch brightness.
- **Auto-Off Timer:** Off, 1m, 2m, 5m, 10m, or 15m.
- **Background Reliability:** Android 14/15 `specialUse` Foreground Service, Auto-Start on Boot, Low-Battery Cutoff, and one-tap Battery Optimization exemption.

## Get the Installable APK (Zero Local Setup via GitHub Actions)
1. Create a new GitHub repository (public or private) and upload/push all files from this folder (including `.github/workflows/build-apk.yml`).
2. Open the **Actions** tab in your GitHub repository.
3. Click the **Build QuickTorch APK** workflow run (wait ~2 minutes for the green checkmark).
4. Scroll to **Artifacts** and download **`QuickTorch-APK`**, unzip it, and install `QuickTorch-v1.0.0.apk` on your Android phone.

## Or Build Locally in Android Studio
1. Open this folder in Android Studio.
2. Let Gradle sync (JDK 17, AGP 8.7.3, compileSdk 35).
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
