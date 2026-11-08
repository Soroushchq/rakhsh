# BUILD.md — Milestone 1: unmodified fork builds & runs with our own api_id

> Target: `./gradlew :TMessagesProj_App:assembleDebug` succeeds; APK installs and logs in
> with our own api_id. **No build has been executed in the analysis sandbox** (no Android
> SDK there — see README.md §"Sandbox limitation"); this guide was written from direct
> inspection of upstream `master` (shallow clone, 2026-11-07) and the upstream README.

## 1. Verified facts about upstream (as of 2026-11-07, master)

| Item | Value | Source (inspected) |
|---|---|---|
| Build system | Gradle 8.13 (`gradle/wrapper/gradle-wrapper.properties`) | inspected |
| AGP | `com.android.tools.build:gradle:8.13.2` (root `build.gradle`) | inspected |
| compileSdk / targetSdk | 36 | `TMessagesProj/build.gradle:113-139` |
| buildToolsVersion | `36.0.0` | `TMessagesProj/build.gradle:114` |
| minSdk | 23 | `TMessagesProj/build.gradle:138` |
| NDK (pinned) | `27.2.12479018` | `TMessagesProj/build.gradle:115` |
| Submodules | **19** (ffmpeg, libvpx, dav1d, boringssl, td, tgcalls, tlottie, libprisma, …) | `.gitmodules` |
| Launchable module | `:TMessagesProj_App` (`applicationId = APP_PACKAGE`) | `TMessagesProj_App/build.gradle:28` |
| Sample credentials | `BuildVars.java:29-30` → `APP_ID = 4` (server-limited sample) | inspected |
| `local.properties` | already gitignored (`.gitignore:6`) | inspected |
| README toolchain req. | Android Studio 2025.1.4 + NDK 27.2.12479018 + SDK 36 | upstream README |

## 2. Machine prerequisites (local build machine)

- JDK 17+ (Temurin 17 recommended; Gradle 8.13 / AGP 8.13 require ≥17)
- Android Studio 2025.1.4 or newer (open the project, do **not** import)
- Android SDK Platform 36 + Build-Tools 36.0.0 + CMake
- NDK **27.2.12479018** (AGP auto-installs the pinned version if licenses are accepted)
- Disk ≥ 25 GB free (source+submodules ≈ 2 GB, SDK/NDK ≈ 4 GB, Gradle caches ≈ 4-6 GB, native build artifacts ≈ 5+ GB), RAM ≥ 16 GB recommended, 4+ cores
  *(first full build incl. NDK typically takes 20-60 minutes)*

## 3. Clone (with the 19 submodules — mandatory)

```bash
git clone --recursive --shallow-submodules <YOUR_FORK_URL>.git "[APP NAME]"
cd "[APP NAME]"
# if you forgot --recursive:
git submodule init && git submodule update --init --recursive --depth=1
```

Upstream origin for tracking updates (do not rewrite history):

```bash
git remote add upstream https://github.com/DrKLO/Telegram.git
```

## 4. Own api_id (ToS §2.1 — mandatory, never committed)

1. Create the app's responsible account with an official Telegram app.
2. Log in at https://my.telegram.org → *API development tools* → fill the form.
   ⚠️ **One api_id per phone number** (S2). Use a reachable number — breach notices go there (ToS §4).
3. Create `local.properties` at the **repo root** (gitignored upstream):

```properties
# local.properties — NEVER commit (see patches/local.properties.example)
sdk.dir=<path to your Android SDK>
TELEGRAM_APP_ID=12345678
TELEGRAM_APP_HASH=0123456789abcdef0123456789abcdef
```

4. Apply the two patches from `patches/` (see §5) so the values flow
   `local.properties → BuildConfig → BuildVars` with a ~2-line diff.

## 5. Minimal fork patch: api_id via local.properties

### 5.1 `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` (lines 29-30)

```diff
-    public static int APP_ID = 4;
-    public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";
+    // [FORK] injected from local.properties via BuildConfig (ToS: own api_id; never committed)
+    public static int APP_ID = BuildConfig.TELEGRAM_APP_ID;
+    public static String APP_HASH = BuildConfig.TELEGRAM_APP_HASH;
```

This matches the existing upstream style in the same file (`DEBUG_VERSION = BuildConfig.DEBUG_VERSION`, `BuildVars.java:21`).

### 5.2 `TMessagesProj/build.gradle` → inside `android { defaultConfig { … } }`

```groovy
// [FORK] Telegram api_id/api_hash from root local.properties (gitignored)
def forkProps = new Properties()
def forkPropsFile = rootProject.file('local.properties')
if (forkPropsFile.exists()) {
    forkPropsFile.withInputStream { forkProps.load(it) }
}
buildConfigField 'int', 'TELEGRAM_APP_ID', (forkProps.getProperty('TELEGRAM_APP_ID') ?: '1')
buildConfigField 'String', 'TELEGRAM_APP_HASH', "\"${forkProps.getProperty('TELEGRAM_APP_HASH') ?: ''}\""
```

Ready-to-use files: `patches/BuildVars.java.diff`, `patches/app-secrets.gradle.snippet`.

> Do **not** touch: signing configs, keystores, `release.keystore`, google-services
> placeholders (upstream ships dummies for reproducible builds — fine for debug builds).

## 6. Build & verify

```bash
./gradlew :TMessagesProj_App:assembleDebug --stacktrace
# first build compiles ~20 native submodules; 20-60 min is normal
adb install -r TMessagesProj_App/build/outputs/apk/debug/TMessagesProj_App-debug.apk
```

**Milestone-1 acceptance checklist (execute on device):**
- [ ] App launches (no crash), splash shows
- [ ] Phone login with real number + code → succeeds **with our own api_id**
- [ ] Send & receive a message both ways with another account
- [ ] Open a channel (Sponsored Messages still render — baseline for ToS §3.3)
- [ ] `adb logcat` shows no MTProto/auth errors
- [ ] CI run green (see §7) with stub keys (build-only, login not expected in CI)

> ⚠️ Long-term use of the upstream sample `APP_ID = 4` risks `API_ID_PUBLISHED_FLOOD`
> for users (S2). Own api_id is mandatory before any distribution.

## 7. CI (GitHub Actions)

Copy `ci/android-ci.yml` → `.github/workflows/ci.yml` in the fork.
It checks out with recursive submodules, sets up JDK 17 + Gradle cache, injects
**stub** `TELEGRAM_APP_ID/HASH` (no secrets in CI), builds
`:TMessagesProj_App:assembleDebug`, and uploads the APK artifact.
Real-device login is *not* part of CI — it stays a manual acceptance step (§6).

## 8. Troubleshooting (common first-build issues)

| Symptom | Fix |
|---|---|
| `SDK location not found` | create `local.properties` with `sdk.dir=…` (§4) |
| License not accepted | `yes | sdkmanager --licenses` (or accept via Studio) |
| NDK version mismatch | AGP auto-downloads pinned `27.2.12479018`; ensure licenses accepted |
| `API_ID_INVALID` on login | api_id/hash missing or wrong in `local.properties` (§4/§5) |
| `API_ID_PUBLISHED_FLOOD` | you are on the sample id 4 — register your own api_id |
| OOM during native build | raise `org.gradle.jvmargs=-Xmx4g` in `gradle.properties`; close Studio while building |
| Submodule dirs empty | rerun `git submodule update --init --recursive --depth=1` |
