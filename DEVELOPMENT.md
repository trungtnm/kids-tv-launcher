# Development guide

For anyone who wants to build, change or extend Kids TV Launcher, and for the person
installing it on a box. The guide for parents is [README_EN.md](README_EN.md)
([Tiếng Việt](README.md)).

## Overview

A single Android app (`com.kidstv.launcher`) written in Kotlin and Jetpack Compose, with
no backend. minSdk 28, targetSdk 34.

| Part | File | Role |
|---|---|---|
| Home screen | `LauncherActivity.kt` | App grid, profile picker, time-lock state, QR code of the admin page |
| Lock screen | `LockActivity.kt` | Shows why the TV is locked, PIN entry and creation, granting extra time |
| UI components | `Ui.kt` | Backdrop, focusable cards, buttons, PIN pad, lock illustrations, QR code (ZXing) |
| Texts | `Strings.kt` | Every text shown on the TV or returned to the web page, in Vietnamese and English |
| Guard service | `GuardService.kt` | Accessibility service: watches the foreground app, blocks, counts time, sleep timer, wake |
| Policy | `Store.kt` | Profiles, PIN, login tokens, viewing time, language, the `decide()` function |
| App list | `Apps.kt` | Lists and launches apps, loads banners and icons |
| Web server | `WebServer.kt` | NanoHTTPD on port 8765, JSON API, serves the admin page |
| Admin page | `assets/web/index.html` | One HTML file with plain JavaScript and no build step |

The Kotlin files live in `app/src/main/java/com/kidstv/launcher/`.

### How blocking works

1. The system sends `TYPE_WINDOW_STATE_CHANGED` to `GuardService` whenever a new window
   comes to the front.
2. The service ignores system surfaces (`Store.isSystemSurface`), records the package in
   `Store.currentPkg`, then calls `Store.decide(pkg)`.
3. On `Verdict.Block`, the service opens `LockActivity` on top. For time locks (pause,
   bedtime, daily quota) the blocked app is also closed so playback stops.
4. A 15-second tick adds viewing time to the active profile and re-checks the policy.
   Commands from the web page call `recheck()` so they apply at once instead of waiting
   for the tick.

Order of precedence in `decide()`: parent mode → pause → extra time (`overrideUntil`) →
bedtime → daily quota → allowed app list.

### Storage

Two JSON files in the app's `files` directory, written to a temporary file and renamed:

- `policy.json`: PIN salt and SHA‑256 hash, login tokens, profiles, active profile, pause
  flag, `overrideUntil`, `sleepAt`, `lang`.
- `usage.json`: seconds watched per day, profile and package; the last 14 days are kept.

Parent mode lives only in memory and ends after 15 minutes, when the screen turns off,
or when the process restarts.

### Languages

The app has its own language setting (`Store.lang`, `"vi"` or `"en"`) instead of
following the system locale, so parents can change it from the phone and the TV follows
immediately. Until it is set, it defaults to Vietnamese on a box whose system language
is Vietnamese and to English otherwise.

- TV texts and web error messages are entries of the `S` enum in `Strings.kt`; use
  `S.NAME.text(args)`.
- The admin page has its own `TEXT` table in `index.html`; use `t('key', args)`. It takes
  the language from `/api/status` and `/api/state`.
- When you add a text, add both languages in the same place.

The only text that follows the system locale is the accessibility service description in
`res/values/strings.xml` and `res/values-vi/strings.xml`, because Android's own Settings
screen shows it.

## Requirements

- JDK 17
- Android SDK: `platforms;android-34`, `build-tools;34.0.0`
- `adb`

On macOS with Homebrew:

```sh
brew install openjdk@17 android-platform-tools
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew assembleRelease
```

The result is `app/build/outputs/apk/release/app-release.apk` (about 1.7 MB, R8 enabled).

The release build is signed with the build machine's debug key. That is enough for
sideloading onto a device at home, but every update must be signed with the same
`~/.android/debug.keystore`; otherwise `adb install -r` refuses it and the app has to be
uninstalled, which loses its data.

