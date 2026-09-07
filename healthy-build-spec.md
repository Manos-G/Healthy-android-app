# Healthy — Android build specification

Paste this file into Claude Code as the first prompt. Attach `healthy-prototype.html` as the reference for the layout and the caffeine math.

---

## 1. Purpose

The app is called Healthy. It records these things each day:

- Caffeine doses, with the time of each dose.
- One sleep entry each morning.

The app then shows which inputs occur on good days and which occur on bad days.

The app reads sleep data and heart rate data from Health Connect. The user types only the data that a sensor cannot measure.

---

## 2. Platform

- Language: Kotlin.
- User interface: Jetpack Compose with Material 3.
- Minimum SDK: 26.
- Target SDK: the highest version that the current stable Android Gradle Plugin supports. Do not use a pre-release plugin to reach a higher number.
- This app is sideloaded. No store deadline forces a target SDK bump. A bump is a separate task with its own commit. It is not a standing obligation.
- Database: Room.
- Test device: Pixel 8 Pro. Install with `adb`.
- Do not add analytics. Do not add crash reporting. Do not add accounts.
- The app has one network permission. It reaches `world.openfoodfacts.org` and no other address. It makes a request only after a barcode scan that misses the local cache. Section 11.5 has the rules. Every other function works with no network.

---

## 3. Health Connect

### 3.1 Dependency and status

Add `androidx.health.connect:connect-client`. Use the most recent stable version.

Call `HealthConnectClient.getSdkStatus()` at start. If Health Connect is not available, show the manual entry form and hide the sync button.

### 3.2 Permissions

Request these three read permissions:

- `android.permission.health.READ_SLEEP`
- `android.permission.health.READ_HEART_RATE`
- `android.permission.health.READ_OXYGEN_SATURATION`

Health Connect refuses an app that has no rationale screen. Add an activity that answers the `ACTION_SHOW_PERMISSIONS_RATIONALE` intent. The screen must state which data the app reads and why.

### 3.3 Records to read

**SleepSessionRecord** — read the session that overlaps the night. Take `startTime`, `endTime` and the duration.

The device writes one session for each night. A session for this user can start after midnight and end in the afternoon. Do not assume that sleep starts before midnight.

Read the `stages` list. The device writes this list. Each stage block has a start time, an end time and a type. The types are `STAGE_TYPE_DEEP`, `STAGE_TYPE_LIGHT`, `STAGE_TYPE_REM` and `STAGE_TYPE_AWAKE`.

Store every stage block in the `stage_block` table. Do not store only the totals. The block times permit later analysis. The totals do not.

Sum the minutes for each type and show the four totals.

The list can be empty on some nights. If the list is empty, hide the stage fields. Do not show zero values.

**Wake-ups.** Count the `STAGE_TYPE_AWAKE` blocks. This device can write no awake blocks at all. If the night has stage data but no awake blocks, show "not reported". Do not show zero, because zero is a measurement and "not reported" is an absence.

**Cycle length.** Find the start time of each deep block. Calculate the interval between each pair of adjacent deep blocks. Take the median. This value is the sleep cycle length. Show it on the Trends screen.

Calculate the cycle length only when the night has 3 or more deep blocks. Below 3 blocks, show "too few blocks".

**HeartRateRecord** — read all samples between the sleep start time and the sleep end time. Sort the beats-per-minute values. Take the 5th percentile as the resting heart rate. Do not take the single lowest sample, because one bad sample gives a false value.

The device writes samples in 30-minute groups. Each group can hold a minimum value and a maximum value. Use the minimum values in the calculation.

**OxygenSaturationRecord** — read the samples in the same window. Take the mean. This record can be absent. If it is absent, leave the field empty.

**WeightRecord** — read all records. Also write a record when the user logs a weight. The unit is kilograms.

**HydrationRecord** — read all records for the day. Also write a record when the user logs a drink. The unit is litres. Health Connect uses litres. The app shows millilitres. Convert on read and on write.

Add these two write permissions:

- `android.permission.health.WRITE_WEIGHT`
- `android.permission.health.WRITE_HYDRATION`

A write makes the data available to other apps. Do not keep the data only in the app database.

### 3.4 Sync behaviour

Add a sync button on the morning entry screen. The button reads the records for the selected night and fills the form.

The user can edit every field after a sync. A manual edit must survive a second sync. Mark an edited field and do not overwrite it.

Do not sync in the background. A manual button is enough and it saves battery.

