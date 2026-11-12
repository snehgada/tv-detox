# tv-detox: Android TV debloat + Calm Home

Reversible Android TV debloat over ADB, plus Calm Home, a minimal Apple TV-style launcher
with nature and anime themes. One launcher, two themes, two TVs. Every script takes a TV name.

| TV (`devices/…`) | Hardware | ADB | Theme |
|---|---|---|---|
| `bedroom` | Banoika (Skyworth) FHD Streaming Stick, Google TV / Android 14, 1 GB RAM, on a projector | `192.168.29.87:5555` | `anime` |
| `living-room` | Haier haierATVnippori TV (MediaTek), Android TV 11, ~900 MB RAM, HDMI inputs + tuner | `192.168.29.158:5555` | `nature` |

If an IP changes, run `adb mdns services` and update `devices/<tv>/device.conf`.
Never put the Bedroom stick into standby over ADB: on the projector it leaves a blank
screen that needs a power cycle.

## Undo everything on a TV

```bash
./scripts/restore.sh living-room
```

This re-enables every package we disabled (the setup wizard last), gives HOME back to the
stock launcher, restores the screensaver settings, then verifies against
`devices/<tv>/backup/00-original`. Add `--remove-launcher` to also uninstall Calm Home, or
`--dry-run` to preview. Tested on both TVs on 2026-10-24: package state matched the original
backup exactly. Reboot tested on both TVs too: each booted straight into Calm Home, with every
disable kept.

## Apply again (after a restore, or if a firmware update undoes things)

```bash
./scripts/apply.sh bedroom
```

The order matters. The setup wizard must be disabled before the stock home, or the
wizard's RecoveryActivity grabs HOME and loops (black screen). `apply.sh` enforces this.

## Switch a TV's theme, or update Calm Home

```bash
./launcher/build.sh nature
```

Set `theme=` in `devices/<tv>/device.conf`, then run `./scripts/install-launcher.sh <tv>`.
Bump `VERSION_CODE` (env var) on every rebuild that you install; it's currently 15. Both
themes use the same package and key, so a TV can switch between them freely.

## What was disabled (`pm disable-user`, never uninstalled)

| | Bedroom (`devices/bedroom/lists/`) | Living Room (`devices/living-room/lists/`) |
|---|---|---|
| phase 1 | Sony LIV, Zee5, ShemarooMe, Play Games; Google feedback + RLZ pings; Privacy Sandbox ad services; Skyworth telemetry, partner promos, factory-test daemon, LED service | Haier TV+ portal, Play Movies & TV, Shahid; Anoki ACR viewing tracker; store demo mode, factory test, Haier evo installer, Google Play Music; Google feedback + RLZ pings; Haier SmartDevice, Google Smart Connect |
| phase 2a | setup wizard | setup wizard |
| phase 2 | Google TV home (`launcherx`), ambient screensaver | Android TV home (`tvlauncher`), its recommendations service, Haier's launcher promo config, Backdrop screensaver |

Kept on purpose: OTA updaters, Assistant/voice search, Chromecast, the phone-remote
service, the TV/input/tuner stack on the Haier, Play Games on the Living Room TV, and
every app you installed yourself.

## Calm Home (`launcher/`)

- Plain Java with no Gradle and no libraries. It requests **no permissions**, not even internet.
- `res/` is shared; `themes/<name>/res/` is linked on top as an overlay. The overlay holds
  the wallpapers, captions/accents, the first-run dock order, fonts and styles, the badge
  layout, scrims and the dock glass.
  - **nature**: 7 CC0/public-domain landscapes from Wikimedia Commons, a light clock, a white
    focus ring and a frosted dock. The screensaver is "Calm Nature".
  - **anime**: 9 dark Dragon Ball Z / Naruto / Attack on Titan wallpapers from wallhaven.cc,
    with a per-series accent (orange / red / green). That accent colours the focus ring, the
    glowing aura, the clock and the series badge. Labels use the Bangers font (OFL). The
    screensaver is "Calm Anime". The art is copyrighted: fine on your own TV, but
    **don't share that APK**.
