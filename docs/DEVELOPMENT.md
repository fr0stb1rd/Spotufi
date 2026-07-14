# Development

## Prerequisites

- **JDK 21 LTS** — AGP 9.x and Gradle 9.x require JDK 21+. Older JDKs will fail.
- **Android SDK 37** (`platforms;android-37.0`) — install via SDK Manager.
- **Git** — used for versioning (`git describe`).

## Build

```bash
# Debug APK (ABI-split, 5 APKs)
./gradlew assembleDebug

# Release APK (R8 + shrink)
./gradlew :app:assembleRelease --no-daemon -PversionName="X.Y.Z" -PversionCode=N

# Quick Kotlin compile check
./gradlew :app:compileDebugKotlin --warning-mode all

# Check for dependency updates
./gradlew dependencyUpdates --warning-mode all --no-parallel

# Run tests
./gradlew :spotify:test
```

### Build logs

Always pipe Gradle output to a log file:

```bash
./gradlew assembleDebug --warning-mode all 2>&1 | tee "build_logs/build_$(date +%Y%m%d_%H%M%S).log"
```

## Import

Open Android Studio → `File → Open` → select the project root. Wait for Gradle sync. Make sure JDK 21 is selected as the project JDK.

## Project structure

```
app/          → Main application (UI, playback, data layer, services)
spotify/      → Spotify Web API client (JVM lib)
innertube/    → YouTube InnerTube API client (Android lib)
```

For detailed source layout see `AGENTS.md`.

## Notes

- `compileSdk` mismatch: `:app`=37, `:innertube`=34 (intentional)
- Debug builds get `.debug` applicationId suffix and `-debug` versionName suffix
- Release signing uses debug key when `-PreleaseStoreFile` is not given
- `@Suppress` / `@SuppressLint` is never allowed — fix the warning instead
- No code comments