---

## 4. Data model

### 4.1 Table `night`

| Column | Type | Source |
|---|---|---|
| `date` | text, primary key, `YYYY-MM-DD` | The date the sleep started |
| `sleepStart` | long, epoch millis | Health Connect |
| `sleepEnd` | long, epoch millis | Health Connect |
| `minutes` | int | Calculated |
| `deepMin` | int, nullable | Health Connect |
| `lightMin` | int, nullable | Health Connect |
| `remMin` | int, nullable | Health Connect |
| `awakeMin` | int, nullable | Health Connect |
| `wakeups` | int, nullable | Health Connect |
| `restingHr` | int, nullable | Health Connect |
| `spo2` | real, nullable | Health Connect |
| `alertness` | int, 1 to 5, nullable | User |
| `energy3pm` | int, 1 to 5, nullable | User |
| `alcoholUnits` | real, nullable | User |
| `lastMeal` | text, `HH:mm`, nullable | User |
| `exercise` | text, nullable | User: none, light or hard |
| `roomTempC` | real, nullable | User |
| `notes` | text | User |
| `editedFields` | text | A list of the fields the user changed |

### 4.2 Table `stage_block`

| Column | Type |
|---|---|
| `id` | long, primary key |
| `nightDate` | text, foreign key to `night.date` |
| `type` | text: deep, light, rem or awake |
| `startTime` | long, epoch millis |
| `endTime` | long, epoch millis |

A sync deletes the blocks for that night and writes the new blocks. Do not create duplicates.

### 4.3 Table `drink`

| Column | Type |
|---|---|
| `id` | long, primary key |
| `name` | text |
| `mg` | int |
| `volumeMl` | int |
| `timestamp` | long, epoch millis |

Section 9.1 needs `volumeMl`. One tap logs the caffeine and the fluid together.

### 4.4 Day boundary

A day starts at 04:00 and ends at 04:00 the next day. This user goes to sleep after midnight. A boundary at midnight puts one night into two days.

---

## 5. Screens

### 5.1 Today

Show the caffeine level now, in milligrams. Use a large numeral.

Below the numeral, draw a curve of the caffeine level across the 24-hour day. Mark each drink on the curve. Draw a vertical line for the time now. Draw a second line for the target bedtime.

Below the curve, show the level at the target bedtime and a verdict. The verdict is "clear" below the limit and "still active" above it.

Below that, show the drink catalog as a grid of buttons. One tap logs one drink at the time now. Show a snackbar to confirm, with an undo action.

At the bottom, list the drinks of the current day. The user can delete an entry with a swipe.

### 5.2 Morning

At the top, show the sync button and the selected date.

After a sync, show the values from Health Connect in a read-only style. The user can tap a value to edit it.

Below the sensor data, show two rating scales from 1 to 5:

- Alertness, in the middle of the morning.
- Energy, at approximately 15:00.

The user cannot know the energy at 15:00 while it is still morning. Save the night with the alertness only. Leave `energy3pm` empty.

Add a card on the Today screen that appears after 15:00 when `energy3pm` for that night is empty. The card holds the same scale and one tap fills it.

Both ratings are nullable. The comparison table in section 5.3 uses the mean of the ratings that exist. A night with one rating still counts.

Show the caffeine total for the previous day. The app calculates this total from the `drink` table. The user does not type it.

Below that, show the fields for alcohol, last meal, exercise, room temperature and notes.

Add a save button. A save replaces any entry that has the same date.

### 5.3 Trends

Show these mean values: sleep hours, alertness, energy, resting heart rate, caffeine milligrams and wake-ups.

Show a chart of sleep hours and alertness across the last 30 nights.

Show a chart of the caffeine total for each day across the last 30 days. Show the target bedtime level as a second line on the same chart. A high daily total matters less than a high level at bedtime.

Show a comparison table. Sort the nights by the mean of alertness and energy. Take the best third and the worst third. For each input, show the mean of the best third against the mean of the worst third.

The table needs 6 rated nights. Below 6 nights, show the count and the target.

Add this text below the table: a difference that stays as the nights increase is a real effect. A difference from a few nights is noise.

### 5.4 Data

Add three export buttons:

- Nights, as CSV.
- Drinks, as CSV.
- All data, as JSON.

Add an import button for the JSON file.

Add a QR share. A QR code holds one food or one recipe. Another phone reads the code and adds the item. This transfer needs no server and no account.

