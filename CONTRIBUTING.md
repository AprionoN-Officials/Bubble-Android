# Contributing to Bubble Android

Thank you for your interest in contributing to **Bubble**! Every contribution—whether a UI translation, bug report, device compatibility report, or code improvement—helps make Bubble better.

> [!NOTE]
> **Maintainer Scope & Pace Notice:**
> Bubble is maintained independently at the owner's own pace and design direction.
> - Please discuss major changes or new workflows in an Issue before opening a Pull Request so they do not disrupt Bubble's core user experience.
> - Opening an Issue or Pull Request does **not** guarantee immediate review or implementation.
> - If you need a feature urgently or prefer a different workflow/UI, you are **encouraged to fork Bubble and build your own version** under the [GNU GPL-3.0](LICENSE) license!

---

## 1. Translating Bubble (No Coding Required)

You do not need Git, Android Studio, or coding experience to translate Bubble:

- **Live Browser Studio (1:1 UI Preview):** Open **[Bubble Translation Studio](https://apriononurhakim.store/Bubble-Android/)** and export `strings.xml` in one click.
- **Offline Spreadsheet:** Download the **[Excel Translation Template (.xlsx)](https://apriononurhakim.store/Bubble-Android/Bubble-v1.1.1-Translation-Template.xlsx)**.
- **Submit Your Translation:** Attach your exported file via the **[🌍 Submit Translation](https://github.com/AprionoN-Officials/Bubble-Android/issues/new?template=03_translation_submission.yml)** form.
- **Track Status:** See all supported and open languages in **[Localization Master Tracker #4](https://github.com/AprionoN-Officials/Bubble-Android/issues/4)**.

---

## 2. Reporting Bugs & Community Discussion

- Search existing [Issues](https://github.com/AprionoN-Officials/Bubble-Android/issues) before opening a new one.
- Use the **Bug Report** or **Feature Request** issue template and include your Android version, device model, and trigger mode (`Default Digital Assistant` vs. `AccessibilityService`).
- For general questions, sharing presets/custom engines, or community chat, join our official Telegram group: **https://t.me/BubbleAndroid**.

---

## 3. Contributing Code (Developers)

### Prerequisites
- **JDK 17**
- **Android SDK Platform 35** (`compileSdk 35`, `targetSdk 35`) & **Build-Tools 35.0.0**
- **Gradle Wrapper** (`./gradlew`, Gradle 8.9)

### Local Build & Test Workflow

```bash
git clone https://github.com/AprionoN-Officials/Bubble-Android.git
cd Bubble-Android

# Run unit tests (Debug & Release)
./gradlew testDebugUnitTest testReleaseUnitTest

# Build Debug APKs (arm64-v8a & armeabi-v7a)
./gradlew assembleDebug
```

### Pull Request Guidelines
- Branch off `main` with a descriptive branch name (e.g., `fix/ocr-crop-edge`, `feat/smart-unit-converter`).
- Keep Pull Requests focused on a single bug fix or scoped improvement that fits Bubble's lightweight design.
- Verify `./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug` passes cleanly before submitting.
- If adding or modifying UI strings in `app/src/main/res/values/strings.xml`, update `values-in/strings.xml` as well and preserve printf placeholders (`%1$s`, `%1$d`, etc.).
- Attach before/after screenshots or screen recordings for any UI or overlay change.
- Never commit `keystore/`, `*.jks`, `local.properties`, or temporary build artifacts.
- Respect Bubble's privacy rules: **no analytics/tracking SDKs**, **no `QUERY_ALL_PACKAGES`**, and always respect **`FLAG_SECURE`** & **Safe Mode**.

---

## Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all interactions within this repository.
