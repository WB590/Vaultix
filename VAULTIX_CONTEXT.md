# Vaultix — Complete Technical Context (Master Guide)

This file is a full technical walkthrough of your Android project.
Goal: help you explain **how everything works** (architecture, code, UI, security, Firebase, flow) in class/report.

---

## 1) Project Identity

- **App name:** Vaultix
- **Purpose:** Secure password manager
- **Stack:** Kotlin + XML + Firebase + AES/PBKDF2
- **Architecture style:** Single-Activity + Fragments + Repository pattern

Vaultix lets users:
1. Authenticate (email/password or Google)
2. Unlock vault with Master Password
3. View encrypted password entries
4. Add encrypted entries
5. Manage session/logout via drawer

---

## 2) Complete User Journey

1. App launches → `WelcomeFragment`
2. After short delay:
   - if user not authenticated → `LoginFragment`
   - if user authenticated → `MasterPasswordFragment`
3. User enters Master Password:
   - app derives encryption key from `master + salt`
   - verifies master key using encrypted verifier
4. Success → `HomeFragment`
5. User can add entry → `AddPasswordFragment`
6. Password is encrypted client-side before Firestore write
7. Back/logout clears in-memory key

---

## 3) Project Structure (Code)

### Core and Orchestration
- `app/src/main/java/com/example/vaultix/MainActivity.kt`
- `app/src/main/java/com/example/vaultix/SessionManager.kt`

### Security
- `app/src/main/java/com/example/vaultix/security/PasswordCrypto.kt`

### Data Layer
- `app/src/main/java/com/example/vaultix/data/FirebaseRepository.kt`
- `app/src/main/java/com/example/vaultix/data/PasswordEntry.kt`

### UI Fragments
- `app/src/main/java/com/example/vaultix/ui/welcome/WelcomeFragment.kt`
- `app/src/main/java/com/example/vaultix/ui/login/LoginFragment.kt`
- `app/src/main/java/com/example/vaultix/ui/master/MasterPasswordFragment.kt`
- `app/src/main/java/com/example/vaultix/ui/home/HomeFragment.kt`
- `app/src/main/java/com/example/vaultix/ui/home/PasswordAdapter.kt`
- `app/src/main/java/com/example/vaultix/ui/add/AddPasswordFragment.kt`

### XML Layouts
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/fragment_welcome.xml`
- `app/src/main/res/layout/fragment_login.xml`
- `app/src/main/res/layout/fragment_master_password.xml`
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/layout/fragment_add_password.xml`
- `app/src/main/res/layout/item_password.xml`
- `app/src/main/res/layout/nav_header.xml`

---

## 4) Dependencies and Build Setup

From `app/build.gradle.kts`, important dependencies:

```kotlin
implementation(platform(libs.firebase.bom))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-analytics")
implementation("com.google.android.gms:play-services-auth:21.2.0")
```

Why each is used:
- `firebase-auth`: login/register and Google credential sign-in
- `firebase-firestore`: storing user salt/verifier/password docs
- `play-services-auth`: Google account picker + token acquisition
- `firebase-analytics`: Firebase analytics instrumentation

Google plugin is enabled:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}
```

This allows generated resources like `default_web_client_id` from `google-services.json`.

---

## 5) Android Manifest Configuration

From `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Why required:
- Firebase Auth and Firestore are network services.

Launcher config:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

Main entry point:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

---

## 6) Deep Dive — Kotlin Files

## 6.1 `MainActivity.kt`

### Responsibility
`MainActivity` is the app coordinator:
- Hosts all fragments
- Controls drawer/toolbar behavior
- Decides navigation transitions
- Handles secure back navigation

### Key behavior
- Starts with `WelcomeFragment`
- On welcome finished:
  - user exists → master screen
  - user missing → login

```kotlin
override fun onWelcomeFinished() {
    if (FirebaseRepository.currentUser() == null) {
        openLoginScreen()
    } else {
        openMasterScreen()
    }
}
```

### Drawer behavior
- Drawer enabled only on `HomeFragment`
- Disabled/locked on login/master/add/welcome

