# Salah Reminder

An offline Android app for the five daily prayers: it works out the times where you are,
plays your own adhan, and keeps a reminder pinned in the notification panel until you
confirm you've prayed.

**[⬇ Download the latest APK](../../releases/latest/download/SalahReminder.apk)**

## What it does

- **Prayer times on your phone** — computed from the sun's position, no internet needed
  once your location is set.
- **Pick your city from a list** — 34,000 cities are bundled with the app. Tap your country,
  tap your city, done. No typing and no internet; GPS and coordinates still work too.
- **Your own adhan** — choose any audio file; it plays at the phone's *alarm* volume.
- **Heads-up before the adhan** — "Asr in 10 min", with the minutes you choose (or off).
- **A reminder you can't swipe away** — the card stays until you tap **✓ Prayed**, and
  comes straight back if your phone dismisses it.
- **Repeat nagging** — it reminds you again every few minutes until you confirm.
- **Qibla compass** — "turn left / turn right", turning green and buzzing when you're facing Makkah.
- **Survives** reboots, app updates, and time-zone changes.

## The screens

**Home** — the next prayer with a live countdown ring, today's five times with their state
(upcoming, waiting for you, prayed), the Gregorian and Hijri date, and a warning banner if
anything on your phone could delay the adhan.

**Choose your city** — a country list, then the cities in it, biggest first. A filter box is
there if you would rather type.

**Settings** — location, calculation method, Asr school, adhan sound, reminder timings, and
the reliability switches. Everything saves the moment you change it; there is no Save button.

**Qibla** — a compass dial with a gold needle pointing at the Kaaba.

## Install

1. Open the download link above on your phone and tap the APK.
2. Allow "Install unknown apps" for your browser when Android asks.

Every push to `main` builds a fresh APK and publishes it as a release, so that link always
gives you the newest version.

## First-time setup

1. Open the app and allow notifications.
2. Tap **⚙ Settings → Search** your city (or **Use my current location**).
3. Choose your **method** and **Asr** school if the defaults aren't yours.
4. **Adhan sound → Choose a file** → pick your adhan, then **▶ Test** to hear it.
5. Pick the **heads-up** and **repeat** minutes.
6. Tap **Allow background running** — this matters on Samsung, Xiaomi, Oppo and Huawei,
   which otherwise stop the reminders.
7. **Test a full reminder now** to see the whole flow once.

You don't need to leave the app running. Android wakes it at each prayer time; between
times it does nothing and uses no battery.

## Running it locally (no Android Studio)

`scripts/dev.sh` fetches its own JDK 17, Gradle and Android SDK into `.toolchain/`
(git-ignored, nothing installed system-wide) and installs straight onto a plugged-in phone:

```sh
./scripts/dev.sh setup      # one-off, ~450 MB
./scripts/dev.sh build      # -> app/build/outputs/apk/debug/app-debug.apk
./scripts/dev.sh install    # build + install + launch on the connected phone
./scripts/dev.sh logs       # follow the app's logcat
```

For `install`, turn on **Developer options → USB debugging** on the phone and tap *Allow*
when it asks. For wireless, use **Wireless debugging** and
`.toolchain/sdk/platform-tools/adb pair <ip>:<port>`.

With your own SDK already installed, plain Gradle works as well:

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assembleDebug
```

No third-party libraries — the whole app is the Android framework plus the files in
`app/src/main/java/com/salah/reminder/`:

| File | Role |
| --- | --- |
| `MainActivity` | home dashboard |
| `SettingsActivity` | all configuration |
| `QiblaActivity` | compass |
| `Ui` | colours, type and the shared view builders |
| `PrayerCalc` | astronomy: prayer times and Qibla bearing |
| `Scheduler` | alarms, state and notifications |
| `AdhanService` | plays the adhan |
| `AlarmReceiver` / `BootReceiver` | alarm delivery and restoring after reboot |
| `CityPickerActivity` | the bundled city list |
| `HijriDate` | Hijri date from the platform calendar |

City data: [GeoNames](https://www.geonames.org/) (`cities15000`, places over 15,000 people),
licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), bundled as
`app/src/main/assets/cities.txt`.
