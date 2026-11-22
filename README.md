# Gift Tracker — Material 3 Expressive (Web + Native Android)

A cross-device Gift Tracking suite built around **Android 16's Material 3 Expressive** design language, **Jetpack Glance Home Screen Widgets**, and real-time **Google Firebase (Cloud Firestore + Auth)** synchronization.

## Repository Structure

```text
gift-tracker/
├── android/                  # Native Android App (Kotlin, Jetpack Compose M3 Expressive 1.5, Glance Widget)
├── web/                      # Next.js 16 Progressive Web App (Desktop, Android, iOS)
├── firestore.rules           # Shared Cloud Firestore security rules (UID-scoped)
├── firestore.indexes.json    # Shared Cloud Firestore composite indexes
└── firebase.json             # Firebase CLI configuration
```

## Highlights

- **Native Android App (`/android`)**:
  - First-party **Jetpack Compose Material 3 Expressive (`1.5.0`)**: `MaterialExpressiveTheme`, `MotionScheme.expressive()` spring physics, Android Wallpaper Dynamic Color, `HorizontalFloatingToolbar` + `VibrantFloatingActionButton`, `LinearWavyProgressIndicator`, connected `ButtonGroup` status toggles with system haptics, `SplitButtonLayout`, and `MaterialShapes` avatars.
  - **Material 3 Home Screen Widget (`androidx.glance`)**: Resizable `GlanceTheme` widget showing active occasion budget progress, `to buy` / `to wrap` counts, remaining checklist items, and a `+ Gift` quick-add button.
  - **Android OS Integrations**: Native Share Sheet target (`ACTION_SEND`), Storage Access Framework JSON Export/Import, Credential Manager Google Sign-In, Edge-to-Edge, and Predictive Back.
- **Progressive Web App (`/web`)**:
  - Auto-adaptive Material 3 Expressive web interface for laptops, desktops, foldables, and mobile browsers.
  - Zero hardcoded personal data — clean privacy-first architecture out of the box.
- **Unified Real-Time Cloud Sync**:
  - Both apps share the exact same Firestore workspace schema (`workspaces/{uid}/{people|events|gifts|wishes}`) and interchangeable JSON backup format.

## Quick Start

### Native Android App
```bash
cd android
./gradlew assembleRelease
# Installable APK: android/app/build/outputs/apk/release/app-release.apk
```
*(Pre-built APKs are also attached to GitHub Releases and every GitHub Actions CI run!)*

### Web App
```bash
cd web
npm install
npm run dev
```

## Connecting Firebase Cloud Sync

1. Create a free Firebase project at [console.firebase.google.com](https://console.firebase.google.com/).
2. Enable **Authentication → Google** (and/or Anonymous) and **Firestore Database** (deploy root `firestore.rules` to lock access to your UID).
3. In **Settings** (gear icon) on either Android or Web, paste your `firebaseConfig` snippet (or place `google-services.json` in `android/app/` and `.env.local` in `web/`).
4. If deploying the Web App via **Firebase App Hosting**, set the backend **App root directory** to `web`.