Add these settings: target bedtime, caffeine half-life in hours, and the bedtime limit in milligrams.

Use the Storage Access Framework for all exports. Do not write to a fixed folder.

---

## 6. Caffeine calculation

Calculate the level at time `t` with this equation:

```
level(t) = sum over all doses of: mg × 0.5 ^ ((t − doseTime) / halfLife)
```

The default half-life is 5 hours. The user can change it.

The default bedtime limit is 50 mg.

Add this note in the settings screen: the model uses a constant half-life and ignores absorption time. The result compares your own days. It is not a measurement of your blood.

---

## 7. Drink catalog

| Drink | mg | Volume |
|---|---|---|
| Freddo espresso | 125 | 200 ml |
| Freddo cappuccino | 125 | 250 ml |
| Frappé | 70 | 250 ml |
| Greek coffee | 60 | 60 ml |
| Espresso | 63 | 30 ml |
| Double espresso | 125 | 60 ml |
| Cold brew, 300 ml | 200 | 300 ml |
| Filter coffee | 95 | 240 ml |
| Cappuccino | 75 | 180 ml |
| Instant coffee | 60 | 200 ml |
| Hell 250 ml | 80 | 250 ml |
| Hell 500 ml | 160 | 500 ml |
| Red Bull 250 ml | 80 | 250 ml |
| Red Bull 355 ml | 114 | 355 ml |
| Monster 500 ml | 160 | 500 ml |
| Black tea | 47 | 240 ml |
| Green tea | 28 | 240 ml |
| Coca-Cola 330 ml | 32 | 330 ml |
| Coke Zero 330 ml | 34 | 330 ml |
| Dark chocolate 50 g | 40 | 0 ml |

The user can add a custom drink with a name, a milligram value and a volume. Store custom drinks and show them in the catalog.

---

## 8. Weight

### 8.1 Entry

The user weighs on a scale and types the value. If a Xiaomi scale writes to Health Connect, a sync reads the value instead.

**The carousel.** Do not show an empty field. Show a wheel that starts at the weight from the previous entry. The user moves the wheel and saves.

Set the range of the wheel to the previous weight plus or minus 3 kg. A body does not change more than 3 kg in one day. A short wheel needs one movement of the thumb. A long wheel needs many.

Set the step to 0.1 kg.

If no previous weight exists, start the wheel at 70 kg and set the range from 40 kg to 150 kg.

Do not ask for a goal weight. Do not show a difference from a target.

### 8.2 Trend

A daily weight moves 1 kg to 2 kg from water, food and glycogen. The daily value is noise. The trend is the signal.

Calculate a smoothed value with this equation:

```
trend(today) = trend(yesterday) + 0.1 × (weight(today) − trend(yesterday))
```

Set `trend` equal to the first weight on the first day.

Show the trend as a solid line. Show the daily weights as small dots around it. Make the line the strong element and the dots the quiet element.

Show the change in the trend across 30 days. Do not show the change across 1 day.

### 8.3 Guidance text

Add this text below the chart: weigh at the same time each day, after you wake and before you eat. A different time gives a different number for the same body.

### 8.4 OpenScale

OpenScale is an open source app. It reads Bluetooth scales. It supports many scale models.

Do not write Bluetooth scale code. OpenScale has solved this problem.

**Read the data in this order:**

1. **Health Connect.** Read `WeightRecord` and take the most recent value. Check whether OpenScale writes there. I do not know the answer. Test it and tell me.
2. **CSV import.** OpenScale exports a CSV file. Add an import for that file. Map the columns to the `weight` table. Ignore a row that has a date already in the table.
3. **Manual entry.** The wheel in section 8.3 stays as the last method.

**Body composition.** A smart scale measures more than weight. Add these columns to the `weight` table. Each column is nullable:

| Column | Unit |
|---|---|
| `bodyFatPct` | percent |
| `waterPct` | percent |
| `musclePct` | percent |
| `boneKg` | kg |
| `visceralFat` | index |

Show a trend line for body fat with the same smoothing as the weight. Hide every field that has no data. Do not show a zero.

Add this note: a bioimpedance scale estimates body composition from electrical resistance. Hydration changes the result. The trend across weeks is useful. One reading is not.

### 8.5 Goal modes

The user selects one of three modes. The default mode is "no goal".

**No goal.** The app records the weight and shows the trend. It sets no energy target.

**Hold the weight.** The user sets a weight. The app sets a band of plus or minus 1 kg around it.

