# Spotufi - Agent Guide

Multi-source Android music player. Jetpack Compose + Material 3, MVVM, Kotlin 2.4.0.
Spotify clone with lossless FLAC streaming (TIDAL monochrome DASH), YouTube audio matching, downloads, crossfade, and tri-state repeat.

## Build

```bash
./gradlew assembleDebug                                          # debug (ABI-split, 5 APKs)
./gradlew :app:assembleRelease --no-daemon                       # release (R8 + shrink)
./gradlew dependencyUpdates --warning-mode all --no-parallel      # check for newer deps
./gradlew :app:compileDebugKotlin --warning-mode all 2>&1        # quick compile check
```

**All local Gradle commands** must include `--warning-mode all` so deprecation
warnings are visible and can be addressed early.

CI release build:
```bash
./gradlew :app:assembleRelease --no-daemon -PversionName="X.Y.Z" -PversionCode=N
```

## Tests

```bash
./gradlew :spotify:test    # only real tests (spotify/src/test/)
./gradlew test             # placeholder tests in :app, real tests in :spotify
```

No lint/detekt/ktlint configured. `./gradlew lint` runs default AGP lint only.

## Modules

| Module | Type | compileSdk | Purpose |
|--------|------|------------|---------|
| `:app` | Android app | 37 | Main application (`io.github.sekademi.spotufi`) |
| `:spotify` | JVM lib | N/A | Spotify Web API client (`com.metrolist.spotify`) |
| `:innertube` | Android lib | 34 | YouTube InnerTube API client (`com.metrolist.innertube`) |

`:app` depends on `:spotify` and `:innertube`.

## Key Versions

AGP 9.2.1, Gradle 9.6.1, Kotlin 2.4.0, KSP 2.3.10, Hilt 2.60.1,
Compose BOM 2026.06.01, Media3 1.10.1, Ktor 3.5.1, Coil 3.5.0, OkHttp 5.4.0, Jsoup 1.18.1

All versions in `gradle/libs.versions.toml`. Compose BOM is inlined in `app/build.gradle.kts`.

## Rules

