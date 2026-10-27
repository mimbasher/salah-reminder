# Putting Salah Reminder on Google Play

Everything the Play Console asks for, written out, plus what is already done in this repo and
what only you can do.

## What the repo now does for you

- `gradle bundlePlayRelease` produces `app/build/outputs/bundle/playRelease/app-play-release.aab`
  — the format Play requires. `./scripts/dev.sh bundle` does the same without a toolchain.
- The build targets **API 36**, which Play requires of new apps from 31 August 2026.
- The `play` flavour leaves out the in-app updater, the `INTERNET` permission and the
  `REQUEST_INSTALL_PACKAGES` permission. Play's Device and Network Abuse policy forbids an app
  that downloads and installs code on its own, and that feature would have the listing
  rejected. The GitHub APK keeps it; same source, one flag.
- CI builds both on every push and keeps the bundle as a 90-day build artifact named
  **play-bundle**.
- [`docs/privacy.md`](privacy.md) is the privacy policy the listing has to link to.

## What only you can do

1. **Developer account** — <https://play.google.com/console/signup>, US$25 once. Identity
   verification takes a few days, so start here.
2. **Create the app** — Play Console → *Create app*. Name *Salah Reminder*, English (UK),
   App, Free.
3. **Play App Signing** — accept it (the default). You upload bundles signed with the key in
   `keystore.jks`, which becomes the *upload key*; Google holds the app signing key.
   **Consequence worth knowing:** an app installed from Play is signed with Google's key, so
   it will not install over the APK from the GitHub releases page, and vice versa. Anyone
   moving from one to the other has to uninstall first, which loses their settings. Say so in
   the release notes when you launch.
4. **Upload** the `.aab` to a *Closed testing* track first, install it on your own phone from
   Play, and let a prayer come in before you promote anything to production.
5. Fill in the forms below.

## Store listing

**App name** (30 characters max)

```
Salah Reminder
```

**Short description** (80 characters max)

```
Offline prayer times, a real adhan, and a reminder that waits until you've prayed.
```

**Full description** (4,000 characters max)

```
Salah Reminder works out the five daily prayer times where you are, plays the adhan, and
keeps a reminder in your notification panel until you confirm you have prayed.

It works entirely on your phone. The times are computed from the position of the sun, the
adhan is built in, and 34,000 cities are bundled with the app — so there is nothing to sign
up for, nothing to load, and no connection needed.

PRAYER TIMES THAT MATCH YOUR MOSQUE
• The calculation method follows your country automatically, so the times are your own
  authority's without setting anything up.
• Or choose from seventeen: Muslim World League, Umm al-Qura, Egyptian Authority, ISNA,
  Karachi, Diyanet, Dubai, Gulf, Kuwait, Qatar, Singapore and Indonesia, Morocco, France,
  Russia, Tehran and Jafari — or type your own Fajr and Isha angles.
• Standard or Hanafi Asr.
• Nudge each prayer by a minute at a time until the app agrees with the timetable on your
  mosque wall.
• Pick your city from a list of 34,000, and the times follow that city's own clock even when
  your phone is in another time zone.
• Sensible times at high latitudes, where the sky never truly darkens in summer, and in the
  Arctic, where the sun may not rise at all for weeks.

A REMINDER THAT DOES NOT GIVE UP
• The adhan plays at your phone's alarm volume. Use the one in the app or point it at any
  audio file you like.
• A heads-up before the adhan — "Asr in 10 min" — for as many minutes as you choose.
• The reminder card stays in your notification panel until you tap "Prayed", and comes back
  if your phone clears it.
• It asks again every few minutes until you confirm.
• Missed the notification? Tick the prayer off in the app instead.

AND ALSO
• Qibla compass that tells you to turn left or turn right, and buzzes when you are facing
  Makkah.
• Qiyam al-Layl: the last third of the night, the middle of the night, or your own number of
  hours before Fajr.
• Dhuha: a set time after sunrise, or mid-morning.
• The Hijri date beside today's.
• Light and dark themes, and a layout that fits tablets and folding phones.

PRIVATE BY CONSTRUCTION
This app has no internet permission. It cannot phone home, because it has no way to. No
account, no analytics, no advertising, no tracking. Your city and your settings stay on your
phone.

Taqabbal Allahu minna wa minkum.
```

**Category**: Lifestyle. **Tags**: prayer times, Islam, adhan, qibla.
**Contact email**: your own. **Website**: `https://github.com/mimbasher/salah-reminder`.
**Privacy policy URL**: see *Hosting the privacy policy* below.

