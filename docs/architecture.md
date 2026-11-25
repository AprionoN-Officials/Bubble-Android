# Bubble — Technical Architecture (`com.bubble.screentools`)

This document describes the verified runtime architecture of **Bubble v1.0.0** (`versionCode = 7`, `minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`) directly audited from the source code.

---

## 1. High-Level Architecture

Bubble follows a **lightweight, modular, single-module Android architecture** written in Kotlin using Android Views, Material 3 (`Theme.Material3.DayNight.NoActionBar`), and Kotlin Coroutines—without heavy DI or UI frameworks—to keep memory overhead and APK size compact.

```mermaid
flowchart TD
    subgraph ModeA["Mode A — Companion App & Settings"]
        MA["MainActivity\n(Segmented Material 3 Dashboard)"]
        LM["LanguageManagerActivity\n(31 ML Kit Languages + Tesseract 5 OCR)"]
        PREFS["BubblePreferences\n(SharedPreferences)"]
        MA --> LM
        MA <--> PREFS
        LM <--> PREFS
    end

    subgraph Triggers["Entry Points (Zero Recording Prompt on Supported Modes)"]
        DA["Default Digital Assistant\nBubbleVoiceInteractionService\n+ BubbleVoiceInteractionSession\n+ BubbleRecognitionService"]
        AS["Accessibility Service\nBubbleAccessibilityService\n(A11y Button / Shortcut)"]
        QS["Quick Settings Tile\nBubbleTileService\n-> OverlayTriggerActivity"]
        QS --> AS
    end

    subgraph Security["Privacy & Fail-Safe Gate"]
        SM["SafeModeManager\n(Foreground Package & Protected Window Check)"]
    end

    subgraph ModeB["Mode B — Interactive Screen Session"]
        BAA["BubbleAssistantActivity\n(Fullscreen Session via SessionSnapshotStore)"]
        BOC["BubbleOverlayController\n(TYPE_ACCESSIBILITY_OVERLAY / WindowManager)"]
        UI["Shared Overlay Views\nSelectionOverlayView | SelectableTextOverlayView\nTranslationOverlayRenderer | OverlayLanguageBarHelper"]
        BAA --> UI
        BOC --> UI
    end

    subgraph Engines["Processing Engines"]
        CAP["ScreenCaptureManager + BitmapCropHelper\n(A11y takeScreenshot / Voice Session / Legacy MediaProjection)"]
        OCR["OCRManager\n(Optional Tesseract 5 -> ML Kit Play Services OCR)"]
        A11Y["ScreenTextExtractor / AssistStructure\n(Fallback, skips password nodes)"]
        TR["TranslationManager + LanguageDetectionManager\n(ML Kit On-Device Translate + 256-entry LruCache)"]
        QR["QrCodeScanner + QrCodeParser + QrActionHelper\n(ML Kit Barcode -> QRIS/EMVCo/Wi-Fi/URL/vCard)"]
        VS["LensIntegrationManager + TemporaryImageProvider\n+ VisualSearchWebActivity\n(Google Lens / Yandex / TinEye / Open With)"]
    end

    DA --> SM
    AS --> SM
    SM -->|Allowed| BAA
    SM -->|Allowed| BOC
    SM -->|Blocked| STOP["Halt, Recycle Bitmaps & Dismiss"]

    BAA --> CAP
    BOC --> CAP
    CAP --> OCR
    OCR -->|Empty Fallback| A11Y
    OCR --> TR
    A11Y --> TR
    CAP --> QR
    CAP --> VS
```

---

## 2. Package & Component Map

