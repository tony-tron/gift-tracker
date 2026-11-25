# Gift Tracker — Native Android App

Native Android client for **Gift Tracker**, built with **Kotlin**, **Jetpack Compose**, **Jetpack Glance (`1.2.0`)**, **Credential Manager**, and **Firebase Cloud Firestore** — sharing 100% real-time database & JSON backup compatibility with [`/web`](../web).

## Key Features & Latest Android APIs

- **Material 3 Expressive (`androidx.compose.material3:material3:1.5.0`)**:
  - `MaterialExpressiveTheme` + `MotionScheme.expressive()` spring physics across transitions and progress bars.
  - **Android Wallpaper Dynamic Color** (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) plus the 4 custom Material You seed palettes (*Festive Terracotta*, *Botanical Sage*, *Sunlit Ochre*, *Pacific Teal*).
  - **`HorizontalFloatingToolbar`** paired with **`VibrantFloatingActionButton`** on compact phone screens, automatically switching to **`NavigationRail`** + 2-pane split layouts on foldables, tablets, and Googlebooks.
  - **`LinearWavyProgressIndicator`** sinusoidal budget progress bars, **`ButtonGroupDefaults`** shape-morphing connected status toggles (`Bought` · `Arrived` · `Wrapped`) with system haptics, **`SplitButtonLayout`** for 1-tap occasion assignment, and **`MaterialShapes`** (`Cookie6Sided`, `Clover4Leaf`, `Arch`, `Pill`, `Flower`) recipient avatars.
- **Material 3 Jetpack Glance Home Screen Widget (`androidx.glance:glance-appwidget:1.2.0`)**:
  - Responsive home screen widget (`2x2`, `4x2`, `4x3`) themed with `GlanceTheme` Material You dynamic colors.
  - Displays active occasion budget progress, `to buy` & `to wrap` counters, top remaining checklist items, and a **`+ Gift`** quick-launch button that opens the composer sheet directly.
- **Native Android OS Integrations**:
  - **Share Target (`ACTION_SEND`)**: Share product links straight from Chrome, Amazon, Target, Etsy, etc. into Gift Tracker to auto-scrape store, title, and price natively.
  - **Storage Access Framework (SAF)**: Export & import JSON backups directly to/from Google Drive or device storage (compatible with Web App JSON backups).
  - **Edge-to-Edge & Predictive Back**: Full Android 15/16/17 edge-to-edge insets, themed monochrome adaptive launcher icon, and predictive back gestures.

## Building Locally

No `google-services.json` is required to build!

```bash
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## Connecting Firebase Cloud Sync

You can connect Firebase in **three ways**:

1. **Pre-Wired APKs via `google-services.json` (Zero-Config on New Phones)**:
   - Register package name `com.twintitanium.gifttracker` (and your signing key SHA-1 / SHA-256 for Google Sign-In) in Firebase Console → **Project Settings → Your apps**.
   - Place `google-services.json` in `android/app/google-services.json` locally (git-ignored) **or** save its contents as a `GOOGLE_SERVICES_JSON` (or `GOOGLE_SERVICES_JSON_BASE64`) GitHub Secret so CI release APKs sign in with Google immediately on any new phone.
2. **1-Scan QR Code / Deep-Link Pairing (`gifttracker://pair`)**:
   - From any already-connected Web or Android device, open **Settings → Cloud Sync → Pair Device / Invite** and scan the QR code or open the shared pairing link (friends who sign in with their own Google account get a separate, private workspace).
3. **Zero-Rebuild Runtime Paste**:
   - Tap **Settings → Cloud Sync**, and paste a pairing link, Web `firebaseConfig` snippet, or `google-services.json` content.

## Local Installation Options (2026–2027 Guide)

1. **Direct APK Install (Fastest right now)**:
   - Download `gift-tracker-release.apk` (from GitHub Releases, GitHub Actions artifacts, or Drive) on your Android device and tap to install.
   - Or over USB / Wireless Debugging (`adb install -r app-release.apk`), which bypasses Play Protect unverified-developer prompts permanently.
2. **Android Developer Console — Free "Limited Distribution Account" (Recommended before Jan 2027)**:
   - Google launched free **Limited Distribution Accounts** in the [Android Developer Console](https://developer.android.com/developer-verification/guides/limited-distribution) for hobbyists and personal apps (\$0 fee, no government ID required).
   - Register `com.twintitanium.gifttracker` with your signing key SHA-256 and authorize up to **50 personal/family devices** via a QR-code/link handshake handled by Google Play Services so local APK installs never hit the 2027 unverified-app 24-hour wait.
3. **Google Play Store "Internal App Sharing" / Firebase App Distribution**:
   - **Firebase App Distribution** (free in your existing Firebase project): Upload the APK and install over-the-air via Firebase App Tester.
   - **Play Store Internal App Sharing** (if you already own a \$25 Play Console account): Enable *Settings → About → tap Play Store version 7 times → General → Developer options → Internal app sharing* inside the Google Play Store app, upload your APK/AAB to `play.google.com/console/internal-app-sharing`, and install directly through the Google Play Store UI via a private link without Play review.