Compare the trend against the band. Do not compare the daily weight. A daily weight leaves the band often and means nothing.

Show a message only when the trend stays outside the band for 7 days. The message states the direction and the size. It gives no instruction.

**Change the weight.** The user sets a rate in kilograms for each week. The user does not set a date.

A rate is correct because the user controls the rate. A date is wrong because a missed date makes the app demand a larger and larger deficit each week.

Limit the rate to 1 percent of body weight for each week. Refuse a larger rate and state the limit.

### 8.6 Progress

Show the trend line against the target line. The target line starts at the trend on the first day of the goal and continues at the chosen rate.

Show the actual rate across the last 14 days beside the target rate. These two numbers tell the user whether the plan works. No other number is necessary.

---

## 9. Fluid

### 9.1 Automatic entry from drinks

Every drink in the catalog has a volume in millilitres. A tap on a drink button logs the caffeine and the fluid together. The user does not log the same drink twice.

### 9.2 Manual entry

Add quick buttons for drinks with no caffeine:

| Drink | Volume |
|---|---|
| Water, glass | 250 ml |
| Water, bottle | 500 ml |
| Water, large bottle | 1000 ml |
| Juice | 200 ml |
| Beer, 330 ml | 330 ml |
| Wine, glass | 150 ml |

The user can add a custom volume.

### 9.3 Display

Show the total for the day in millilitres. Show a bar that fills as the total increases.

Set the default target to 2000 ml. The user can change the target. Do not send a notification when the user is below the target. Do not show a streak.

### 9.4 Alcohol link

A beer or a wine adds to the fluid total. It also adds to the alcohol units for that day. The morning screen reads this total. The user does not type the alcohol units again.

Add a settings field for the units in one beer and the units in one glass of wine. The definition of a unit changes between countries.

---

## 10. Menstrual cycle

This section is optional. It is off by default.

### 10.1 Setting

Add one toggle in the settings screen: "Track menstrual cycle". Do not ask the user for a gender. Do not show this question at first start.

When the toggle is off, hide every field in this section.

### 10.2 Health Connect

Request `android.permission.health.READ_MENSTRUATION` and `android.permission.health.WRITE_MENSTRUATION`. Request these permissions only when the user turns the toggle on.

Read `MenstruationPeriodRecord` for the start date and the end date. Read `MenstruationFlowRecord` for the daily flow.

Write both records when the user logs from this app.

### 10.3 Entry

Add a flow field to the morning screen. The values are none, light, medium and heavy. These values match the Health Connect flow types.

Calculate the cycle day from the most recent period start date. Day 1 is the first day of the period.

### 10.4 Analysis

Add the cycle day to the comparison table as an input.

Add a chart of sleep hours against cycle day. Body temperature increases in the second half of the cycle. Sleep quality often falls before a period. This chart shows whether that pattern is true for this user.

---

## 11. Barcode scanner

### 11.1 Library

Use ZXing Android Embedded. The licence is Apache 2.0.

Do not use ML Kit. ML Kit is proprietary code. It stops a release on F-Droid.

Add a scan button on the Today screen and on the food screen. One tap opens the camera. One scan closes the camera.

After a scan, read the `kind` column. A drink opens the caffeine dialog. A food opens the portion dialog. A new barcode asks the user to select the kind.

### 11.2 Local table first

Add a table `product`:

| Column | Type |
|---|---|
| `barcode` | text, primary key |
| `kind` | text: drink or food |
| `name` | text |
| `brand` | text |
| `mg` | int, caffeine, drinks only |
| `volumeMl` | int, drinks only |
| `packGrams` | int, foods only |
| `servingGrams` | int, foods only |
| `kcal100` | real |
| `protein100` | real |
| `carbs100` | real |
| `sugar100` | real |
| `fat100` | real |
| `fibre100` | real |
| `salt100` | real |
| `calcium100` | real |
| `iron100` | real |
| `potassium100` | real |
| `magnesium100` | real |
| `vitaminD100` | real |
| `vitaminB12100` | real |
| `source` | text: user or off |

One table holds drinks and foods. The `kind` column selects the correct screen after a scan.

A scan reads this table first. A match adds the drink at once. The app makes no network request.

### 11.3 Open Food Facts

On a miss, request this address:

```
https://world.openfoodfacts.org/api/v2/product/{barcode}.json
    ?fields=product_name,brands,quantity,nutriments
```

The service needs no key and no login. Send a User-Agent header with the app name, the version and a contact address. The service refuses a request with a default User-Agent.

