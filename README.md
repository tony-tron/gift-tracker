# Gift Tracker — Material 3 Expressive

A cross-device Gift Tracking Progressive Web App crafted with **Android 16's Material 3 Expressive** design language, real-time **Google Firebase (Cloud Firestore + Auth)** synchronization, and adaptive canonical layouts for **Android phones, foldables, and Googlebook laptops**.

## Highlights

- **Material 3 Expressive UI**:
  - 4 Material You Dynamic Color seed palettes (*Festive Terracotta*, *Botanical Sage*, *Sunlit Ochre*, *Pacific Teal*) in Dark, Light, and System Auto modes.
  - Sinusoidal wavy `ExpressiveProgress` budget indicators, shape-morphing connected status button groups (`Bought` · `Arrived` · `Wrapped`), split action buttons, and 35-shape geometric recipient avatars.
- **Adaptive Android Window Classes & Foldables**:
  - **Compact (`< 640px` Phone)**: Thumb-friendly Horizontal Floating Toolbar paired with a Vibrant Primary FAB and spring-animated bottom sheets.
  - **Medium (`640px–1079px` Unfolded Foldables & Tabletop)**: Collapsed Navigation Rail + 2-pane split layouts respecting `horizontal-viewport-segments` foldable hinges.
  - **Expanded (`>= 1080px` Googlebook / Laptop)**: Expanded Navigation Drawer with live item badges + Canonical Supporting-Pane & List-Detail layouts.
- **Privacy-First Architecture**:
  - Zero hardcoded personal data. Clean empty slate on first run, ready for your own custom recipients, events, budgets, clothing sizes, and wishlists.
- **Android Native Share Target**:
  - Share any product URL directly from Chrome, Amazon, Target, Etsy, Cotopaxi, or Hot Topic on Android into **Gift Tracker** to auto-extract the store, title, and price.

## Quick Start

```bash
npm install
npm run dev
```

## Connecting Firebase Cloud Sync (Phone + Googlebook)

1. Create a free Firebase project at [console.firebase.google.com](https://console.firebase.google.com/).
2. Enable **Authentication → Google** (and/or Anonymous) and **Firestore Database** (deploy `firestore.rules` to lock access to your UID).
3. Copy your Web App `firebaseConfig` snippet and either:
   - Paste it directly into the in-app **Sync & Theme** dialog on your phone and Googlebook, **or**
   - Copy `.env.example` to `.env.local` and fill in `NEXT_PUBLIC_FIREBASE_*`.
4. Sign in with Google — your data stays synced in real time across devices with full offline persistence.