| Package | Key Classes | Responsibility |
| --- | --- | --- |
| `com.bubble.screentools` | `BubbleApp` | Applies saved app locale (`LocaleHelper.applyAppLocale`) at startup. |
| `ui.main` | `MainActivity`, `LanguageManagerActivity` | Material 3 segmented settings dashboard, permission status checks, bidirectional language picker, offline ML Kit model downloader/deleter, and custom Tesseract `.traineddata` import/download UI. |
| `ui.overlay` | `BubbleAssistantActivity`, `BubbleOverlayController`, `OverlayLanguageBarHelper`, `OverlayRootFrameLayout`, `SelectionOverlayView`, `SelectionGestureHandler`, `SelectableTextOverlayView`, `TranslationOverlayRenderer` | Full-screen Assistant session UI & Accessibility overlay lifecycle, rectangle/ellipse/freeform selection canvas with corner handles, full-screen live tap-to-select text canvas, adaptive translation box rendering, QR result card, and auto-hiding bottom controls. |
| `service` | `BubbleVoiceInteractionService`, `BubbleVoiceInteractionSessionService`, `BubbleVoiceInteractionSession`, `BubbleRecognitionService`, `BubbleAccessibilityService`, `BubbleTileService`, `OverlayTriggerActivity`, `MediaProjectionService` | OS integration entry points. Includes `BubbleRecognitionService` so Android `RoleManager` lists Bubble in *Default Digital Assistant* settings; `BubbleTileService` uses `startActivityAndCollapse()` via `OverlayTriggerActivity` to remain Android 12–15 compliant. |
| `capture` | `ScreenCaptureManager`, `BitmapCropHelper`, `SessionSnapshotStore`, `MediaProjectionConsentActivity` | In-memory screenshot acquisition, aspect-accurate view-to-bitmap cropping with optional 2× upscaling + 16 px white padding for OCR, and one-shot in-memory snapshot handoff (`SessionSnapshotStore`). |
| `ocr` | `OCRManager`, `TesseractOcrManager`, `ScreenTextExtractor` | Hybrid OCR-first text extraction (optional Tesseract 5 `.traineddata` in `filesDir/tesseract/tessdata/`, default unbundled ML Kit Play Services Latin OCR) with Accessibility/AssistStructure fallback. |
| `translation` | `TranslationManager`, `LanguageDetectionManager`, `SupportedLanguages` | ML Kit Language ID, on-device ML Kit translation across 31 curated languages, 256-entry in-memory `LruCache`, bidirectional swap helper, and model download/delete management. |
| `qr` | `QrCodeScanner`, `QrCodeParser`, `QrActionHelper` | Unbundled ML Kit Barcode Scanning (QR format), pure-Kotlin payload parser (Indonesian QRIS / EMVCo TLV, PayNow, DuitNow, PromptPay, UPI/e-wallet URIs, Wi-Fi, URL, vCard/MECARD, Email, Phone, SMS), and native Android action dispatcher (`ACTION_VIEW`, installed payment app picker, Android 11+ `ACTION_WIFI_ADD_NETWORKS`). |
| `lens` | `LensIntegrationManager`, `TemporaryImageProvider`, `VisualSearchWebActivity` | Targeted `ACTION_SEND` handoff to Google Lens (`com.google.ar.lens`) or Google App (`com.google.android.googlequicksearchbox`), in-app WebView reverse image search for Yandex Images & TinEye with `DataTransfer` injection, native `ACTION_VIEW` ("Open With") chooser, and `FileProvider` serving `cacheDir/lens_temp/`. |
| `security` | `SafeModeManager` | Evaluates foreground package against the user-editable excluded list (14 default banking/e-wallet/authenticator/ID apps) and checks protected windows to halt scanning fail-safely. |
| `data` | `BubblePreferences` | Consolidated `SharedPreferences` (`bubble_preferences`) backing all user settings and excluded package list. |
| `model` | `BubbleState` | Strongly-typed enums and state models (`SelectionShape`, `ActionMode`, `VisualSearchEngine`, `TranslationOverlayStyle`, `SelectionRegion`, `TextBlockResult`, `TranslatedBlock`, `BubbleOverlayState`). |
| `util` | `CompatibilityHelper`, `LocaleHelper`, `PerformanceManager` | API-level compatibility guards (API 24–35), per-app locale application (`AppCompatDelegate` + `Context.createConfigurationContext` for services), debounce timings (650 ms translate / 1800 ms visual search), and volatile cache cleanup. |

---

## 3. Screen Capture Hierarchy

Bubble prioritizes capture mechanisms that require **no MediaProjection "Start recording or casting?" dialog**:

1. **Default Digital Assistant (Android 7–15 / API 24–35)**
   - Triggered via `VoiceInteractionService` (`BubbleVoiceInteractionSession`).
   - Android directly supplies both the screen `Bitmap` (`onHandleScreenshot`) and `AssistStructure` (`onHandleAssist`).
   - Hardware bitmaps are copied to software `ARGB_8888`, checked against `SafeModeManager`, transferred via `SessionSnapshotStore` (single-consume, cleared immediately), and displayed in `BubbleAssistantActivity`.
   - Requires **neither** `AccessibilityService` **nor** `SYSTEM_ALERT_WINDOW`.
