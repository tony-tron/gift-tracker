# Gift Tracker — Material 3 Expressive

A cross-device Gift Tracking suite built around **Android 16's Material 3 Expressive** design language and real-time **Google Firebase (Cloud Firestore + Auth)** synchronization.

## Repository Structure

```text
gift-tracker/
├── web/                      # Next.js 16 Progressive Web App (Desktop, Android, iOS)
├── android/                  # Native Android Client (Kotlin + Jetpack Compose)
├── firestore.rules           # Shared Cloud Firestore security rules (UID-scoped)
├── firestore.indexes.json    # Shared Cloud Firestore composite indexes
└── firebase.json             # Firebase CLI configuration
```

## Highlights

- **Material 3 Expressive UI**:
  - 4 Material You Dynamic Color seed palettes (*Festive Terracotta*, *Botanical Sage*, *Sunlit Ochre*, *Pacific Teal*) in Dark, Light, and System modes.
  - Sinusoidal wavy `ExpressiveProgress` budget indicators, shape-morphing connected status button groups (`Bought` · `Arrived` · `Wrapped`), split action buttons, and geometric recipient avatars.
- **Auto-Adaptive Responsive Layouts**:
  - **Compact (`< 640px` Phone)**: Thumb-friendly Horizontal Floating Toolbar paired with a Vibrant Primary FAB and spring-animated bottom sheets.
  - **Medium (`640px–1079px` Foldables & Tablets)**: Collapsed Navigation Rail + 2-pane split layouts respecting `horizontal-viewport-segments` foldable hinges.
  - **Expanded (`>= 1080px` Laptop & Desktop)**: Expanded Navigation Drawer with live item badges + Canonical Supporting-Pane & List-Detail layouts.
- **Privacy-First Architecture**:
  - Zero hardcoded personal data. Clean empty slate on first run, ready for your own custom recipients, events, budgets, clothing sizes, and wishlists.
- **Native Share Target**:
  - Share product URLs directly from Chrome, Amazon, Target, Etsy, Cotopaxi, or Hot Topic into **Gift Tracker** to auto-extract store, title, and price.

## Quick Start (Web App)

```bash
cd web
npm install
npm run dev
```

## Connecting Firebase Cloud Sync

1. Create a free Firebase project at [console.firebase.google.com](https://console.firebase.google.com/).
2. Enable **Authentication → Google** (and/or Anonymous) and **Firestore Database** (deploy root `firestore.rules` to lock access to your UID).
3. Copy your Web App `firebaseConfig` snippet and either:
   - Open **Settings** (gear icon) in the app and paste it directly, **or**
   - Copy `web/.env.example` to `web/.env.local` and fill in `NEXT_PUBLIC_FIREBASE_*`.
4. If deploying via **Firebase App Hosting**, set the backend **App root directory** to `web`.
