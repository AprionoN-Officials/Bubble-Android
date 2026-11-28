> **Maintainer Scope & Pace Notice:**
> Bubble is maintained independently at the owner's own pace and design direction.
> - Please discuss large changes or new flows in an Issue before opening a PR so they do not disrupt Bubble's core UX.
> - Submitting a PR does **not** guarantee immediate review or acceptance.
> - If you need a feature urgently or prefer a different workflow/UI, you are **encouraged to fork Bubble and build your own version** under the GNU GPL-3.0 license!

## Summary
<!-- Briefly describe what this PR changes and why. -->

## Related Issue
<!-- Link the related issue, e.g. Closes #123 or Related to #4 -->
- Related / Closes: #

## Type of Change
- [ ] Bug fix
- [ ] Scoped improvement / new feature
- [ ] UI / UX change
- [ ] Translation / localization
- [ ] Documentation / CI

## Verification & Testing
<!-- Describe how you tested your changes and on which Android version / device. -->
- [ ] `./gradlew testDebugUnitTest testReleaseUnitTest` passes
- [ ] `./gradlew assembleDebug` builds cleanly
- Device / Android version tested:

## Screenshots / Screen Recording (if UI changed)
<!-- Attach before/after screenshots for any visual or overlay changes. -->

## Checklist
- [ ] My changes fit Bubble's core workflow and privacy rules (no tracking SDKs, no `QUERY_ALL_PACKAGES`, respects `FLAG_SECURE` & Safe Mode).
- [ ] Any new/edited strings preserve printf placeholders (`%1$s`, `%1$d`) and update `values-in/strings.xml` if applicable.
- [ ] No secrets, keystores, or temporary build artifacts are included.
- [ ] I agree that my contribution is licensed under GNU GPL-3.0.