2. **Silent Accessibility Screenshot (Android 11+ / API 30+)**
   - Triggered via Accessibility Button/Shortcut or Quick Settings Tile (`BubbleAccessibilityService`).
   - Calls `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, ...)` (`android:canTakeScreenshot="true"`).
   - Converts `HardwareBuffer` to software `ARGB_8888`, closes the buffer immediately, and respects `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (`FLAG_SECURE`).
   - Uses `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` when invoked from `BubbleAccessibilityService`.
3. **Legacy MediaProjection Fallback (Android 7–10 / API 24–29)**
   - Used only when Accessibility silent screenshots are unsupported (`API < 30`) and the user activates via Accessibility/Tile rather than Default Assistant.
   - `MediaProjectionService.startWithGrant()` enters foreground mode (`foregroundServiceType="mediaProjection"`) *before* calling `getMediaProjection()` to avoid Android `SecurityException` races.
   - Captures frames strictly in RAM via `VirtualDisplay` + `ImageReader` and releases them when the session ends.

---

## 4. Hybrid Text Detection, OCR & Translation Pipeline

```mermaid
sequenceDiagram
    participant U as User
    participant O as Overlay / Assistant UI
    participant C as BitmapCropHelper
    participant T as OCRManager (Tesseract / ML Kit)
    participant A as ScreenTextExtractor / AssistStructure
    participant TR as TranslationManager (ML Kit)

    U->>O: Select region (or Entire Screen)
    O->>O: Wait 650 ms debounce (canceled on touch/resize)
    O->>C: Crop region (+2x upscale & 16px pad for OCR)
    C-->>O: CroppedBitmapResult (bitmap, offsets, scaleX/Y, padPx)
    O->>T: recognizeBitmap()
    alt Custom Tesseract 5 enabled & .traineddata installed
        T->>T: TessBaseAPI line-level recognition
    end
    alt Tesseract disabled or empty
        T->>T: ML Kit Play Services Text Recognition
    end
    alt OCR returns empty
        O->>A: Fallback to Accessibility / AssistStructure (skips password fields)
    end
    O->>TR: translateBlocks(blocks, source, target, autoDetect, cache=true)
    TR->>TR: Check 256-entry LruCache -> On-device ML Kit Translate
    TR-->>O: TranslatedBlock list (mapped to view coordinates)
    O->>U: Render TranslationOverlayRenderer + auto-hide bottom controls
```

---

## 5. Multi-Engine Visual Search & Temporary Image Lifecycle

- **Delay Protection**: Selecting a region in Visual Search mode starts a **1,800 ms** (`PerformanceManager.getVisualSearchDebounceMs()`) cancelable countdown (`2s → 1s`). Touching any selection handle immediately cancels the countdown so users can adjust framing before external apps open.
- **Dispatch Targets**:
  - **Google Lens (Default)**: Targeted `Intent.ACTION_SEND` (`image/png`) to `com.google.ar.lens`, falling back to `com.google.android.googlequicksearchbox`, then native Android `ACTION_VIEW` (*Open With*) chooser.
  - **Yandex Images / TinEye**: Opened in-app via `VisualSearchWebActivity` (`WebView`) with JavaScript `DataTransfer` file injection and top engine-switcher chips.
  - **Open With (`SHARESHEET` enum)**: Opens Android's native `ACTION_VIEW` chooser (`Pilih aplikasi untuk membuka`).
- **Temporary Cache Cleanup**: `TemporaryImageProvider` writes cropped PNGs solely to `cacheDir/lens_temp/bubble_lens_<timestamp>.png` (never MediaStore/gallery). Files expire after **5 minutes** and are purged automatically on next capture or immediately via *Clear Cache* in Settings.

---

## 6. Privacy & Safe Mode Enforcement

- **Default Excluded List**: Ships with 14 common mobile banking, e-wallet, password manager, authenticator, and digital ID packages (`BubblePreferences.DEFAULT_EXCLUDED_PACKAGES`), fully editable by the user.
- **Runtime Monitoring**: `BubbleAccessibilityService` listens to `TYPE_WINDOW_STATE_CHANGED` and invokes `BubbleOverlayController.haltAllAndDismiss()` immediately if an excluded app enters the foreground.
- **Password & Secure Content Protection**:
  - `ScreenTextExtractor` skips nodes where `node.isPassword == true`.
  - `BubbleVoiceInteractionSession` skips `AssistStructure.ViewNode` fields with text/number password `inputType` flags.
  - Android `FLAG_SECURE` windows are respected without workaround attempts.
- **Zero Persistent Screenshot Storage**: Full-screen snapshots are kept exclusively in RAM and recycled immediately on overlay dismissal. The **only** user-initiated gallery write is when the user explicitly taps *Save QR* (or selects a payment app that requires gallery QRIS import), saving a cropped QR PNG to `Pictures/BubbleQR`.

---

## 7. Build & APK Size Architecture

Configured in `app/build.gradle.kts` and `gradle.properties`:
- **R8 Full Mode**: `android.enableR8.fullMode=true`, `isMinifyEnabled = true`, `isShrinkResources = true`.
- **ABI Splits**: Separate `arm64-v8a` and `armeabi-v7a` APKs (`isUniversalApk = false`).
- **Compressed Native Libraries**: `packaging.jniLibs.useLegacyPackaging = true` compresses Tesseract/Leptonica & ML Kit `.so` binaries inside the APK.
- **Unbundled Play Services ML Kit**: `play-services-mlkit-text-recognition:19.0.1` and `play-services-mlkit-barcode-scanning:18.3.1` keep vision models out of the base APK, while ML Kit translation models (~30 MB each) and Tesseract `.traineddata` packs are downloaded on demand.
- **Resource Pruning**: `resourceConfigurations += listOf("id", "in", "en")` strips unused library translations.