Read these fields:

- `product_name` and `brands` give the name. These fields are reliable.
- `quantity` gives the volume, for example "250 ml". Parse the number. This field is reliable.
- `nutriments.caffeine_100g` gives the caffeine in grams for each 100 ml. Multiply by 10 to get milligrams for each 100 ml. This field is often absent.

For a food, also read the fields in section 12.2.

### 11.4 Missing caffeine

If the caffeine value is absent, show a dialog. The dialog shows the product name and asks for the milligrams. The user reads the value from the can.

Write the answer to the `product` table with `source` set to user. The app does not ask again for that barcode.

If the product is absent from Open Food Facts, ask for the name, the milligrams and the volume.

### 11.5 Network rules

Add the `INTERNET` permission. Restrict all requests to `world.openfoodfacts.org`.

The app makes a request only after a scan that misses the local table. The app makes no other request.

Every other function works with no network. Add a network security configuration that permits this one domain.

If a request fails, show the manual dialog. Do not show an error and stop.

---

## 12. Food

### 12.1 Purpose

The app records what the user eats. The purpose is correlation with sleep and alertness. The purpose is not a calorie budget.

Keep every target off by default. Add one optional setting for a daily energy target. Do not show a red colour above a target. Do not send a notification. Do not show a streak.

### 12.2 Fields from Open Food Facts

Read these fields from the `nutriments` object. Each value is for 100 g:

- `energy-kcal_100g`
- `proteins_100g`
- `carbohydrates_100g`
- `sugars_100g`
- `fat_100g`
- `fiber_100g`
- `salt_100g`
- `calcium_100g`, `iron_100g`, `potassium_100g`, `magnesium_100g`
- `vitamin-d_100g`, `vitamin-b12_100g`

Magnesium and vitamin D have a plausible link to sleep. Add both to the comparison table in section 5.3. Energy and protein do not belong in that table.

Read `product_quantity` for the pack weight in grams. Read `serving_size` for the serving text, for example "30 g". Parse the number from that text.

These fields are well populated for packaged food in Europe. They are more reliable than the caffeine field.

### 12.3 The portion problem

A barcode gives the values for 100 g. It does not give the weight the user ate. The app must ask.

Show three buttons after a scan:

- **Whole pack** — use `packGrams`.
- **One serving** — use `servingGrams`. Hide this button when the field is absent.
- **Weigh it** — open a number field for grams.

Add this text below the buttons: a kitchen scale gives a correct number. An estimate by eye is wrong by 30 percent or more.

### 12.4 Food with no barcode

Fruit, vegetables and bread from a bakery have no barcode. Add a manual food form: a name, a weight and the values for 100 g.

Store every manual food in the `product` table. Give it a code that starts with `manual-`. The user searches this table by name and logs the food again with one tap.

### 12.5 Meals

Add a table `meal_entry`:

| Column | Type |
|---|---|
| `id` | long, primary key |
| `timestamp` | long, epoch millis |
| `mealType` | text: breakfast, lunch, dinner or snack |
| `barcode` | text, nullable |
| `recipeId` | long, nullable |
| `grams` | real |

Write a `NutritionRecord` to Health Connect for each entry. This record holds energy, protein, carbohydrate, fat and caffeine. Other apps can then read the data.

### 12.6 Meal time and sleep

The morning screen has a "last meal" field. Fill this field from the latest `meal_entry` of the previous day. The user does not type the time again.

Add the hours between the last meal and the sleep start time to the comparison table. This interval is a strong input for sleep quality.

---

## 13. Recipes

### 13.1 Why recipes matter

A user eats the same meals many times. A recipe turns a long entry into one tap. Without recipes the user stops after two weeks.

### 13.2 Tables

Table `recipe`:

| Column | Type |
|---|---|
| `id` | long, primary key |
| `name` | text |
| `cookedGrams` | real |
| `portions` | int |

Table `recipe_item`:

| Column | Type |
|---|---|
| `id` | long, primary key |
| `recipeId` | long |
| `barcode` | text, nullable |
| `childRecipeId` | long, nullable |
| `name` | text |
| `grams` | real |

Section 13.6 needs `childRecipeId`. An item points at a product or at another recipe, never both. Reject a save when both columns hold a value.

### 13.3 Build a recipe

The user adds each ingredient. A scan adds an ingredient. A search of the `product` table also adds an ingredient. A manual entry adds an ingredient.

