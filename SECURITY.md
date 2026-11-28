# Security Policy

## Supported Versions

Only the latest stable release of **Bubble** receives security fixes:

| Version | Supported |
| :--- | :---: |
| `v1.1.x` (latest) | ✅ Yes |
| `< v1.1.0` | ❌ No |

## Reporting a Vulnerability

Because Bubble uses sensitive Android capabilities (`VoiceInteractionService`, `AccessibilityService`, optional `MediaProjection`, and an in-app `WebView`), we take security and privacy reports seriously.

**Please do NOT open a public GitHub Issue for security vulnerabilities.**

Instead, please report them privately using GitHub's **Private Vulnerability Reporting**:

1. Open the **Security** tab of this repository → **Advisories** → **Report a vulnerability**:
   - Direct link: https://github.com/AprionoN-Officials/Bubble-Android/security/advisories/new
2. Include as much detail as possible:
   - Bubble version, Android version, and device model
   - Trigger mode (`Default Digital Assistant` vs. `AccessibilityService`)
   - Clear reproduction steps or proof of concept
   - Potential security or privacy impact

### What to Expect
- Reports are reviewed on a **best-effort basis** as maintainer time permits.
- Confirmed security or privacy issues are prioritized for a patch release, and reporters will be credited in the advisory/release notes unless anonymity is requested.

## Security & Privacy Scope Notes
- Bubble strictly respects Android `FLAG_SECURE` and skips password fields (`isPassword` / password `InputType`).
- Full-screen capture snapshots stay in RAM only and are recycled when the overlay closes.
- Temporary visual search crops in `cacheDir/lens_temp/` expire automatically after 5 minutes and on app startup.
- Never commit signing keystores (`*.jks`, `*.keystore`) or private credentials.
