# Salah Reminder (Android)

- Calculates the 5 daily prayer times for your city (search by name, GPS, or coordinates)
- Plays your own adhan audio file at each prayer time (or the phone's alarm tone)
- "Prayer in X minutes" heads-up before each adhan (you set X, 0 = off)
- Pinned notification that stays until you tap **✓ Prayed** — if swiped away it comes right back
- Repeats a reminder every N minutes until you tap Prayed
- Qibla compass
- Survives reboot, time-zone changes and app updates

## Get the APK (free, no coding)

1. Make a free account at github.com and tap **New repository** (any name, Public or Private).
2. Choose **uploading an existing file**, unzip this folder and drag in everything inside it
   (including the hidden `.github` folder — on a phone, use the GitHub website in desktop mode
   or upload from a computer). Commit.
3. Open the **Actions** tab. The "Build APK" job runs automatically (about 3–5 min).
4. When it shows a green tick, open it and download **SalahReminder-apk** under *Artifacts*.
   Unzip it to get `app-debug.apk`.
5. Copy it to your phone, open it, and allow "Install unknown apps" when asked.

## First-time setup on the phone

1. Open the app, allow notifications.
2. Search your city (or tap GPS) — times appear at the top.
3. Pick your calculation method and Asr school.
4. **Choose sound file** → pick your adhan MP3. Tap **▶ Test adhan** to hear it
   (it uses the *alarm* volume).
5. Set heads-up minutes and repeat minutes, tap **Save settings**.
6. Tap **Allow running in background** — important on Samsung, Xiaomi, Oppo, Huawei,
   which otherwise kill reminders.
7. Tap **Test full reminder now** to see the whole flow.