- **`@Suppress` / `@SuppressLint` never allowed.** Fix the code instead. If a warning has no modern replacement, restructure the code to avoid it.
- **Errors and warnings must be researched thoroughly.** Search the web for modern solutions. Read official documentation (Kotlin, Android, Compose, Ktor, Hilt, etc.) before making changes. Do not guess or apply quick fixes.
- **Semantic versioning (https://semver.org/) used.** MAJOR.MINOR.PATCH format. Breaking changes → MAJOR, new features → MINOR, bug fixes → PATCH.
- **JDK 21 LTS required.** AGP 9.x and Gradle 9.x require JDK 21+. Both local and CI builds must use JDK 21.
- **Gradle logs must be saved and reviewed.** Always pipe Gradle output to a log file (`build_logs/build_YYYYMMDD_HHMMSS.log`) and review it after execution. Logs must never be committed or pushed.

## Gotchas

- **compileSdk mismatch**: `:app`=37, `:innertube`=34. Intentional.
- **Release signing**: Uses debug key when no `-PreleaseStoreFile` given (sideload-friendly). See `app/build.gradle.kts:51`.
- **R8 enabled**: `isMinifyEnabled = true`. ProGuard rules at `app/proguard-rules.pro`.
- **Git-based versioning**: Local builds derive version from `git describe`. CI overrides via `-P`.
- **Debug builds**: Have `.debug` applicationId suffix and `-debug` versionName suffix.
- **Repositories must be in settings.gradle.kts**: `repositoriesMode = FAIL_ON_PROJECT_REPOS`.
- **Core desugaring required**: For `:innertube` (NewPipeExtractor).
- **No `org.jetbrains.kotlin.android` plugin**: AGP 9+ has built-in Kotlin support.
- **Gradle 9**: `project.exec` removed, use `providers.exec`. `applicationVariants` removed, use `androidComponents.onVariants`.
- **Ktor 3.5.1**: `response.body` is nullable.
- **Material3 1.4.0+**: `material-icons-core` no longer transitive, must be explicit dependency.
- **AGP 9+ Kotlin DSL**: Namespace in `build.gradle.kts` sets the manifest package; no `package=` in AndroidManifest.xml.
- **compileSdk 37**: Android 17 stable since June 2026. API 37 SDK (`platforms;android-37.0`) must be installed. `:app` uses 37, `:innertube` stays at 34.
- **Android 16 edge-to-edge mandatory**: `windowOptOutEdgeToEdgeEnforcement` is ignored on API 36+. `enableEdgeToEdge()` must be called. ✅ Already done in `MainActivity.kt`.
- **Android 16 predictive back default**: `onBackPressed` not called on API 36+. Use Compose `BackHandler` or `OnBackInvokedCallback`. ✅ `BackHandler` used in PlayerScreen.
- **Media3 `onPlaybackResumption()`**: Use instead of custom `PlaybackStatePref` for process-death playback restore. See `docs/H4ZH4N_MERGE_PLAN.md`.
- **`rememberModalBottomSheetState` deprecated**: `rememberBottomSheetState` replacement is only in Material3 1.5.0+ (not yet stable). Current BOM `2026.06.01` → Material3 1.4.0. **Blocked** — bump BOM when 1.5.0 stable drops. 4 files affected.
- **MediaSessionService extends LifecycleService** (Media3 1.10): Can use `lifecycleScope` instead of custom `CoroutineScope` in PlaybackService.
- **Room 3.0 announced**: KSP-only, Kotlin-only, new `androidx.room3` package. Wait for stable rather than using Room 2.x.

## Source Layout

```
app/src/main/java/io/github/sekademi/spotufi/
  audio/          # Crossfade, BiquadFilter, CrossfadeFilterAudioProcessor
  data/
    api/          # Api, LyricsApi, ProfileCache, SpotifySession, SpotifySync, TokenProvider
    entity/       # SongsModel, AccountModel, AlbumsModel, ArtistsModel, etc.
    preferences/  # SettingsPref, DownloadPref, ListeningHistoryPref, StreamCachePref, etc.
    recommendation/ # SpotifyRecommendationEngine
    update/       # UpdateChecker
  di/             # SongPlayer, StreamResolver, CrossfadeEngine, CurrentSongState, Palette, CandidateScorer, DownloadManager, SpotifyWebPlayer
  ui/
    components/   # AppComponents, UpdatePrompt, SongOptionsSheet, LikedSongsAlbum, SavedInSheet, BiographyParser, BiographyText
    navigation/   # MyNavHost, Routes, MainBottomNavigation
    notification/ # PlaybackService, MusicPlayerController, WebMediaPlayer
    repository/   # AppRepository
    screens/      # Album, Artist, Category, Downloads, History, Home, Library, LikedSongs, Lyrics, Player, Playlist, Queue, Search, Settings, Show, SpotifyLogin
    theme/        # Color, Theme (SpotuiTheme), Type
    viewmodel/    # Album, Artist, Category, Home, Library, LikedSongs, Lyrics, Player, Playlist, Search, Show
```

## Features (H4zh4n merge — v1.4.0 + CyberSecSleuth additions)

- **Tri-state repeat**: Off / One / All
- **Stream URL caching**: In-memory + persistent (SharedPrefs); skips re-resolution
- **Battery optimization bypass**: Optional — requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **Downloads**: Search, sort (date/title/artist), confirm-on-clear, offline access, auto-export to public Music/ folder
- **Download folder name**: Configurable via Settings → Updates (default "Spotufi")
- **Playlist**: Search + sort
- **Listening history**: Redesigned stats, top artists/tracks, interactive playback
- **Player**: Slide-to-dismiss gesture, YouTube resolution errors, resolution/buffering states
- **Notification**: Close button on media notification
- **PoToken timeouts**: Prevents background resolution hangs
- **Configurable update source**: Markdown release notes in update dialog
- **Library**: Maintainer attribution
- **Artist biography**: Jsoup-parsed HTML biography with clickable artist/album/track links

## Update Check

Version info stored in repo root `latest.txt` (single line, e.g. `1.1.1`).

`UpdateChecker` (`data/update/`) fetches from `raw.githubusercontent.com/{repo}/main/latest.txt` (no rate limit). App finds APK URL by trying `Build.SUPPORTED_ABIS` first, falls back to `universal`. No local cache — always live. Skip ("Don't show again") stored in SharedPreferences.

Called from:
- `UpdatePrompt.kt:42` — auto on app launch
- `SettingsScreen.kt:404` — manual "Check for updates" tap

## Release

Triggered by `v*` tag push. Workflow: `.github/workflows/release.yml`
1. Builds release APKs (5 ABI splits + universal) with R8
2. Generates SHA-256 checksums + GPG signatures
3. Creates GitHub Release with auto-generated notes
4. Updates `latest.txt` to the new version and pushes to `main` (so `main` always has the latest version)

Release naming: Gradle default (`app-arm64-v8a-release.apk`), no renaming.
