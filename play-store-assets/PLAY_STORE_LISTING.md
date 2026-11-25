# Google Play Store Listing & Closed Testing Guide — Gift Tracker

Everything below is tailored to **`com.twintitanium.gifttracker`** (`versionCode = 8`, `versionName = "1.1.4"`).

---

## 1. Privacy Policy & Where to Host It

A Privacy Policy page has been added directly to your Next.js web app at:
- **Source:** [`web/src/app/privacy/page.tsx`](../web/src/app/privacy/page.tsx)
- **Route:** `/privacy`

### Hosting on Firebase (Recommended)
Your repo is already configured for Firebase project **`gift-tracker-3018d`** (`.firebaserc` + `firebase.json` + `web/apphosting.yaml`):

1. **Before deploying**, verify `CONTACT_EMAIL` in `web/src/app/privacy/page.tsx` is set to `twintitaniumteam@gmail.com` (and use that same email in your Google Play Console listing).
2. **Deploy your web app** via **Firebase App Hosting** (which serves the Next.js app from `web/`):
   - Once deployed, your Privacy Policy URL will be:
     `https://<your-apphosting-domain>/privacy`
   - If you also use Firebase Hosting (`gift-tracker-3018d.web.app`) rewriting to App Hosting, it will be live at:
     `https://gift-tracker-3018d.web.app/privacy`
3. Paste that live HTTPS URL into:
   - **Play Console → App content → Privacy policy**
   - **Play Console → Store settings** (optional website field)

> **Tip:** Google Play reviewers test that the Privacy Policy URL loads publicly with HTTP 200 without requiring a login.

---

## 2. Store Listing Copy

### App Name (max 30 characters)
`Gift Tracker` *(12 chars)*

### Short Description (max 80 characters)
`Track gift ideas, event budgets, & live shared wishlists with friends & family.` *(79 chars)*

### Full Description (max 4,000 characters)
```text
Gift Tracker helps you organize gift ideas, manage occasion budgets, and coordinate live wishlists with friends and family — without spoiling the surprise.

KEY FEATURES

• Occasions & Budget Tracking
Create events like Birthdays, Holidays, or Anniversaries with dedicated budgets. Track every gift through Idea, Bought, and Wrapped stages with a clear spending progress bar.

• People & Gift Ideas
Keep a running list of gift ideas, store links, prices, and notes for everyone you shop for all year round.

• Collaborative Shared Wishlists
Generate unlisted live wishlist links for specific groups (like Family or Friends). Recipients can claim gifts and leave notes in real time — while claim statuses stay hidden from the wishlist owner.

• Material 3 Home Screen Widget
Pin the Gift Tracker Checklist widget to your Android home screen to see your active occasion budget, To Buy / To Wrap counts, and a "+ Gift" quick-add button at a glance.

• Share Directly from Any Store
Use the Android Share Sheet from Amazon, Target, Etsy, Chrome, or any browser to send a product link straight into Gift Tracker with automatic title and store detection.

• Privacy-First & Optional Cloud Sync
Your data stays local on your device by default. Want to sync across your phone and the web? Sign in with Google to enable real-time Firebase Cloud Sync, or pair devices in one scan with a QR code. Export or import a full JSON backup anytime.
```

### Categorization & Contact Details
- **App or game:** App
- **Category:** Lifestyle *(or Productivity)*
- **Tags:** Shopping, Organization, Productivity
- **Email address:** *(Your developer support email — same as in `privacy/page.tsx`)*

---

## 3. Graphic Assets (Ready to Upload)

All required Play Store graphics are in [`play-store-assets/`](./) at exact Play Console pixel specifications (8-bit RGB PNG, no alpha channel):