The user types the weight of each ingredient before it is cooked.

### 13.4 Cooked weight

After the ingredients, ask for the weight of the finished dish. Put the empty pot on the scale first. Then put the full pot on the scale. The difference is the cooked weight.

This step is necessary. A stew loses water and gets lighter. Rice absorbs water and gets heavier. Without the cooked weight every portion after this point is wrong.

Calculate the values for 100 g of the finished dish:

```
value100(dish) = sum of all ingredient values ÷ cookedGrams × 100
```

### 13.5 Log a portion

Show three ways to log a portion:

- **Weigh it** — the user types the grams from a scale. This way is correct.
- **A share of the dish** — the user selects a fraction, for example one quarter. The app multiplies `cookedGrams` by the fraction.
- **One portion** — the app divides `cookedGrams` by `portions`.

### 13.6 Reuse

Show the recipe list with the most recent recipe first. One tap opens the portion buttons. Two taps log a full meal.

A recipe can hold another recipe as an ingredient. A sauce is a recipe. A pasta dish uses the sauce. Limit this depth to 2 levels.

---

## 14. The morning notification

### 14.1 Why a fixed time fails

This user wakes at a different time each day. One night ended at 13:49. A notification at 08:00 arrives during sleep. The user dismisses it and does not return.

The trigger is the end of sleep, not the clock.

### 14.2 Trigger

Run a periodic `WorkManager` job every 30 minutes.

Each run does this:

1. Read the most recent `SleepSessionRecord` from Health Connect.
2. Compare the end time against the last notified session.
3. If the session is new, and the `night` table has no row for that date, send the notification.
4. Store the session end time. Do not send a second notification for the same session.

Add a fallback in the settings: a fixed time each day. Some users prefer it. Some devices stop the background job.

### 14.3 The notification

Create a channel with `IMPORTANCE_LOW`. This level shows the notification in the shade with no sound and no vibration.

Text: "Log last night". Add the sleep duration as the second line, for example "11 h 11 m recorded".

One tap opens the app at the morning screen with the correct date already selected. Use a `PendingIntent` with a date extra. Do not open the app at the Today screen.

The notification stays until the user saves the night. Set `setOngoing(false)` so the user can dismiss it.

Request `POST_NOTIFICATIONS` on Android 13 and later. Ask for this permission after the first saved night, not at first start.

### 14.4 Battery

The test device is a Pixel 8 Pro. A Pixel runs standard Android. It does not stop background work in the way that some other makers do.

`WorkManager` is reliable on this device. Do not add a wake lock. Do not add a foreground service. The 30-minute job is enough.

Add one item to the settings: a button that asks the system to ignore battery optimisation for this app. Explain what the button does. Do not force it. The job usually runs without it.

Add a status line in the settings: the time of the last successful job. A time many hours old shows the system stopped the job.

If a later Android version stops the job, the fixed time in section 14.2 remains as the fallback.

---

## 15. Health notes

### 15.1 Purpose

The user writes a short note about health, for example "teeth pain" or "sore throat". The note attaches to a date. The user finds the note again later.

### 15.2 Table `note`

| Column | Type |
|---|---|
| `id` | long, primary key |
| `date` | text, `YYYY-MM-DD` |
| `text` | text |
| `createdAt` | long, epoch millis |

Add a Room FTS table over the `text` column. FTS gives a fast search.

### 15.3 Add a note

Add a note button on the Today screen and on the morning screen. The button opens one text field and a save button.

The date is today by default. The user can change the date.

### 15.4 The notes screen

Open this screen from a "Notes" button in the Data tab.

The screen has three parts, from top to bottom:

**A calendar.** Show one month. Put a dot under each day that has a note. The user swipes to change the month. A tap on a day filters the carousel to that day.

**A search field.** The search reads the FTS table. The search runs as the user types.

**A carousel.** Show the matching notes as cards in a horizontal row. Each card shows the date and the text. A tap opens the note to edit or delete.

When the search field is empty and no day is selected, the carousel shows every note. The most recent note is first.

### 15.5 Analysis

Add the note text to the CSV export. A note is a strong clue for a bad night. Pain and illness change sleep more than caffeine does.

---

## 16. Energy and nutrient targets

### 16.1 Measure the energy need, do not calculate it

A formula estimates the energy need from height, weight, age and sex. The error is often 300 kcal. Do not use a formula as the main method.

The app has the weight trend and the food log. These two give the true value:

```
TDEE = mean daily intake + (trend change in kg × 7700) ÷ days
```

7700 kcal is the energy in 1 kg of body tissue. Use a rolling window of 14 days.

Show the result as "your maintenance energy". Show the number of days of data behind it.

### 16.2 Before the data exists

The window needs 14 days of weight entries and 14 days of complete food logs.

Below 14 days, use the Mifflin-St Jeor equation as a start value. Mark it clearly as an estimate. Replace it as soon as the window fills.

Do not mix the two. Show one number and state its source.

### 16.3 The daily target

```
target = TDEE − (rate in kg each week × 7700 ÷ 7)
```

Set a floor. The target must not fall below the basal metabolic rate. If the calculation gives a lower number, use the basal rate and tell the user the chosen rate is too fast for the current weight.

### 16.4 Nutrient targets

The user sets a target for any nutrient. Each target is a floor or a ceiling.

Set these defaults. The user can change every one:

| Nutrient | Type | Default |
|---|---|---|
| Protein | floor | 1.6 g for each kg of body weight |
| Fibre | floor | 30 g |
| Saturated fat | ceiling | 10 percent of energy |
| Sugar | ceiling | 10 percent of energy |
| Salt | ceiling | 5 g |

### 16.5 Display

Show a bar for each target. Fill the bar as the day continues.

Do not colour a bar red. Do not send a notification. Do not show a streak. Show the number and the bar.

Show the mean across 7 days beside the value for today. One day means little. A week means something.

---

## 17. Sources and references

Add a screen that lists the basis of every calculation in the app. Open it from the Data tab. Add a link to it beside each number it explains.

The screen states, for each item, the value the app uses and where the value comes from:

| Item | Value | Source |
|---|---|---|
| Caffeine half-life | 5 h | Pharmacokinetic studies. The range is 4 h to 6 h. Genetics change it. |
| Bedtime caffeine limit | 50 mg | A rule of thumb. It is not measured for this user. |
| Weight smoothing factor | 0.1 | The Hacker's Diet moving average. |
| Energy in 1 kg of tissue | 7700 kcal | The standard value for mixed body tissue. |
| Resting heart rate | 5th percentile | Chosen to reject a bad sensor sample. It is not a clinical method. |
| Heart rate cycle detection | Local minima, 20 min window | Heart rate falls in deep sleep. This is a rhythm, not a stage. |
| Sleep stages | From the watch | A wrist sensor estimates stages from movement and heart rate. It agrees with laboratory scoring 60 to 80 percent of the time. |
| Maximum weight change rate | 1 percent each week | A common upper limit in nutrition guidance. |
| Reference intakes | Various | U.S. National Academies, Institute of Medicine tables. |

Write each entry in plain words. State what the app does not know. A user who can see the basis of a number can judge the number.

---

## 18. The heart rate hypnogram

### 18.1 Why

The watch reports sleep stages. The stages are not accurate. The stage blocks repeat at the same size all night. Real deep sleep gets shorter as the night continues.

The heart rate is a measurement. The heart rate falls in deep sleep and rises in REM sleep. The shape of the heart rate across the night shows the true structure.

The Gadgetbridge project reaches the same conclusion for Xiaomi devices: the sleep stages are not accurate, but the heart rate pattern shows the sleep pattern well.

### 18.2 Do not label stages

Do not write a classifier that outputs deep, light and REM. Such a classifier is a guess. The app then repeats the error it exists to correct.

Find the cycles. A cycle boundary is a measurement. A stage label is not.

### 18.3 The algorithm

1. Read every `HeartRateRecord` sample between the sleep start and the sleep end. Use the individual samples, not the 30-minute groups.
2. Count the samples. The night needs one sample for each 10 minutes or better. Below that, show "data too sparse" and stop.
3. Smooth the values with a rolling median. Set the window to 5 samples. A median removes a bad contact reading. A mean does not.
4. Set the baseline to the 10th percentile of the smoothed values.
5. Find each local minimum. A point is a minimum when it is lower than every point within 20 minutes on both sides, and it sits below the baseline plus 3 bpm.
6. Join two minima that are less than 50 minutes apart. Two cycles cannot be that close.
7. The cycle count is the number of minima. The cycle length is the median interval between adjacent minima.

### 18.4 Display

Draw the smoothed heart rate across the night. Mark each minimum with a dot.

Draw the stage blocks from the watch as a thin strip below the curve. Use the same time axis.