## First install on a box

The install file `kids-tv-launcher.apk` is on the
[Releases](https://github.com/trungtnm/kids-tv-launcher/releases/latest) page, or build
it as described above.

**1. Turn on debugging on the box**

On the TV, open Settings → Device Preferences → About and press "Build" 7 times. Go
back, open "Developer options" and turn on "USB debugging" (on some boxes "Network
debugging"). Note the box's IP address under Settings → Network.

**2. Install `adb` on a computer**

- Mac: `brew install android-platform-tools`
- Windows and Linux: download "SDK Platform-Tools" from the Android Developers site and
  unpack it.

**3. Install the app and enable the guard service**

On the first command the TV asks whether to allow this computer; choose "Always allow"
and press OK.

```sh
adb connect <BOX_IP>:5555
adb install -r kids-tv-launcher.apk
adb shell settings put secure enabled_accessibility_services com.kidstv.launcher/com.kidstv.launcher.GuardService
adb shell settings put secure accessibility_enabled 1
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell input keyevent KEYCODE_HOME
```

The fifth command disables Google's stock launcher so the Home button always lands on
the new home screen. The TV now shows "Create a parent PIN"; the remaining steps are in
[README_EN.md](README_EN.md#setup).

**4. Turn on the family filter (recommended)**

Block adult sites in every app with Cloudflare's family DNS:

```sh
adb shell settings put global private_dns_specifier family.cloudflare-dns.com
adb shell settings put global private_dns_mode hostname
```

**5. Turn debugging off**

The app does not need debugging to run; everything set above survives turning it off
and survives reboots. Turn "USB debugging" off in "Developer options" when you are done,
and turn it back on only to update or debug the app.

## Updating on a box

```sh
adb connect <BOX_IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

An update keeps the PIN, profiles and viewing time. After `adb install -r` the system
re-binds the accessibility service within a few seconds; check with:

```sh
adb shell dumpsys accessibility | grep "Bound services"
```

Two things to know when reinstalling:

- The process restarts, so an active parent mode ends.
- Viewing time not yet written to disk (up to about a minute) is lost.

## Uninstall

```sh
adb shell pm enable com.google.android.tvlauncher
adb uninstall com.kidstv.launcher
adb shell settings put global private_dns_mode off
```

The box returns to Google's stock launcher. Uninstalling erases the PIN and every
profile, so it is also how to recover from a forgotten PIN: uninstall, then install
again from step 3 of the first install.

## Debugging

The app logs with the tag `KidsTV` for every web command, every block and every wake
request:

```sh
adb logcat -s KidsTV
```

Other useful commands:

```sh
# App in the foreground
adb shell dumpsys activity activities | grep mResumedActivity
# Whether the box is awake or asleep
adb shell dumpsys power | grep mWakefulness=
# Recent wake and sleep reasons
adb logcat -d | grep -E "Waking up|Going to sleep"
# Capture the TV screen
adb exec-out screencap -p > screen.png
```

The video layer of streaming apps usually captures as black. To see whether a video is
playing, use `adb shell dumpsys media_session`.

### Trying the admin page without a box

`index.html` only calls `fetch` on `/api/*`. To view it on a computer, make a copy of
the file and insert, before the first `<script>` tag, a snippet that sets
`localStorage.token` and `localStorage.tab` and replaces `window.fetch` with a function
returning a sample state object (set its `lang` to `"vi"` or `"en"`). The images
`docs/screenshots/web-*.png` and `docs/screenshots/en/web-*.png` were rendered this way
with headless Chrome:

```sh
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new \
  --force-device-scale-factor=2 --window-size=500,1190 \
  --screenshot=out.png file:///path/to/the-copy.html
```

Headless Chrome does not allow a window narrower than 500 px; a capture at a smaller
width is cropped on the right rather than showing a page that overflows.

## Admin page API

Every call except `status` and `login` needs the `X-Token` header. Request bodies are
UTF‑8 JSON.

| Method | Path | Content |
|---|---|---|
| GET | `/api/status` | `hasPin`, `lockoutSec`, `lang` |
| POST | `/api/login` | `{"pin": "..."}`, returns `token` |
| GET | `/api/state` | Profiles, lock state, running app, viewing time, app list, `lang` |
| POST | `/api/policy` | Saves `profiles` and `switchLocked`, returns the state |
| POST | `/api/action` | `{"type": ...}`, returns the state |
| GET | `/api/icon?pkg=…&token=…` | PNG icon of an app |

| `type` | Parameters | Effect |
|---|---|---|
| `pause`, `resume` | | Pause the TV or let it continue |
| `extra` | `minutes` | Ignore the quota and bedtime for that many minutes; 0 cancels |
| `profile` | `id` | Switch the active profile |
| `lock` | | End parent mode |
| `home` | | Go to the home screen |
| `close` | | Go to the home screen and close the running app |
| `wake` | | Wake the box |
| `launch` | `pkg` | Launch an app if the policy allows it |
| `youtube` | `url` | Play a video in `com.google.android.youtube.tv` |
| `sleep` | `minutes` | Set the sleep timer; 0 cancels |
| `lang` | `lang` | Set the language of the TV and the admin page: `vi` or `en` |
| `pin` | `current`, `pin` | Change the PIN; revokes every token and returns a new one |

Errors come back as `{"error": "..."}` with status 400, 401 (bad token; the page signs
out), 403 (wrong current PIN when changing it) or 404. Five wrong PINs lock PIN entry
for 60 seconds, shared between the web page and the TV lock screen.

## Lessons from the real device

These decisions come from testing on an Amlogic box running Android TV 12. Keep them
when changing the code.

- **No Device Owner.** The firmware does not declare `android.software.device_admin`,
  and the system allows a single user, so Android TV's built-in restricted profile is
  not available either. All locking relies on the accessibility service.
- **The stock launcher must be disabled.** `cmd package set-home-activity` reports
  success, but the Home button keeps going to `com.google.android.tvlauncher` until that
  package is disabled.
- **Do not call `GLOBAL_ACTION_HOME` before opening the lock screen.** The two race and
  the home screen can cover the lock screen. `LockActivity` goes home by itself when it
  closes.
- **Read the request body as UTF‑8 by hand.** NanoHTTPD's `parseBody` decodes as
  US‑ASCII when the header names no charset, which corrupts Vietnamese. See
  `WebServer.body()`.
- **Wake with `setTurnScreenOn`.** A wake lock with `ACQUIRE_CAUSES_WAKEUP` is ignored
  by the firmware. `GuardService.wake()` starts `LauncherActivity` with `EXTRA_WAKE`, and
  the activity asks for the screen itself.
- **Turn the screen off with `GLOBAL_ACTION_LOCK_SCREEN`.** A TV has no lock screen, so
  this action puts the box to sleep. The box keeps Wi‑Fi and the web server still
  answers while asleep.
- **Never run `adb root`.** On this firmware it stops the ADB service until the box is
  rebooted.
- **Only pass a rebuilt URL to an intent.** `youtubeId()` extracts the 11-character
  video id and rebuilds a standard YouTube link, so pasted text never reaches the TV
  directly.

## Known limits

- The admin page uses unencrypted HTTP; the PIN and token travel in the clear on the
  local network.
- Login tokens do not expire; they are revoked only when the PIN changes.
- The app does not filter content inside individual apps.
- A first install needs a computer and `adb`, because disabling the stock launcher can
  only be done from a shell.
- There are no automated tests. Testing is manual, on a box through `adb` and the web
  page.

## Conventions

- Commits follow conventional commits (`feat:`, `fix:`, `docs:`).
- Do not commit `local.properties`, build directories, or backups taken from a device
  (`device-backup/` is already in `.gitignore`).
