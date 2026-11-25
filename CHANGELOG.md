# Changelog

All notable changes to **Bubble** (`com.bubble.screentools`) are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Material You Expressive frontend built with official AndroidX Jetpack Compose and Material 3 (`MotionScheme.expressive()`).
- Appearance settings section: Theme mode (`Auto`, `Light`, `Dark`), Pure black OLED mode for dark theme, Android 12+ dynamic wallpaper color support, and four built-in Material 3 presets (`Bubble`, `Lagoon`, `Lavender`, `Coral`).
- Material 3 Expressive segmented card stacks (`24dp` outer / `6dp` inner corners), asymmetric speech-bubble droplet icon badges (`BubbleIconBadge`), check-thumb switches, and `SingleChoiceSegmentedButtonRow` controls.
- Android 13+ Material You monochrome adaptive launcher icon (`drawable-anydpi-v26/ic_launcher_icon.xml`) with API 24–25 vector fallback.
- GitHub Actions CI workflow (`.github/workflows/android-build.yml`) running unit tests and building debug + release APKs.
- GitHub Actions automated Release workflow (`.github/workflows/android-release.yml`) for `v*` tags and manual dispatch, with optional keystore signing from secrets, `SHA256SUMS.txt` generation, and `CHANGELOG.md` release notes extraction.
- `x86_64` ABI split alongside `arm64-v8a` and `armeabi-v7a` for emulator and desktop container testing.
- Technical guide for the new UI and CI/CD pipelines in `docs/ui-and-ci.md`.

### Changed
- Migrated `MainActivity`, `LanguageManagerActivity`, and `VisualSearchWebActivity` to Jetpack Compose while keeping Bubble's single-scroll dashboard layout (no bottom navigation bar) and `AppCompatActivity` per-app locale compatibility.
- Updated Gradle wrapper (`9.6.1`), Android Gradle Plugin (`9.3.0`), Kotlin Compose compiler plugin (`2.4.10`), `compileSdk = 37`, and `targetSdk = 36`.
- Refined overlay pill drawables (`bg_action_button_active.xml`, `bg_action_button_inactive.xml`) and dark neutral surface colors.

## [1.0.0]

### Added
- Initial release of Bubble (`com.bubble.screentools`) with live on-screen translation (31 offline ML Kit languages), hybrid OCR (ML Kit Play Services + custom Tesseract 5 `.traineddata`), full-screen Select Text, automatic QR code scanning (including Indonesian QRIS EMVCo TLV, Wi-Fi, UPI, URL, contacts), multi-engine visual search (Google Lens, Yandex, TinEye, Open With), Default Digital Assistant / Accessibility / Quick Settings Tile triggers, bilingual EN/ID support, and Bubble Safe Mode.
