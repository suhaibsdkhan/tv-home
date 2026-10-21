# tv-home

**Make a slow, ad-filled Android TV fast and clean again.** Debloat it over Wi-Fi and give it a
tiny Apple TV-style home screen with rotating nature wallpapers. You can hand the whole thing to
Claude Code with one prompt, or do it by hand with the scripts here.

![Before: Google TV home with a sponsored hero. After: clean dock over a Lake Louise wallpaper](before-after.png)

On a 2 GB TCL Google TV this disabled 25 bloat apps and cut the home screen's memory use from
192 MB to 83 MB. Everything is reversible: packages are disabled, never uninstalled.

## Do it with Claude Code (easiest)

1. On the TV: Settings → System → About → press OK on **Build** 7 times → back → Developer options
   → turn on **USB debugging**. Keep the TV on, on the same Wi-Fi as your computer.
2. Clone this repo, open Claude Code inside it (so it can reuse the launcher and scripts), and paste:

   ```text
   My Android TV is slow and full of ads. Find it on my Wi-Fi, connect over ADB, and debloat it:
   disable the manufacturer's ads, telemetry and junk (reversibly, never uninstall), asking me first
   about apps and features I might use. Then build and install a fast, minimal Apple TV-style home
   screen with a dock of my favourite apps and rotating nature wallpapers. Back up before changing
   anything, check every step with screenshots from the TV, and give me a restore script.

   Heads-up from people who've done this: use the adb-shell Python package if macOS blocks adb; on
   Google TV also disable com.google.android.tungsten.setupwraith or it hijacks the Home button; and
   test the Home button from inside an app.
   ```

3. Accept "Allow USB debugging?" on the TV when asked, then answer a few questions: which apps you
   use, which go in the dock, whether you want wallpapers.

The prompt is short on purpose; Claude works out the details. The gotchas below are there in case it gets stuck.

## Do it by hand (macOS)

```bash
./setup.sh                                                  # Python ADB client, JDK 17, Android SDK
tools/find-tv.sh                                            # finds the TV, saves its IP
tools/venv/bin/python -I tools/tv.py --probe                # model, Android version
tools/disable.sh examples/tcl-google-tv-g07/debloat.txt     # or your own list, see examples/
launcher/build-and-install.sh                               # build + install the launcher
```

Undo everything with `tools/restore.sh <the same list>`.

## What's here

| Path | What it is |
|---|---|
| [PROMPT.md](PROMPT.md) | The prompt for Claude Code / any coding agent |
| [launcher/](launcher/) | The home screen. 4.7 MB including the wallpapers; plain Android, no network, no background services |
| [launcher/res/values/config.xml](launcher/res/values/config.xml) | Dock apps, hidden apps, Live TV app, grid columns |
| [tools/tv.py](tools/tv.py) | Pure-Python ADB client: shell, batch, screenshots, chunked APK push |
| [tools/find-tv.sh](tools/find-tv.sh) | Finds Android TVs on your Wi-Fi |
| [tools/type.sh](tools/type.sh) | Type into the TV's on-screen keyboard from your laptop (`tools/type.sh` alone prompts hidden, for passwords) |
| [tools/disable.sh](tools/disable.sh), [tools/restore.sh](tools/restore.sh) | Apply / undo a debloat list |
| [examples/](examples/) | Debloat lists per TV model (plus a full restore script for the TCL G10) |
| [tools/profiles.sh](tools/profiles.sh) | Create, list, wake and remove the Google TV profiles behind "Who's watching?" |
| [tools/aerial-wallpapers.sh](tools/aerial-wallpapers.sh) | Optional: swap the wallpapers for stills from Apple TV's aerial screensaver (macOS, personal use) |

## The launcher