- Dock: hold **OK** (or press **Menu**) on a tile to Move left/right, Remove, or open App info.
  **All apps** opens the grid; hold OK there to Add to Dock. On TVs with an input picker
  (the Haier) an **Inputs** tile opens the same source list as the remote's Input key.
- To swap wallpapers: put 1280×720 JPEGs in `themes/<name>/res/drawable-nodpi/`, then keep the
  `wallpapers`, `wallpaper_captions` and `wallpaper_accents` arrays in that theme's
  `theme.xml` in the same order. Credits are in each theme's `WALLPAPER-CREDITS.tsv`.
- Signing key: `~/.android/calmhome.keystore`, with its password in `…keystore.pass` (mode 600).
  Keep both: Android only accepts updates signed with the same key.

## Not in git

These stay on the Mac only (see `.gitignore`):
- **Anime wallpapers** (`launcher/themes/anime/res/drawable-nodpi/`): copyrighted art. To build
  the anime theme from a fresh clone, download the 9 images listed in
  `themes/anime/WALLPAPER-CREDITS.tsv`, crop them to 1280×720 JPEG, and save them there under
  the listed names (`wp_dbz1.jpg` … `wp_aot3.jpg`). The nature theme builds as-is.
- **`devices/<tv>/backup/` and `devices/<tv>/screens/`**: settings dumps, device serials and
  screenshots of signed-in accounts. `restore.sh` needs `backup/00-original`, so keep a copy
  of it somewhere safe.
- **`launcher/build/`** and the signing key in `~/.android`.

## Files

- `devices/<tv>/device.conf`: the TV's serial and theme
- `devices/<tv>/backup/00-original/`: the pre-change snapshot (packages, all settings, HOME,
  meminfo) with SHA256SUMS. `01-after-restore-test/` is the snapshot after the round-trip test.
- `devices/<tv>/state/disabled-by-us.txt`: what `restore.sh` re-enables
- `devices/<tv>/screens/`: screenshots from every step (`./scripts/shot.sh <tv> <name>`)

## Metrics (2026-10-24)

From `dumpsys meminfo` in `backup/00-original` vs `backup/meminfo-after-20261024-1853.txt` (PSS).

| | Bedroom | Living Room |
|---|---|---|
| Memory held by the processes we removed (before) | 166 MB | 215 MB |
| Calm Home + leftover shells (after) | 34 MB | 38 MB |
| Net always-resident memory freed | ~132 MB (14% of RAM) | ~177 MB (19% of RAM) |
| Memory status | normal → normal | **critical → normal** |
| Apps Android keeps cached for instant switching | 8 → 8 | 1 → 16 |

On Android 11 the Haier still starts three disabled *persistent* apps at boot (Anoki ACR,
Haier SmartDevice, factory test), but only as empty processes. Their components stay
disabled, ACR went from 52.5 MB to 3.2 MB, and it sent no network traffic after boot.

Living Room cold starts after the change (ms, 3 runs): Calm Home 1212/622/514,
YouTube 881/662/566, Netflix 719/506/499, JioHotstar 2772/1601/1410. No launch times were
recorded before the change, so these can't yet be compared.

## Notes

- Bedroom RAM in use: 815 MB before, 717 MB after a reboot (with YouTube and Netflix running).
- The Haier's screenshots come back empty while live TV/HDMI video is on screen (MediaTek
  protection). Its input picker auto-switches to the highlighted input after ~3 s, the same
  as with the remote.
- After a restore, the Haier keeps a harmless `user_rotation=0` system setting that Android
  writes when a landscape-only app opens.
- After a reboot, the Haier's network ADB took a few minutes to come back, even though the TV
  was already on Calm Home. If it was in standby when rebooted, it boots back into standby with
  Wi-Fi off until someone presses Power.
- A factory reset wipes all of this (Calm Home included) and brings back stock.
- "Wireless debugging" turns itself off on reboot. Port 5555 (network debugging) stays
  on, which is what these scripts use.
