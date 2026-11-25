# Material You Expressive UI & CI/CD Guide

This document describes the Jetpack Compose Material You Expressive frontend architecture and the GitHub Actions CI/CD workflow introduced for **Bubble** (`com.bubble.screentools`).

---

## 1. Material You Expressive Frontend Architecture

The companion app (`MainActivity`, `LanguageManagerActivity`, and `VisualSearchWebActivity`) is built with official **AndroidX Jetpack Compose** and **Material 3 Expressive** (`androidx.compose.material3:material3:1.5.0-alpha25`, `MotionScheme.expressive()`), while keeping Bubble's existing single-scroll workflow and native overlay services intact.

### Key Components

| File | Responsibility |
| --- | --- |
| `ui/theme/BubbleTheme.kt` | `BubbleExpressiveTheme` wrapping `MotionScheme.expressive()`, Android 12+ dynamic wallpaper theming (`dynamicLightColorScheme` / `dynamicDarkColorScheme`), four built-in Material 3 presets (`Bubble`, `Lagoon`, `Lavender`, `Coral`), dark-mode Pure Black OLED support, and edge-to-edge system bar icon contrast syncing. |
| `ui/component/BubbleExpressiveComponents.kt` | Reusable Material 3 Expressive UI primitives: segmented card stacks (`24dp` outer / `6dp` inner corner geometry separated by `3dp` gaps), asymmetric speech-bubble droplet icon badges (`BubbleIconBadge`) mapped to Material You semantic container tones, check-thumb Material 3 switches, `SingleChoiceSegmentedButtonRow` choice cards, and horizontal color palette capsules. |
| `ui/main/BubbleDashboardScreen.kt` | Single-scroll vertical dashboard preserving Bubble's layout hierarchy without a bottom navigation bar: Hero launch card, Safe Mode alert banner, Status & Activation, Translation & OCR, Lens & Visual Search, Overlay & QR, Appearance, and Privacy & Security. |
| `ui/main/ModelsScreen.kt` | Offline ML Kit translation pack manager (31 languages with live search and download/delete actions) and Tesseract 5 `.traineddata` manager (SAF import, activation toggle, deletion, and official `tessdata_fast` quick downloads). |
| `ui/main/BubbleUiState.kt` | `@Stable` Compose state holder synchronizing live permission states, downloaded ML Kit / Tesseract models, and `BubblePreferences`. |
| `lens/VisualSearchWebActivity.kt` | Material 3 Expressive `TopAppBar`, engine switcher chips (`Google Lens`, `Yandex`, `TinEye`), and progress indicator wrapping the existing `WebView` auto-upload flow via `AndroidView`. |

### Adaptive & Monochrome Launcher Icon

Android 13+ Material You themed launcher icon support is provided via an `<adaptive-icon>` resource while keeping `drawable/ic_bubble_logo.xml` as a pure vector for in-app rendering and API 24–25 compatibility:

- `app/src/main/res/drawable-anydpi-v26/ic_launcher_icon.xml` — `<adaptive-icon>` combining background, foreground, and `<monochrome>` layers
- `app/src/main/res/drawable/ic_launcher_bg.xml` — Solid Bubble background layer
- `app/src/main/res/drawable/ic_launcher_fg.xml` — Centered adaptive safe-zone foreground vector
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — Single-color monochrome layer for Material You themed icons
- `app/src/main/res/drawable/ic_launcher_icon.xml` — API 24–25 vector fallback

### Compatibility Notes

- `MainActivity`, `LanguageManagerActivity`, and `VisualSearchWebActivity` continue extending `AppCompatActivity` so `AppCompatDelegate.setApplicationLocales()` (`LocaleHelper`) works reliably across Android 7–15 (API 24–35/36).
- All background services (`BubbleVoiceInteractionService`, `BubbleAccessibilityService`, `BubbleTileService`, `MediaProjectionService`), overlay controllers (`BubbleOverlayController`, `BubbleAssistantActivity`), OCR, translation, QR parsing, and `SafeModeManager` remain unchanged in behavior.

---

## 2. GitHub Actions CI/CD Workflow

The repository includes an automated build and test pipeline at `.github/workflows/android-build.yml`.

### Triggers

- **Push** to `main` or `feat/**` branches
- **Pull Request** targeting `main`
- **Manual dispatch** (`workflow_dispatch`) from the GitHub Actions tab

### Pipeline Steps

1. **Checkout & JDK 17:** Checks out the repository (`actions/checkout@v5`) and configures Eclipse Temurin JDK 17 (`actions/setup-java@v5`).
2. **Android SDK Setup:** Accepts SDK licenses and installs `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, and `platforms;android-37.0` (`--channel=2`) via `sdkmanager`.
3. **Gradle Caching:** Uses `gradle/actions/setup-gradle@v4` to cache Gradle distributions and dependencies across runs.
4. **Unit Tests & APK Builds:** Executes:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug assembleRelease --no-daemon
   ```
5. **Artifact Uploads (7-day retention):**
   - `bubble-debug-arm` — `app-arm64-v8a-debug.apk` and `app-armeabi-v7a-debug.apk` (for physical ARM devices; uses `.debug` applicationId suffix so it installs alongside release builds)
   - `bubble-debug-x86_64` — `app-x86_64-debug.apk` (for x86_64 emulators and desktop Android containers)
   - `bubble-release-apks` — R8-minified, resource-shrunk release APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`)

### Release Signing Notes

- By default in CI (without a private keystore checked into git), `assembleRelease` produces unsigned release APKs to verify R8 minification and resource shrinking without exposing secrets.
- When `keystore/bubble-release.jks` is present locally or decoded in a release workflow, `app/build.gradle.kts` signs the release build using `BUBBLE_KEYSTORE_PASSWORD`, `BUBBLE_KEY_ALIAS`, and `BUBBLE_KEY_PASSWORD`.
