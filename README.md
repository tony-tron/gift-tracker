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
- **Unified Real-Time Cloud Sync & Collaborative Wishlist Sharing**:
  - Both apps share the exact same Firestore workspace schema (`workspaces/{uid}/{people|events|gifts|wishes|shared_links}`), 1-scan QR/link device pairing, and interchangeable JSON backup format.
  - **Multi-Group Live Wishlist Checklists (`/w/[shareId]`)**: Create separate unlisted checklist links for different groups (e.g., *Family*, *Friends*) where gift-givers claim items, mark purchases, and leave group notes in real time — while Firestore security rules strictly hide claim statuses from the wishlist owner.

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
2. Enable **Authentication → Google** (and/or Anonymous) and **Firestore Database**, then deploy `firestore.rules` to scope workspaces per Google UID:
   ```bash
   firebase use --add                  # Select your Firebase project (great for forks!)
   firebase deploy --only firestore:rules
   ```
3. **Connect once, pair everywhere**:
   - **Pre-Wired Web Host**: Set `NEXT_PUBLIC_FIREBASE_*` in `.env.local` or Firebase App Hosting / Vercel so every browser is zero-config.
   - **Pre-Wired Personal Android APKs**: Add `GOOGLE_SERVICES_JSON` (or `GOOGLE_SERVICES_JSON_BASE64`) as a GitHub Repository Secret so CI-built APKs sign in with Google out of the box on any new phone — while forks without the secret still build cleanly.
   - **1-Scan QR / Pairing Link**: In **Settings → Cloud Sync → Pair Device / Invite**, scan the QR code or share the `?pair=...` / `gifttracker://pair` link to configure new devices or invite friends (each Google account gets its own isolated workspace).
   - **Manual Paste**: Or paste a pairing link, `firebaseConfig`, or `google-services.json` directly into **Settings**.
4. If deploying the Web App via **Firebase App Hosting**, set the backend **App root directory** to `web`.
