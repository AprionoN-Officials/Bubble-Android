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

## 2. GitHub Actions CI/CD Workflows

The repository includes two GitHub Actions workflows under `.github/workflows/`:

1. **Continuous Integration** (`.github/workflows/android-build.yml`) — runs unit tests and builds debug + release APKs on every push, pull request, or manual dispatch.
2. **Automated Release** (`.github/workflows/android-release.yml`) — builds R8-minified per-ABI release APKs, generates `SHA256SUMS.txt`, and publishes a GitHub Release automatically on `v*` tags or manual dispatch.

### 2.1 Continuous Integration (`android-build.yml`)

**Triggers:**
- Push to `main` or `feat/**`
- Pull requests targeting `main`
- Manual dispatch (`workflow_dispatch`)

**Steps:**
1. Checks out the repository (`actions/checkout@v5`) and sets up Eclipse Temurin JDK 17 (`actions/setup-java@v5`).
2. Accepts Android SDK licenses and installs `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, and `platforms;android-37.0` (`--channel=2`).
3. Enables Gradle caching via `gradle/actions/setup-gradle@v4`.
4. Runs:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug assembleRelease --no-daemon
   ```
5. Uploads build artifacts (7-day retention):
   - `bubble-debug-arm` — `app-arm64-v8a-debug.apk` and `app-armeabi-v7a-debug.apk` (uses `.debug` `applicationIdSuffix` so it installs alongside release builds)
   - `bubble-debug-x86_64` — `app-x86_64-debug.apk` (for x86_64 emulators and desktop Android containers)
   - `bubble-release-apks` — R8-minified, resource-shrunk release APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`)

### 2.2 Automated Release (`android-release.yml`)

**Triggers:**
- Pushing a tag matching `v*` (for example, `git tag v1.1.0 && git push origin v1.1.0`)
- Manual dispatch (`workflow_dispatch`) with `tag` (e.g. `v1.1.0`) and optional `prerelease` flag

**Published Assets:**
- `Bubble-<tag>-arm64-v8a.apk`
- `Bubble-<tag>-armeabi-v7a.apk`
- `Bubble-<tag>-x86_64.apk`
- `SHA256SUMS.txt`

**Optional Release Signing Secrets:**

To publish signed release APKs directly from GitHub Actions, add the following repository secrets under **Settings > Secrets and variables > Actions**:

| Secret | Description |
| --- | --- |
| `BUBBLE_KEYSTORE_BASE64` | Base64-encoded contents of `bubble-release.jks` (`base64 -w 0 bubble-release.jks`) |
| `BUBBLE_KEYSTORE_PASSWORD` | Keystore password |
| `BUBBLE_KEY_ALIAS` | Key alias inside the keystore |
| `BUBBLE_KEY_PASSWORD` | Key password |

The decoded keystore file is deleted in an `if: always()` cleanup step after the build completes. If `BUBBLE_KEYSTORE_BASE64` is not set, the workflow falls back gracefully to building unsigned release APKs.