- Clock, greeting and date top-left. Frosted pills top-right for **Inputs** (HDMI/AV/Live TV
  dropdown; the remote's Menu key opens it too), **Bluetooth** (pair remotes, headphones, speakers),
  **Wi-Fi** (shows the network name, or "Ethernet" when wired) and **Settings**.
- A dock of your favourite apps. The focused app's name appears above it over a glow coloured from
  its banner. Press Down for every other app in a grid.
- 12 nature photos crossfade every 10 minutes, with a scrim so text stays readable. Swap them in
  `launcher/assets/wallpapers/` (1920×1080 JPEGs, credits in `launcher/WALLPAPER-CREDITS.md`).
- Deterministic D-pad focus, remembers the last tile, picks up newly installed apps on return.
- **Who's watching?** (optional, Google TV on Android 12+): a frosted picker on power-on and wake,
  with an illustrated avatar per person and a Guest. Each person other than the main user is a Google
  TV profile with its own app data, so Netflix, Spotify, YouTube, Disney+… open in **their own
  account**, and Guest's apps are signed out. Each person's dock fills with their most used apps.
  Set up: `tools/profiles.sh add <name> <debloat list>` per person (and one for Guest), list them in
  `launcher/local/values/people.xml` (git-ignored) as `Name|user id` / `Guest|id|guest`, rebuild.
  Avatar photos: `launcher/local/drawable-nodpi/avatar_<name>.jpg`.
- **Remote button remap**: an optional accessibility service that makes the remote's app buttons
  open what you want, e.g. the YouTube button opens SmartTube. Set `key_remaps` in `config.xml`
  (`keycode=package`, `action:…`, `component:…` or `tvinput:<input id>`), then turn it on once in
  Settings → Accessibility → Remote button remap. Find a button's keycode with
  `tools/venv/bin/python -I tools/tv.py "logcat -c"`, press it, then
  `tools/venv/bin/python -I tools/tv.py "logcat -d | grep handleKeyBeforeQueueing"`.

Change the dock: edit `config.xml`, then run `launcher/build-and-install.sh`.

## Gotchas we hit

- **macOS blocks the `adb` binary** on the local network ("No route to host"). `tools/tv.py` uses
  the `adb-shell` Python package instead.
- **Google TV hijacks the Home button.** With the stock launcher disabled, the setup app
  (`com.google.android.tungsten.setupwraith`) has a higher HOME priority than any third-party
  launcher. The install script disables it; `restore.sh` re-enables it.
- **Home opened the launcher behind the current app**, because of a stale home task from switching
  launchers. Force-stopping the launcher and `com.android.tv.settings` and pressing Home fixes it.
  The install script does this.
- **Chained `pm` commands** (`pm disable a; pm disable b`) can silently stop after the first. The
  scripts run one per shell call.
- **Apps installed from the Play Store didn't appear**, because the package broadcast was missed
  while another app was in front. The launcher re-checks with `getChangedPackages` on every return.
- **The manufacturer's remote buttons stop working** once the app they're hard-wired to, or the
  promo service that decides what they open, is disabled. On TCL the YouTube, Netflix, Prime Video
  and Channel buttons go dead, and Settings / the profile button did nothing once the Google TV home
  was gone. The remote button remap fixes all of them.
- **TCL turns the remap off at every power cycle**: TCL's app-boot manager refuses to start
  third-party services without its `APP_AUTO_START` app-op. The install script allows it, and the
  launcher re-enables the service on start (it's granted `WRITE_SECURE_SETTINGS` over ADB).
- **Live TV is stuck on "Searching for signal…"** if you disable `com.tcl.channelplus`: TCL's free
  channels feed Live TV. Keep it.
- **Per-person accounts need profiles, not just a picker**: apps keep one login per Android user, and
  a home screen can't switch users. Google TV profiles are *profiles* (they run beside the main user),
  and a launcher may open apps inside them with `LauncherApps`, but only apps with a phone-style
  launcher entry. TV-only apps (Prime Video, Crave, Apple TV, Max) open the main user's copy, where
  their own profile picker separates people, and are hidden from Guest.
- **Profiles sleep after a full restart** (unplugged, update), and only ADB can start them:
  `tools/profiles.sh wake`. Turning the TV off with the remote is standby and keeps them running.
- **A force-stop (e.g. reinstalling) unbinds the remap service** without turning it off in Settings.
  The launcher notices and re-applies the setting so Android binds it again.
- **The home screen ran at ~8 fps** on a 4K TCL: its GPU can't repaint a full-screen photo, scrim and
  glow at 3840x2160 on every frame of a focus animation (~150 ms a frame; Settings, with a flat
  background, ran fine). The background now lives on its own 1080p `SurfaceView`, redrawn only when
  it changes and scaled by the display hardware: 17 ms a frame. Live blurs (`RenderEffect`) have the
  same problem, so "Who's watching?" frosts a 64-pixel thumbnail instead.
- **The TV can't be found** on apartment, office or guest Wi-Fi that isolates devices from each
  other. Put the TV and computer on a phone hotspot for the setup; everything sticks when the TV
  moves back. Uploads over a hotspot can time out mid-way: rerun, the APK's md5 is checked.
- **Unsplash blocks scripted downloads** with a bot check. The wallpapers come from Wikimedia
  Commons Featured Pictures instead.

## Supported TVs

Android TV and Google TV: TCL, Xiaomi/Redmi, Hisense, Sony, OnePlus, Realme, Nvidia Shield,
Chromecast with Google TV and most others. Not Samsung (Tizen), LG (webOS) or Fire TV.
Tested on: TCL Google TV G07 (Android 11) and G10 (Android 12). Add yours to `examples/`.

## Credits

Based on [mihirgupta0900/tv-home](https://github.com/mihirgupta0900/tv-home), inspired by [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Wallpaper photos are
from Wikimedia Commons under their own licences, see
[launcher/WALLPAPER-CREDITS.md](launcher/WALLPAPER-CREDITS.md). The font is [Inter](https://github.com/rsms/inter) (SIL Open Font License, [launcher/assets/fonts/OFL.txt](launcher/assets/fonts/OFL.txt)), an open stand-in for Apple's San Francisco, which is licensed for Apple devices only. Code is MIT, see [LICENSE](LICENSE).
