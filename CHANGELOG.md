# Changelog

All notable changes to **Bubble Android** (`com.bubble.screentools`) are documented in this file.

## [1.1.1] — Personalization, Smart Links & Material 3 Expressive Update (`versionCode = 10`)

### Added
- **Material 3 Expressive Main UI Redesign**:
  - Clean minimalist header with Bubble logo, dynamic build version & theme/variant badge (`BuildConfig.VERSION_NAME`), and tagline.
  - **Top Scrollable 7-Segment Tab Bar** (`General`, `Translation`, `Search`, `Selection`, `Panel`, `Appearance`, `More`) with preserved per-tab vertical scroll state and no bottom navigation bar.
  - Plain Material Surface background, rounded 20dp expressive cards, dynamically-sized pill buttons (`minHeight="44dp"`, zero text clipping), and reduced-motion respect.
- **General Segment Enhancements**:
  - Real-time device status summary (Default Digital Assistant, Accessibility Service, Screen Capture mode, Visual Search status, Default Engine, Offline Language count, Overlay Permission).
  - Consolidated core settings right in **General**: **App Interface Language**, **Default Action Mode**, **Auto-Scan QR Codes**, **Auto-Detect Links on Screen**, Safe Mode toggle, Excluded Apps, Quick Settings shortcuts, and Usage Guide.
  - Live preview of the user's currently saved Action Panel configuration.
- **Auto Link Detection & Smart Select Text Links**:
  - Pure-Kotlin `ScreenLinkDetector` automatically detects safe `http://`/`https://` URLs and valid public domains when Bubble opens or when selecting a region, presenting a non-blocking **"Link found. Open link?"** quick card (yielding priority to QR cards to prevent overlap).
  - In **Select Text** mode, link blocks are visually highlighted with cyan accent framing and underline, clickable directly inside the preview card (`FLAG_ACTIVITY_NEW_TASK` safe in Assistant & Accessibility overlays), copyable as clean URLs (**Copy Link**), and openable directly (**Open Link** replacing *Web Search* when a link is selected).
- **Feature 1 — Advanced Selection Customization**:
  - Live interactive `SelectionOverlayView` preview supporting direct drag, resize, and Freeform drawing.
  - 5 selection shapes: **Rectangle**, **Rounded Rectangle**, **Ellipse**, **Freeform** (with optional quadratic Bezier smoothing), and **Custom Curved** (configurable superellipse curvature).
  - 4 border styles: **Solid**, **Dashed**, **Dotted**, **Dash-Dot** with optional reduced-motion-aware border animation.
  - Border color (Material You accent or custom HEX/RGB picker), thickness (`dp`), corner radius (`dp`), curvature level, selection fill tint opacity, outer dim intensity, resize handle visibility/size/color, edge smoothing, minimum selection size, and Reset to Default.
  - Unified `SelectionPathBuilder` shared across Preview, Overlay, and `BitmapCropHelper` so non-rectangular clipping/masking matches visual bounds for both OCR (background-filled outside mask) and Visual Search (transparent mask).
- **Feature 2 — Advanced Translation Customization**:
  - 3 genuinely differentiated **Translation Quality Modes** (`Fast`, `Accurate`, `Natural`) backed by `OcrImagePreprocessor` and `TranslationContextProcessor`.
  - **Context Reconstruction Profiles** (`General`, `Literal`, `Natural`, `Manga / Game`) with spatial paragraph & speech-bubble merging, vertical Japanese/CJK right-to-left column reconstruction, de-hyphenation, OCR glitch cleanup, and mixed-script splitting.
  - Preferred Source Languages, Fallback Source Language, Recently Used Language quick chips, and **Delete Unused Offline Models** cleanup with estimated storage display.
  - Strict separation between ML Kit offline translation packs and Tesseract `.traineddata` OCR models (with header/size validation), vector swap icon, and Live Pipeline Preview.
- **Feature 3 — Advanced Search Engine Customization**:
  - Modular `SearchEngineRegistry` cleanly separating Visual (Image) Search Engines (**Google Lens**, **Yandex Images**, **TinEye**, **Bing Visual Search**, **SauceNAO**, **trace.moe**, **Open With**) and Selected-Text Search Engines (**Google**, **DuckDuckGo**, **Bing**, **Wikipedia**).
  - Enable/disable, set default, vector **`=`** drag-to-reorder handles (with tap-to-pick-position fallback), remember last engine, and validated Custom Visual & Text Search Engine creation/editing/deletion (documented in `docs/Custom-Engine.md` & `docs/Custom-Engine_ID.md`).
  - Search image pipeline optimization: **Original / Balanced / Data Saver** presets, max resolution & compression sliders, format conversion (`Auto` / `PNG` / `JPEG` / `WebP`), optional **Confirm Before Upload**, external browser vs in-app WebView option, and offline/invalid-URL/missing-app error handling.
