# Privacy policy

**Salah Reminder** · last updated 20 October 2026

Salah Reminder does not collect, store or share any personal data. There is no account to
make, no analytics, no advertising, no tracking and no crash reporting. Nothing you do in the
app is sent anywhere, because the app has nowhere to send it.

## What the app keeps, and where

Everything below is kept in the app's own private storage on your phone, and nowhere else.
Uninstalling the app deletes all of it.

| What | Why | Leaves your phone? |
| --- | --- | --- |
| The city you chose, or the coordinates you set | To work out the prayer times for that place | No |
| Your settings — calculation method, adjustments, reminder times, theme, chosen adhan file | To behave the way you asked | No |
| Which prayers you have ticked off, for the last seven days | So the home screen and the reminders know what is still outstanding | No |

## Location

If you tap **Use my current location**, the app asks Android for your approximate or precise
location. The coordinates are used on your phone, to compute the position of the sun, and are
saved on your phone so the times survive a restart. They are never transmitted.

You never have to grant this. Choosing your city from the built-in list of 34,000 cities, or
typing coordinates, works just as well and asks for no location permission at all.

The one part that does reach the network is Android's own address lookup, which turns the
coordinates into a city name to show you. That is a system service belonging to Android, not
to this app, and it runs only at the moment you tap that button. It is governed by Google's
privacy policy rather than this one.

## The internet

The Google Play build of Salah Reminder does not hold the internet permission. It cannot make
a network connection of any kind. Prayer times are computed on your phone from the sun's
position, and the adhan is bundled with the app, so none of it needs a connection.

(The separate build published on GitHub does hold that permission, for one purpose: checking
the project's releases page for a newer version of the app. It sends no information about you
when it does. The Play build has no such feature, because Google Play handles updates.)

## Permissions, and what each is for

- **Notifications** — to show the prayer reminder.
- **Alarms & reminders (exact alarms)** — a prayer time is a fixed moment; an alarm that
  Android is free to delay by twenty minutes is not a prayer reminder. Used only to fire the
  five daily reminders and the optional Qiyam and Dhuha ones.
- **Location (approximate or precise)** — optional, see above.
- **Run at startup** — to put the day's alarms back after the phone reboots.
- **Ignore battery optimisations** — optional, offered because some phones silently stop
  background alarms otherwise.
- **Vibrate, wake lock, foreground service (media playback)** — to sound the adhan and wake
  the screen when a prayer comes in.
- **Read an audio file you pick** — only the one file you choose as your adhan, and only
  while the app plays it.

## Children

The app is suitable for all ages and collects nothing from anyone, children included.

## Changes

Any change to this policy will be published on this page, with the date at the top updated.

## Contact

Questions about this policy, or about anything the app does with your data:
<https://github.com/mimbasher/salah-reminder/issues>