## Graphics to prepare

| Asset | Size | Status |
| --- | --- | --- |
| App icon | 512 × 512 PNG, no transparency | **Ready**: [`docs/play/icon-512.png`](play/icon-512.png), rendered from the launcher icon |
| Feature graphic | 1024 × 500 PNG or JPEG | **Ready**: [`docs/play/feature-graphic-1024x500.png`](play/feature-graphic-1024x500.png) |
| Phone screenshots | 2–8 images, 1080 × 1920 or larger, 9:16 | **You need these.** The ones in `docs/screenshots/` are 420 × 880 — too small and the wrong shape for Play |
| Tablet screenshots | optional, 7" and 10" | Worth it: the app already fits tablets |

To capture fresh ones at the right size:

```sh
./scripts/dev.sh emulator                      # a Pixel-shaped Android 16 phone
.toolchain/sdk/platform-tools/adb shell screencap -p /sdcard/shot.png
.toolchain/sdk/platform-tools/adb pull /sdcard/shot.png
```

Good ones to take: the home screen with the countdown ring (dark), the same in light, the
list of today's times, Settings showing the calculation method, the Qibla compass, and the
pinned reminder in the notification shade.

## Data safety form

Answer **no** to every collection and sharing question. In full:

- *Does your app collect or share any of the required user data types?* — **No**.
- *Is all of the user data collected by your app encrypted in transit?* — not applicable once
  you have answered no above.
- *Do you provide a way for users to request that their data be deleted?* — **No** data is
  collected; uninstalling removes everything the app has stored.

Location is **used but not collected**: it never leaves the device, which is exactly the case
Google's form describes as not collection. If the form presses you on it, the wording to use
is: *approximate or precise location is read on the device to compute prayer times and is
stored only on the device; it is not transmitted, logged or shared.*

## Content rating questionnaire

Category *Reference, News, or Educational*. Answer **no** to violence, sexuality, language,
controlled substances, gambling, user-generated content, user interaction, sharing location
with other users, and digital purchases. The result will be *Everyone / PEGI 3*.

## App access

No login. Tick *All functionality is available without special access*.

## Ads

*This app does not contain ads.*

## Government apps, financial features, health

None apply.

## The exact-alarm declaration

The app holds `USE_EXACT_ALARM`, which Play reviews. The permitted use it falls under is an
alarm clock or calendar reminder whose timing is the point of the app. Declaration text:

```
Salah Reminder is a prayer-time reminder. The five daily Islamic prayers begin at precise
moments determined by the position of the sun, which differ every day and for every location.
The app's only function is to alert the user at those moments and to play the adhan. An
inexact alarm, which Android may defer by many minutes to batch wakeups, would announce a
prayer at the wrong time and would make the app unfit for its purpose. Exact alarms are used
solely to fire the five daily prayer reminders and the two optional ones (Qiyam al-Layl
before dawn, Dhuha in the forenoon) that the user turns on.
```

## The foreground service declaration

`mediaPlayback` is declared, and used to play the adhan when a prayer comes in:

```
The app plays the adhan — the Islamic call to prayer, an audio file bundled with the app or
one the user chooses — at each prayer time. Playback starts from an alarm while the app is in
the background, and must continue and remain controllable while the screen is off, so it runs
as a media playback foreground service with a notification carrying a stop control.
```

## Hosting the privacy policy

Play needs a public URL. Either works:

- **GitHub Pages** — repo *Settings* → *Pages* → source *Deploy from a branch*, branch `main`,
  folder `/docs`. The policy is then at
  `https://mimbasher.github.io/salah-reminder/privacy`.
- **The file on GitHub** — `https://github.com/mimbasher/salah-reminder/blob/main/docs/privacy.md`
  is a public page and Play accepts it.

## Release notes for the first version

```
First release on Google Play.

• Prayer times computed on your phone, with the calculation method chosen from your country,
  seventeen to pick from by hand, and per-prayer adjustment to match your local mosque.
• The adhan, a reminder that waits until you have prayed, a Qibla compass, Qiyam al-Layl and
  Dhuha.
• No internet permission: nothing you do leaves your phone.

Already using the version from GitHub? Google Play signs apps with its own key, so this one
cannot install over it. Uninstall the old app first — your settings will not carry over.
```

## After launch

Play will email about each policy deadline — chiefly the yearly target API bump. When that
comes, raise `targetSdk` in `app/build.gradle`, build, test the alarms, upload.