```kotlin
private fun syncDrawerForCurrentFragment() {
    when (supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
        is HomeFragment -> setDrawerEnabled(true)
        else -> setDrawerEnabled(false)
    }
}
```

### Secure back behavior
On back from Home or Master:
- sign out Firebase
- clear in-memory master key
- return to Login

```kotlin
when (supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
    is MasterPasswordFragment, is HomeFragment -> {
        FirebaseRepository.logout()
        SessionManager.clear()
        openLoginScreen(clearBackStack = true)
        return
    }
}
```

---

## 6.2 `WelcomeFragment.kt`

### Responsibility
Simple splash/loading screen with brand logo before routing.

### Lifecycle flow
- Inflates `fragment_welcome`
- Waits 1400 ms
- Calls `listener?.onWelcomeFinished()`

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    delay(1400)
    listener?.onWelcomeFinished()
}
```

Why this is good:
- clean startup UX
- central place for future startup checks (token refresh, remote config, etc.)

---

## 6.3 `LoginFragment.kt`

### Responsibility
Handles:
- Email/password login
- Email/password register
- Google Sign-In → Firebase Auth

### Email flow snippet

```kotlin
if (isRegister) {
    FirebaseRepository.register(email, password)
} else {
    FirebaseRepository.login(email, password)
}
listener?.onAuthenticated()
```

### Google flow steps
1. Build GoogleSignInClient
2. Launch account picker
3. Read `idToken`
4. Send token to Firebase credential login

```kotlin
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken(googleWebClientId)
    .requestEmail()
    .build()
```

```kotlin
FirebaseRepository.loginWithGoogleIdToken(idToken)
listener?.onAuthenticated()
```

### Error handling
- Missing config message
- status-code aware Google failures
- generic fallback for Firebase exceptions

---

## 6.4 `MasterPasswordFragment.kt`

### Responsibility
Vault unlock screen with real verification of master key.

### What happens on Unlock
1. Get or create user security (`salt`, verifier)
2. Derive AES key from entered master
3. Validate key using encrypted verifier
4. Save key in `SessionManager`
5. Navigate to Home

Core snippet:

```kotlin
val securityConfig = FirebaseRepository.getOrCreateUserSecurity {
    PasswordCrypto.generateSalt()
}
val key = PasswordCrypto.deriveMasterKey(masterPassword, securityConfig.salt)
```

Verifier logic:
- first time: create encrypted verifier and store it
- next times: decrypt verifier; mismatch = reject

```kotlin
if (!verifierCipher.isNullOrBlank() && !verifierIv.isNullOrBlank()) {
    val verifierPlain = PasswordCrypto.decrypt(verifierCipher, verifierIv, key)
    if (verifierPlain != MASTER_VERIFIER_PLAINTEXT) {
        // invalid master
    }
}
```

This guarantees one effective master password per user (without storing it directly).

---

## 6.5 `HomeFragment.kt`

### Responsibility
Displays decrypted password list and opens Add screen.

### Load flow
1. Read session key
2. Fetch encrypted docs from Firestore
3. Decrypt each password
4. Build UI list
5. Show empty state if needed

```kotlin
val encryptedEntries = FirebaseRepository.getPasswordEntries()
val uiItems = encryptedEntries.mapNotNull { entry ->
    val plainPassword = PasswordCrypto.decrypt(entry.encryptedPassword, entry.iv, key)
    UiPasswordEntry(id = entry.id, site = entry.site, password = plainPassword)
}
```

The fragment does not store clear passwords in DB; they are decrypted only at runtime for display.

---

## 6.6 `PasswordAdapter.kt`

### Responsibility
RecyclerView adapter for each saved entry card.

### Features
- Toggle Show/Hide password
- Copy password to clipboard

Show/hide snippet:

```kotlin
binding.tvPassword.text = if (item.isVisible) item.password else "••••••••••"
```

Copy snippet:

```kotlin
val clip = ClipData.newPlainText("vaultix_password", current.password)
clipboard.setPrimaryClip(clip)
```

---

## 6.7 `AddPasswordFragment.kt`

### Responsibility
Add new credential securely.

### Features
- Site + password input
- Generate strong password button (A-Za-z0-9)
- Encrypt before save

Generator snippet:

```kotlin
private val passwordAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
private fun generateStrongPassword(length: Int): String { ... }
```

Save flow snippet:

```kotlin
val encryptedPayload = PasswordCrypto.encrypt(password, key)
FirebaseRepository.addPassword(
    site = site,
    encryptedPassword = encryptedPayload.cipherTextBase64,
    iv = encryptedPayload.ivBase64
)
```

---

## 6.8 `FirebaseRepository.kt`

### Responsibility
Single data access layer for Firebase services.

### Auth APIs
- `login(email, password)`
- `register(email, password)`
- `loginWithGoogleIdToken(idToken)`
- `logout()`

### Firestore APIs
- `getOrCreateUserSecurity(...)`
- `saveMasterVerifier(...)`
- `getPasswordEntries()`
- `addPassword(...)`

### Why repository pattern matters
- UI fragments stay cleaner
- easier testing and maintenance
- single place for backend logic

---

## 6.9 `PasswordEntry.kt`

Defines two models:

```kotlin
data class PasswordEntry(
    val id: String,
    val site: String,
    val encryptedPassword: String,
    val iv: String
)

