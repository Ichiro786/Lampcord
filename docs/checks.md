# Checks

Run these before opening a pull request.

```bash
# Shared logic and update tests
./gradlew :shared:desktopTest

# Desktop app compile
./gradlew :desktopApp:compileKotlinJvm

# Android compile
./gradlew :androidApp:assembleDebug
```

For a quick edit-only loop:

```bash
./gradlew :shared:compileKotlinDesktop :desktopApp:compileKotlinJvm
```

## Notes

- `:shared:desktopTest` compiles commonMain, desktopMain, and desktopTest.
- The native voice library is built by `:shared:buildVoiceDesktop` and is required by desktop tests.
- iOS compilation is covered by the macOS CI job.
- There is no ktlint or Android lint gate yet. Do not reformat unrelated files in a
  behavior change.
