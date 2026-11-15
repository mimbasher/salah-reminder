# Contributing

Pull requests, bug reports and feedback are welcome. This file is everything you need to
build, run and test the app; the [README](README.md) is for people who just want to use it.

The most useful contribution is often not code. Prayer times are an argument with an
authority, not only with the sun: if the app disagrees with the timetable on your mosque
wall by more than a minute, open an issue with your city, the method and both sets of times.
Reports from phones that delay alarms (Samsung, Xiaomi, Oppo, Huawei) help just as much.

## 📐 The rules the app is built around

Three constraints shape every change, and a pull request that breaks one needs a good
argument first:

- **No third-party libraries.** The whole app is the Android framework. It keeps the APK
  small, the permissions honest and the build free of a dependency tree to audit.
- **Nothing runs in the background.** Android wakes the app at each prayer time; between
  times it does nothing and uses no battery. No foreground service, no polling.
- **It works offline.** Once a location is set, nothing the app needs is on a network.

## 📱 One layout, every screen

No screen locks its orientation and every one is resizable, so the same code has to lay
itself out on a phone held upright, a phone on its side, a folding phone mid-fold, a tablet
and a split-screen window. There are no XML layouts: `Ui` reads the **window's** width in dp
(`Configuration.screenWidthDp`, never the display's, which is a different number in split
screen) and the screens build themselves from it.

| Width | What it is | What changes |
| --- | --- | --- |
| under 600dp | a phone held upright | one column, capped at 480dp and centred |
| 600dp and over (`Ui.wide`) | a phone on its side, an open foldable, a small tablet | home puts the hero beside the list; Qibla puts the dial beside the readout |
| 840dp and over (`Ui.expanded`) | a large tablet, a desktop window | settings deals its cards into two columns (`Ui.columnise`) |

Three rules to keep to when you add to a screen:

- Size in `Ui.dp(...)` or sp, never in pixels, and ask `Ui.wide` / `Ui.expanded` rather than
  testing for a device.
- Add a section with `Ui.sectionTitle`, which tags it so `Ui.columnise` knows it may cut there.
- A screen is rebuilt from scratch on every rotation and every fold, so anything the user has
  typed or chosen must come back from `SharedPreferences`, from `onSaveInstanceState`, or from
  a view with an id in `res/values/ids.xml`.

Worth checking a change against: a small phone (360×640), a phone on its side, the emulator's
7.6" foldable in both states, and a 10" tablet. `./scripts/dev.sh emulator` opens the first.

## 🏃 Running it locally (no Android Studio)

`scripts/dev.sh` fetches its own JDK 21, Gradle and Android SDK into `.toolchain/`
(git-ignored, nothing installed system-wide) and installs straight onto a plugged-in phone:

```sh
./scripts/dev.sh emulator   # open a simulated phone on this computer, app installed
./scripts/dev.sh install    # build + install + launch on a plugged-in phone
./scripts/dev.sh build      # -> app/build/outputs/apk/github/debug/app-github-debug.apk
./scripts/dev.sh bundle     # -> app/build/outputs/bundle/playRelease/app-play-release.aab
./scripts/dev.sh logs       # follow the app's logcat
./scripts/dev.sh stop       # close the emulator
```

An Android app is not served over localhost like a website — it needs a phone, real or
simulated. `emulator` gives you the simulated one in a window you can click; the first run
fetches the emulator and an Android 16 image (~1.5 GB, once).

For `install`, turn on **Developer options → USB debugging** on the phone and tap *Allow*
when it asks. For wireless, use **Wireless debugging** and
`.toolchain/sdk/platform-tools/adb pair <ip>:<port>`.

With your own SDK already installed, plain Gradle works as well:

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assembleGithubDebug
gradle testGithubDebugUnitTest   # the unit tests, also run on every CI build
```

There is no Gradle wrapper on purpose: CI pins Gradle 9.6.0 and `scripts/dev.sh` fetches its own
9.6.0 plus a JDK 21, so a committed `gradlew` would only add a third version to disagree with.

## 🗺 Where everything is

No third-party libraries — the whole app is the Android framework plus the files in
`app/src/main/java/com/salah/reminder/`:

| File | Role |
| --- | --- |
| `MainActivity` | home dashboard |
| `SettingsActivity` | all configuration |
| `QiblaActivity` | compass |
| `Ui` | colours, type and the shared view builders |
| `ThemedActivity` | applies the light/dark choice before a screen is built |
| `PrayerCalc` | astronomy: prayer times and Qibla bearing |
| `Region` | which authority's timetable each country keeps |
| `Scheduler` | alarms, state and notifications |
| `AdhanService` | plays the adhan |
| `AlarmReceiver` / `BootReceiver` | alarm delivery and restoring after reboot |
| `CityPickerActivity` | the bundled city list |
| `Updater` | checks GitHub releases, downloads and installs — one copy per flavour, the Play one does nothing |
| `HijriDate` | Hijri date from the platform calendar |

Tests live in `app/src/test/`: `PrayerCalcTest` for the astronomy, the rounding rule and the
polar fallbacks, `RegionTest` for the country table. The test names say what each one holds.

City data: [GeoNames](https://www.geonames.org/) (`cities15000`, places over 15,000 people),
licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), bundled as
`app/src/main/assets/cities.txt` — country, name, region, latitude, longitude, and an index
into `app/src/main/assets/timezones.txt`, which is the same dataset's time-zone column.

## 🏪 Google Play

The same source builds two ways:

| Flavour | What it is | Build |
| --- | --- | --- |
| `github` | the APK on the releases page, which updates itself | `gradle assembleGithubRelease` |
| `play` | the store build: no self-updater, and no internet or install-packages permission at all | `gradle bundlePlayRelease` |

Play updates its own apps and its policy forbids one that installs code itself, so the
updater is compiled out rather than hidden. [`docs/play-store.md`](docs/play-store.md) has
the listing copy, the data-safety and exact-alarm answers, and the rest of the checklist;
[`docs/privacy.md`](docs/privacy.md) is the policy the listing links to.

## 🔑 Signing

Builds are signed with a fixed key held in GitHub Actions secrets (`KEYSTORE_B64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), so each release installs as an update over
the last one. Without that the key would differ every build and Android would reject the
update as a signature mismatch.

For local builds, put the same key in `keystore.jks` with a `keystore.properties` beside it:

```
storeFile=keystore.jks
storePassword=...
keyAlias=salah
keyPassword=...
```

Both files are git-ignored and must stay that way. With no keystore present the build still
works — it falls back to a throwaway debug key, which is fine for trying things out but will
not install over a release build.