data class UiPasswordEntry(
    val id: String,
    val site: String,
    val password: String,
    val isVisible: Boolean = false
)
```

Purpose:
- `PasswordEntry` = DB transport model (encrypted)
- `UiPasswordEntry` = decrypted runtime model for screen rendering

---

## 6.10 `SessionManager.kt`

Stores the derived key only in memory:

```kotlin
private var masterKey: Key? = null
```

Methods:
- `setMasterKey(...)`
- `getMasterKey()`
- `isUnlocked()`
- `clear()`

Security idea:
- key is session-scoped, not persisted to disk

---

## 6.11 `PasswordCrypto.kt`

### Cryptographic parameters
- KDF: PBKDF2-HMAC-SHA256
- Iterations: 120,000
- Key length: 256 bits
- AES mode: GCM (authenticated encryption)
- IV length: 12 bytes
- Tag length: 128 bits

### Derivation

```kotlin
val spec: KeySpec = PBEKeySpec(
    masterPassword.toCharArray(),
    salt,
    KDF_ITERATIONS,
    KDF_KEY_LENGTH
)
```

### Encrypt/Decrypt

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
```

Why AES-GCM:
- confidentiality + integrity (tamper detection)

---

## 7) Deep Dive — XML Layouts

## 7.1 `activity_main.xml`

Main shell layout:
- root: `DrawerLayout`
- top: `MaterialToolbar`
- center: `FrameLayout` as fragment container
- left drawer: `NavigationView` with header + menu

This is the structural backbone of app navigation.

---

## 7.2 `fragment_welcome.xml`

Contains:
- logo (`vaultix_logo`)
- app title
- tagline
- progress spinner

Dark background and centered composition for startup branding.

---

## 7.3 `fragment_login.xml`

Contains:
- logo + title
- email field
- password field with toggle icon
- buttons: Login / Register / Google
- progress indicator

Uses Material TextInput + buttons with your theme colors.

---

## 7.4 `fragment_master_password.xml`

Contains:
- master title
- secure password input with toggle
- unlock button
- loading spinner

Single-purpose UX: unlock vault session.

---

## 7.5 `fragment_home.xml`

Contains:
- title
- loading spinner
- empty-state text
- RecyclerView list
- FAB for add action

Coordinates list browsing and add navigation.

---

## 7.6 `fragment_add_password.xml`

Contains:
- site input
- password input
- generate strong password button
- save button
- loading spinner

This screen is write-only and encrypts before any save.

---

## 7.7 `item_password.xml`

Card for each password entry:
- `tvSite`
- `tvPassword`
- row buttons:
  - `btnToggle` (show/hide)
  - `btnCopy` (copy to clipboard)

---

## 7.8 `nav_header.xml`

Drawer header:
- top banner image (`banner_nav`)
- title (`nav_title`)
- dynamic user email (`tvDrawerEmail`)

---

## 8) Menu, Theme, Colors, Icons

## 8.1 Drawer menu
`drawer_menu.xml` has:
- Profile
- Settings
- Logout
- Other

## 8.2 Item tint
`nav_item_tint.xml` forces black icon/text on white drawer background.

