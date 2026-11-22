# Gift Tracker — Native Android Client

This directory will house the native **Android (Kotlin + Jetpack Compose)** client for Gift Tracker, built with Android 16's **Material 3 Expressive** components (`FloatingToolbar`, `ButtonGroup`, `SplitButton`, `WavyProgressIndicator`, `MaterialShapes`, and `MotionScheme.expressive()`).

## Shared Firebase Backend

Both `/android` and [`/web`](../web) connect to the same **Firebase Cloud Firestore** schema (`users/{uid}/{people|events|gifts|wishes}`) governed by the root [`firestore.rules`](../firestore.rules) and [`firestore.indexes.json`](../firestore.indexes.json).

### Setup Notes (When Initializing Android Studio Project)

1. Register your Android package name in Firebase Console → **Project Settings → Your apps**.
2. Download `google-services.json` into `android/app/google-services.json` (git-ignored by default so credentials are never committed).