| Asset | File | Dimensions | Play Console Requirement |
| :--- | :--- | :--- | :--- |
| **App Icon** | `icon-512x512.png` | `512 × 512` | 512×512 PNG (32-bit, max 1024 KB) |
| **Feature Graphic** | `feature-graphic-1024x500.png` | `1024 × 500` | 1024×500 PNG or JPEG (max 15 MB) |
| **Phone Screenshot 1** | `screenshot-1-occasions.png` | `1080 × 1920` | Min 2 screenshots, 9:16 or 16:9 |
| **Phone Screenshot 2** | `screenshot-2-shared-wishlist.png` | `1080 × 1920` | Min 2 screenshots, 9:16 or 16:9 |
| **Phone Screenshot 3** | `screenshot-3-widget.png` | `1080 × 1920` | Min 2 screenshots, 9:16 or 16:9 |

> **Note:** The 3 phone screenshots are high-resolution promotional mockups so you can submit immediately. You can replace them with direct screenshots from your phone or Android Studio emulator anytime.

---

## 4. Play Console "App Content" Checklist

### A. Data Safety Form
Based on the actual Android codebase (`AndroidManifest.xml`, `FirebaseSyncManager.kt`, `LocalGiftStorage.kt`, `LinkScraper.kt`):

1. **Does your app collect or share any of the required user data types?** → **Yes** *(because Cloud Sync & Shared Wishlists transmit user-entered data & Google Sign-In credentials to Google Firebase when enabled)*.
2. **Is all of the user data collected by your app encrypted in transit?** → **Yes** (Firebase / HTTPS).
3. **Do you provide a way for users to request that their data is deleted?** → **Yes** (provide the Privacy Policy / email deletion instructions from `/privacy`).
4. **Data Types to declare:**
   - **Personal info → Name & Email address** (collected *only* if user signs in with Google for Cloud Sync; used for **App functionality** & **Account management**; **Optional**; **Not shared** with third parties).
   - **Personal info → User IDs** (Firebase UID for workspace isolation; **App functionality**; **Optional**; **Not shared**).
   - **App info and performance / Device IDs / Ads:** **No** (no analytics SDKs, no Crashlytics, no AdMob).
   - *(Note on user-created gift lists/wishlists: if you want to be extra thorough, you can also declare **App activity → In-app search/other user-generated content** or **Personal info → Other info** for synced wishlist items, marked as **Optional** and **App functionality**.)*

### B. Other App Content Declarations
- **Ads:** No, my app does not contain ads.
- **App Access:** All functionality is available without special access (or note that Google Sign-In is optional and local mode works out of the box).
- **Target Audience & Content:** 13+ (or 18+ if you don't want to fill out the Families Policy questionnaire).
- **Content Rating (IARC):** General / Everyone (no violence, no UGC public social feed — shared wishlists are unlisted links only).
- **Government Apps / Financial Features / Health:** No.

---

## 5. Building & Signing for Closed Testing

Google Play requires a **signed Android App Bundle (`.aab`)** — **not** a debug-signed APK.

1. **Generate an Upload Keystore** (if you don't have one yet):
   ```bash
   keytool -genkey -v -keystore gift-tracker-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gifttracker
   ```
   *(Keep this `.jks` file safe and backed up!)*
2. **Build the signed `.aab`:**
   - **Via GitHub Actions (`.github/workflows/ci.yml`):** Add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (and `GOOGLE_SERVICES_JSON_BASE64`) to your repo's GitHub Secrets, then download `app-release.aab` from the workflow artifact.
   - **Or locally:**
     ```bash
     export ANDROID_KEYSTORE_PATH=/path/to/gift-tracker-upload.jks
     export ANDROID_KEYSTORE_PASSWORD=your_password
     export ANDROID_KEY_ALIAS=gifttracker
     export ANDROID_KEY_PASSWORD=your_password
     cd android
     ./gradlew bundleRelease
     # Output: android/app/build/outputs/bundle/release/app-release.aab
     ```
3. **Upload to Closed Testing:**
   - In Play Console → **Testing → Closed testing → Create track** (or use the default `Alpha` track).
   - Upload `app-release.aab`.
   - Add your friends' Google account emails under **Testers** (or create a Google Group) and copy the opt-in URL to share with them once Google approves the release!
   - *(Reminder: If your Play Developer account is a **personal** account created after Nov 2023, Google requires at least **12 testers opted-in continuously for 14 days** in Closed Testing before you can apply for Production.)*