The user then sees both. A deep block from the watch with no fall in the heart rate below it is a guess by the watch.

Show three numbers: the cycle count, the cycle length, and the lowest heart rate of the night.

### 18.5 Two cycle lengths

Section 3.3 calculates a cycle length from the deep blocks of the watch. This section calculates one from the heart rate.

Store both. Show both on the Trends screen. Label the first "from the watch" and the second "from heart rate".

Two numbers that agree give confidence. Two numbers that disagree show the watch is guessing. Both results are useful.

### 18.6 What this is not

Add this text to the sources screen: this chart shows heart rate, not brain activity. Only an EEG measures sleep stages. This method finds the rhythm of the night from a real measurement. It does not name the stages.

---

## 19. Build and install

1. Install JDK 17 or newer. The Android Gradle Plugin 8.x needs 17 as a minimum. JDK 21 is supported and is a valid choice. Do not install a second JDK if one of these versions is already present. Set JAVA_HOME.
2. Install the Android SDK command-line tools. Do not install an emulator image. The build uses a real phone.
3. Set `ANDROID_HOME` and accept the SDK licences.
4. Turn on developer options and USB debugging on the phone.
5. Run `./gradlew assembleDebug`.
6. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

A debug APK has a debug signature. This signature is sufficient for a personal install. Do not create a keystore unless a release build is necessary.

---

## 20. Acceptance tests

The build is correct when all of these statements are true:

1. The app requests the three Health Connect permissions and the rationale screen opens.
2. A sync fills the sleep start, the sleep end and the duration for last night.
3. A sync fills the resting heart rate from the samples inside the sleep window.
4. The app hides the stage fields when the stage list is empty.
5. A sleep session from 02:38 to 13:49 records against the date the sleep started.
6. A 125 mg dose reads 63 mg after 5 hours and 31 mg after 10 hours.
7. A manual edit of a synced field survives a second sync.
8. The nights CSV opens in a spreadsheet with the correct columns.
9. A JSON export and a JSON import return the same data.
10. A tap on a drink button adds to the caffeine total and to the fluid total.
11. A weight entry appears in Health Connect and other apps can read it.
12. The weight trend line moves less than the daily dots.
13. A scan of a known barcode adds the drink with no network request.
14. A scan of an unknown barcode asks for the milligrams once and never again.
15. A scan works with the phone in flight mode after the first successful lookup.
16. A food scan offers whole pack, one serving and weigh it.
17. A recipe with 1000 g of ingredients and a 700 g cooked weight gives correct values for 100 g.
18. A saved recipe logs a portion in two taps.
19. The notification arrives after a new sleep session, not at a fixed hour.
20. A tap on the notification opens the morning screen at the correct date.
21. The notification makes no sound.
22. The weight wheel starts at the previous weight.
23. A note appears as a dot on the calendar and in the search results.
24. The energy target comes from the measured window after 14 days, not from the formula.
25. The app refuses a weight change rate above 1 percent of body weight each week.
26. The energy target never falls below the basal metabolic rate.
27. The hold-the-weight mode compares the trend against the band, not the daily weight.
28. Every number in the sources screen matches the value the code uses.
29. The hypnogram shows the heart rate curve and the watch stage strip on one time axis.
30. A night with too few heart rate samples shows a message and no chart.
31. The Trends screen shows both cycle lengths with different labels.
32. An OpenScale CSV import adds the weights and skips the dates already stored.
33. A weight row with no body composition hides those fields.
34. The last meal time on the morning screen comes from the meal entries.
35. The menstrual fields stay hidden while the toggle is off.
36. Every function except a new barcode lookup works in flight mode.

---

## 21. Order of work

Build in this order. Test each step on the phone before you start the next step.

1. The Room database and the data model.
2. The Today screen, with the caffeine curve and the catalog.
3. The morning screen, with manual entry only.
4. Health Connect permissions and the rationale screen.
5. The sync function.
6. The Trends screen.
7. Weight and fluid.
8. The barcode scanner.
9. The menstrual cycle section.
10. Food and meals.
11. Recipes.
12. The morning notification.
13. Health notes.
14. Weight goals and energy targets.
15. The heart rate hypnogram.
16. The sources screen.
17. Export, import and QR share.

Step 3 gives a usable app. Every step after step 3 removes manual work.

Steps 10 and 11 are the largest part of the project. Do not start them until steps 1 to 9 run on the phone for one week. Food logging fails when the rest of the app is not yet a habit.
