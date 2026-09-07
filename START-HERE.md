# Healthy — start here

Read this file first. Then read `healthy-build-spec.md`.

---

## What to build

An Android app called Healthy. It is a personal health tracker. It records sleep, caffeine, fluid, food, weight and health notes. It then shows which daily inputs occur before good days and which occur before bad days.

The app is offline, open source and has no accounts.

---

## The files

| File | What it is |
|---|---|
| `START-HERE.md` | This file. The working rules. |
| `healthy-build-spec.md` | The full specification. 21 sections. |
| `healthy-prototype.html` | A working web prototype. It shows the layout, the colours and the caffeine maths. |

Open the prototype in a browser before you write code. It is not the target. It shows the intent.

---

## Your first task

Do not write application code yet.

1. Check the environment. Report the version of the JDK, the state of `ANDROID_HOME`, and whether `adb` finds a device.
2. Tell me what is missing. Give me the command to install each missing item for my operating system.
3. Wait for me to confirm the environment works.

---

## Working rules

**Follow the build order in section 21.** Do not build the whole app and then test it. Each step must run on the phone before the next step starts.

**Test on the phone, not an emulator.** The app reads Health Connect. An emulator has no watch data.

**Use the acceptance tests in section 20.** After each step, tell me which tests now pass. Do not report a step as complete until its tests pass on the phone.

**Ask before you change the specification.** If a part of the spec is wrong or impossible, tell me and give the reason. Do not choose a different design in silence.

**Keep the commits small.** One commit for each step in the build order.

---

## Hard constraints

- No analytics. No crash reporting. No accounts.
- Network access is limited to `world.openfoodfacts.org`. The app makes a request only after a barcode scan that misses the local cache. Every other function works with no network.
- Use only open source libraries. Do not use ML Kit. ML Kit stops a release on F-Droid.
- Use ZXing Android Embedded for the barcode scanner.
- No streaks. No badges. No notification that says the user missed a target.
- The user owns the data. Export must work at every stage of the build.

---

## Things to test, not to assume

I do not know the answers. Find them on the phone and tell me.

1. **Does Mi Fitness write awake blocks?** The stage list has deep, light and REM. I saw no awake blocks. If there are none, the wake-up count must show "not reported" and not zero.
2. **Does heart rate still sync?** Health Connect showed heart rate entries from 17 June. Read the last 7 days and tell me the most recent entry.
3. **Does Open Food Facts have caffeine values for my drinks?** Test with a Hell can and a Red Bull can. Tell me if `caffeine_100g` is present or absent.
4. **Does the background job survive the night?** Install the notification, then leave the phone for one night. Report whether the job ran.
5. **Does the Pixel Thermometer app write to Health Connect?** Take one reading and look for a `BodyTemperatureRecord`.

---

## About the device

The phone is a Pixel 8 Pro. The watch is a Redmi Watch 5. Mi Fitness runs on the phone, pairs with the watch, and writes to Health Connect. It already holds permission for sleep, heart rate and blood oxygen.

A Pixel runs standard Android. Background work is reliable. Section 14.4 has the detail.

The Pixel 8 Pro has a temperature sensor. Check whether the Thermometer app writes a `BodyTemperatureRecord` to Health Connect. If it does, read it. A raised temperature explains a bad night better than any other input.

---

## About the sleep data

The watch reports sleep stages. The stage data is not accurate. Deep sleep blocks repeat at the same size all night, and real deep sleep gets shorter as the night continues. Treat the stage percentages as a rough guide.

The session start time, the session end time and the heart rate are reliable. Build the analysis on those.

The user sleeps at unusual hours. One night ran from 02:38 to 13:49. Every date calculation must handle a night that starts after midnight and ends in the afternoon. The day boundary is 04:00.