- **Feature 4 — GitHub Automatic Update Checker (Play-Protect-Friendly)**:
  - Checks `https://api.github.com/repos/AprionoN-Officials/Bubble-Android/releases/latest` (or `/releases` when prereleases enabled) with Semantic Versioning (`SemVer`), ETag caching (`304 Not Modified`), rate-limit cooldown handling, and ABI-aware asset matching (`arm64-v8a` / `armeabi-v7a` / universal).
  - Settings for Auto Check, Check on Launch, Interval (`6h` / `12h` / `24h` / `7d`), Wi-Fi Only, and Include Prerelease (default OFF), scheduled via lightweight Android `JobScheduler` (no foreground service, no embedded tokens).
  - Opens official GitHub Releases directly in the user's browser for transparent downloading — completely eliminating `REQUEST_INSTALL_PACKAGES` and replacing `QUERY_ALL_PACKAGES` with explicit `<queries>` entries to prevent Google Play Protect false positives.
- **Feature 5 — Voluntary Donation Reminder**:
  - Centralized `DonationConfig` & `DonationReminderManager` gated exclusively on in-app customization menus (**Search**, **Selection**, **Panel**, **Appearance** — never on app launch, General/Translation/More, Safe Mode, permissions, update checks, or overlay triggers via gestures/Accessibility/shortcuts).
  - Displays once on first install upon entering an eligible customization tab, then applies a **global 72-hour cooldown** across all eligible tabs once the user taps `Continue` (after the silent 5-second delay). `X` / Back / `Donate` (linking to `https://trakteer.id/apriononurhakim`) never reset cooldown.
- **Feature 6 — Material 3 Expressive & Theme Customization**:
  - `ThemeManager` & `ThemePaletteGenerator` supporting **Follow System**, **Light**, **Dark**, and **AMOLED Black** modes, Android 12+ **Material You Dynamic Color**, 7 curated manual seed palettes + custom HEX/RGB picker, WCAG contrast compliance, **Theme Scope** toggles (Main UI / Overlay / Selection without overwriting manual selection colors), and a Live Theme Preview card.
- **Feature 7 — Custom Action Panel**:
  - Preserves Bubble's iconic minimalist **two-button default** (`Translate` & `Search Image` at Bottom) on fresh installs and after reset.
  - Shared `ActionPanelView` rendering identically in Live Overlay and Main App previews (`Bubble → Panel`).
  - 6 focused, working overlay actions: **Translate**, **Search Image**, **Select Text**, **Copy Image**, **Save Image**, and **Share Image** (QR detection runs automatically without needing a dedicated button).
  - Button enable/disable (minimum 1 active), vector **`=`** drag-to-reorder handles, multi-button layouts (**Horizontal Scroll**, **Two Rows**, **Icon Only**), docking (**Bottom / Top / Left / Right / Custom** with interactive drag-to-position editor & insets-clamped relative coordinates), scale, opacity, spacing, padding, radii, margins, custom colors, and Search Engine Selector toggle.

### Preserved & Verified
- Zero ads, zero paywalls, zero accounts, zero AI chatbots, zero tracking, zero redeem codes.
- All v1.0.0 Safe Mode protections (`FLAG_SECURE`, excluded banking/authenticator apps, password-field skip, RAM-only screenshots) and `BUG-01..BUG-06` fixes preserved intact.

## [1.0.0] — Initial Public Release (`versionCode = 8`)
- Default Digital Assistant (`VoiceInteractionService`), Accessibility Service, and Quick Settings Tile triggers.
- Live on-screen offline translation (31 ML Kit languages), custom Tesseract 5 `.traineddata` OCR, full-screen Select Text, automatic QR/QRIS scanner, multi-engine Visual Search (Google Lens, Yandex, TinEye, Open With), Bubble Safe Mode, and bilingual EN/ID UI.
- Resolved `BUG-01..BUG-06` in live emulator QA.