## 8.3 Colors
`colors.xml` defines app palette:
- `vaultix_primary` `#0A3161`
- `vaultix_accent` `#34E1FF`
- `vaultix_secondary` `#2465A5`
- `vaultix_text_light` `#D6E3F0`
- `vaultix_dark` `#051A33`

## 8.4 Theme
`themes.xml` and `values-night/themes.xml` apply Material3 NoActionBar + custom color mapping.

## 8.5 App launcher icon
- adaptive icon files: `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`
- foreground uses your logo: `drawable/ic_launcher_foreground.xml`
- background uses brand primary color: `drawable/ic_launcher_background.xml`

---

## 9) Firestore Data Contract

Per user doc:
- `users/{uid}`
  - `salt`
  - `masterVerifierCipher`
  - `masterVerifierIv`

Per password doc:
- `users/{uid}/passwords/{docId}`
  - `site`
  - `password` (cipher Base64)
  - `iv` (Base64)

Important:
- no clear password is saved in Firestore
- no master password is saved in Firestore

---

## 10) End-to-End Technical Flows

## 10.1 Email login + unlock
1. LoginFragment calls `FirebaseRepository.login()`
2. MainActivity opens Master screen
3. Master derives key + verifies
4. Session key stored in memory
5. Home opens and decrypts list

## 10.2 Google login + unlock
1. LoginFragment launches Google picker
2. Gets idToken
3. Calls `loginWithGoogleIdToken`
4. Same master unlock flow as email

## 10.3 Add new password
1. User inputs site/password (or generated)
2. AddFragment encrypts with session key
3. Save encrypted payload in Firestore
4. Return to Home

## 10.4 Copy password
1. User taps Copy
2. Adapter writes plain password into clipboard
3. toast confirms copy

---

## 11) Security Guarantees in Current Implementation

Implemented:
- client-side encryption before persistence
- master password not stored
- per-user salt
- verifier-based master validation
- session key cleared on logout/back routes

Current limitations (normal for MVP):
- session key lives in process memory (not hardware-backed)
- no biometric gate yet
- no offline-first crypto policy yet

---

## 12) Firebase Setup Checklist (Operational)

Required for stable app behavior:

1. Enable Email/Password in Firebase Auth
2. Enable Google provider in Firebase Auth
3. Add Android SHA-1/SHA-256 in Firebase app settings
4. Download fresh `google-services.json` after SHA updates
5. Ensure Firestore rules allow authenticated user access to own path

Common error mapping:
- `CONFIGURATION_NOT_FOUND` → Auth setup incomplete
- `PERMISSION_DENIED` → Firestore rules mismatch
- Google picker works but no login → SHA/OAuth mismatch

---

## 13) How to Explain This Project in Viva/Presentation

Suggested order:

1. Problem statement (secure password management)
2. Architecture (single activity + fragments + repo)
3. Authentication (email + Google)
4. Security model (PBKDF2 + AES-GCM + verifier)
5. Data model (`users/{uid}`, `passwords`)
6. UI walkthrough (welcome/login/master/home/add/drawer)
7. Demo: add + decrypt + copy + logout
8. Lessons learned and next improvements

---

## 14) “Every Inch” Recap (Quick Bullet)

- `MainActivity` controls app state and secure navigation
- `WelcomeFragment` gives startup UX and route decision point
- `LoginFragment` handles 2 auth providers
- `MasterPasswordFragment` is the cryptographic gate
- `HomeFragment` decrypts and displays data
- `AddPasswordFragment` encrypts before saving
- `PasswordAdapter` handles reveal + clipboard copy
- `FirebaseRepository` centralizes backend calls
- `PasswordCrypto` centralizes encryption primitives
- XML files define each visual screen and interactions
- Theme/colors keep brand consistency
- Firestore holds encrypted payloads only

---

## 15) Next Evolution Ideas

- Add edit/delete password entries
- Add password strength meter and custom length generator
- Add biometric unlock after first master validation
- Add local encrypted cache for offline mode
- Add unit tests for crypto and repository paths

---

This is already a strong, well-structured first Android project: secure design, clean flow, modular code, and real backend integration.
