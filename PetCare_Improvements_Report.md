# PetCare – Code Improvements Report

**Project:** PetCare (`com.example.petcare`, Kotlin, XML views, minSdk 29, targetSdk 36)
**Date:** 4 October 2026
**Scope:** Three improvement tasks done one at a time, each planned first and confirmed before any code changed.

| # | Task | Status |
|---|------|--------|
| 1 | Secure password storage (replace SHA-256 with PBKDF2) | ✅ Done |
| 2 | Migrate the database from SQLiteOpenHelper to Room | ✅ Done |
| 3 | Reminders with AlarmManager + WorkManager | ✅ Done |

General rules followed: no UI redesign, no loss of user data, existing tests kept passing, new tests added, heavy work (hashing, database) moved off the main thread, and new code commented.

---

## Task 1 – Secure password storage

### Problem
Passwords were stored as `SHA-256(email + ":" + password)`: one fast hash, with no random salt. The login matched the hash inside the SQL query, and all hashing ran on the main thread. Change Password didn't ask for the current password, and the minimum length was 6 characters.

### What was done
- **New `PasswordHasher` class** (plain Kotlin, unit-testable on the JVM):
  - PBKDF2WithHmacSHA256 (`javax.crypto`) with a **random 16-byte salt per user** from `SecureRandom`, producing a 256-bit key.
  - Self-describing storage format: `pbkdf2$<iterations>$<saltBase64>$<hashBase64>`.
  - Constant-time comparison with `MessageDigest.isEqual`.
  - Recognises the legacy SHA-256 format and returns `ValidNeedsUpgrade`.
  - Placeholder values (`SOCIAL_LOGIN_NO_PASSWORD`, `GUEST_HASH`) and malformed values never match.
- **Automatic upgrade of existing accounts.** When a user with an old SHA-256 hash logs in successfully, the password is re-hashed with PBKDF2 and saved. Hashes made with fewer iterations than the current setting are upgraded the same way.
- **Timing protection.** A login with an unknown email still runs one hash, so response time doesn't reveal which emails are registered.
- **Minimum length raised from 6 to 8** for sign-up, reset and change password, through one `MIN_PASSWORD_LENGTH` constant. The strength meter now shows:
  - weak: fewer than 8 characters;
  - medium: 8–11 characters with fewer than 3 character types;
  - strong: 3 or more character types, or 12 or more characters.
- **Change Password now asks for the current password.** There is a new "Current Password" field with the same style as the other fields. The screen rejects a new password identical to the current one and tells Google-only accounts that they have no password to change.
- **Work moved off the main thread.** Login, sign-up, reset and change password run on `Dispatchers.IO`, and the button is disabled while the work runs.

### Iteration count (measured)
OWASP recommends 600,000 iterations for PBKDF2-HMAC-SHA256. On Android, `PBKDF2WithHmacSHA256` is provided by Bouncy Castle, which is pure Java and therefore slow:

| Device | 100,000 iterations (login) | 600,000 iterations (login) |
|---|---|---|
| API 36 emulator (debug build) | ~0.9 s | ~8–13 s |
| Android 10 / API 29 emulator (debug build) | ~1.2–1.3 s | not practical |

**Chosen value: 100,000.** Each stored hash records its own iteration count, so raising the value later upgrades every user automatically at their next login. If a strict ≤ 1 s login on Android 10 is required, 75,000 is suggested.

### Files
- `PasswordHasher.kt` (new)
- `MainActivity.kt`, `ForgotPasswordActivity.kt`, `ChangePasswordActivity.kt`
- `res/layout/activity_change_password.xml`, `res/values/strings.xml`
- Tests: `PasswordHasherTest.kt` (10 JVM tests), `PasswordHasherTimingTest.kt` (instrumented timing test)

### Report text
> Passwords are now stored using PBKDF2-HMAC-SHA256 with a unique random 16-byte salt per user, in a self-describing format (`pbkdf2$iterations$salt$hash`) and verified with a constant-time comparison, replacing the previous unsalted-per-user SHA-256 scheme. Existing accounts are migrated transparently: when a user with a legacy hash logs in successfully, the password is re-hashed with PBKDF2 and the stored value is replaced. All hashing runs on a background thread, the minimum password length was raised to 8 characters, and changing a password now requires the current password.

---

## Task 2 – Migrating the database to Room

### Problem
All data access lived in one 1,600-line `AuthDatabaseHelper` (SQLiteOpenHelper, version 14). About 20 screens called it directly, mostly on the main thread, and several queries didn't filter by user.

### What was done
- **8 `@Entity` classes** matching the existing tables exactly (names, columns, SQLite types, nullability, primary keys, defaults): `users`, `pets`, `tasks`, `task_completions`, `expenses`, `pet_photos`, `healthcare_history`, `pet_locations`.
- **7 DAOs**:
  - every query that reads or changes user data filters by `owner_id` / `user_id`;
  - deleting a pet is one `@Transaction` (`deletePetCascade`) that removes its completions, routines, expenses, photos and health records;
  - deleting a routine also removes its completions in one transaction.
- **Repository layer:** `AuthRepository`, `PetRepository`, `HealthRepository`, `TaskRepository`, `ExpenseRepository` and `PlaceRepository`, plus `UserScope` for the current user and ownership checks. The repositories return the same UI models as before, so adapters and layouts didn't change.
- **ViewModels** for the Home, Tasks and Expenses screens (`StateFlow`, with loading on `Dispatchers.IO`).
- **Room database version 15** with `exportSchema = true`, using the same file (`petcare_v3.db`) so existing installs are upgraded in place.
- **`Migration(14, 15)`:**
  - The old tables reached version 14 through many `ALTER TABLE` steps, so the column order differs between installs. Room validates schemas strictly, so every table is **rebuilt and its rows are copied by column name**.
  - All **IDs and AUTOINCREMENT counters (`sqlite_sequence`) are kept**, so the IDs of deleted rows are never reused. Reminder alarms are keyed by task and record IDs.
  - `COALESCE` is used on NOT NULL columns so an unexpected NULL can't abort the migration.
  - Indexes were added on `owner_id`, `user_id` and `pet_id`.
- **Foreign keys:** the old schema declared foreign keys but never enforced them, and a real device database contained an orphan expense. Declaring `@ForeignKey` would have made Room crash during the migration or required deleting that data. The relationships are therefore kept as indexed columns, and cascading deletes are explicit `@Transaction` methods, the same behaviour as before.
- **Every database call now runs off the main thread** (coroutines, ViewModels, or `goAsync()` in the broadcast receiver). Room throws if a query runs on the main thread, which enforces this.
- `AuthDatabaseHelper.kt` was **deleted**. Shared classes moved to `Models.kt` and `DateKeys.kt`.

### Testing
| Test | Result |
|---|---|
| `MigrationTest` (MigrationTestHelper): version 14 → 15 for both the "upgraded install" and "fresh install" table layouts, every value checked, ID counters kept, unique email kept | ✅ 2/2 |
| `PetCareDaoTest`: pet-delete transaction, per-user filtering, completions | ✅ 3/3 |
| **Real device data:** a backup of a real phone's version 14 database migrated by the actual app on an emulator | ✅ all 8 tables identical, row by row; counters kept |
| Emulator walkthrough: sign up → login → add pet → add routine → complete it → add expense → Tasks and Spending tabs → delete pet | ✅ no crashes; cascade removed only that pet's data |

### Files
- New: `data/Entities.kt`, `data/Daos.kt`, `data/PetCareDatabase.kt`, `data/Migrations.kt`, `data/Repositories.kt`, `ui/HomeViewModel.kt`, `ui/TasksViewModel.kt`, `ui/ExpensesViewModel.kt`, `Models.kt`, `DateKeys.kt`, `app/schemas/.../15.json`
- Deleted: `AuthDatabaseHelper.kt`
- Updated to use the repositories and ViewModels: `AddEditPetActivity`, `AddExpenseActivity`, `AddTaskActivity`, `ChecklistActivity`, `DashboardActivity`, `DelegateContactActivity`, `ExpensesActivity`, `ExpensesPageController`, `HomePageController`, `ImportRecordsActivity`, `LocationsActivity`, `MainActivity`, `NotificationsActivity`, `PersonalInfoActivity`, `PetDetailsActivity`, `PetHistoryActivity`, `ProfileActivity`, `ReminderReceiver`, `Reminders`, `ShareReceiverActivity`, `TasksActivity`, `TasksPageController`, `CompletionRules` (import only)
- Tests: `MigrationTest.kt`, `PetCareDaoTest.kt`

### Report text
> The database layer was migrated from a hand-written SQLiteOpenHelper to the Room persistence library, with entities that mirror the existing eight tables, DAOs, a repository layer and ViewModels for the main screens. A Room migration (version 14 → 15) rebuilds each table and copies all rows by column name, preserving IDs and auto-increment counters; it was validated with MigrationTestHelper and on a copy of a real device database, with every row of every table identical after the upgrade. All database access now runs off the main thread, and every query is filtered by the signed-in user.

---

## Task 3 – Reminders (AlarmManager + WorkManager)

### Problem
Exact alarms delivered the reminders, but all alarms were rebuilt inside the boot `BroadcastReceiver`, which Android limits to about 10 seconds. Nothing repaired alarms lost to a force-stop or a battery saver. On Android 14 and later, reminders also stayed inexact after the user granted the exact-alarm permission.

### What was done
- **AlarmManager is still used for the reminder itself.** `setExactAndAllowWhileIdle` alarms fire at the scheduled minute; this path is unchanged.
- **`ReminderCheckWorker`:** a periodic WorkManager job that runs about once a day with a battery-not-low constraint. It counts reminders whose alarm is missing, re-arms every reminder that should exist, and logs the result. It's enqueued as unique work (`KEEP`) from `PetCareApplication`.
- **`RescheduleRemindersWorker`:** a one-time WorkManager job that replaces the work previously done in the boot receiver. `ReminderReceiver` now only **enqueues** it (unique, `REPLACE`) on:
  - `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`;
  - `TIME_SET` and `TIMEZONE_CHANGED`, because alarm times are wall-clock based;
  - `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (Android 12+), so reminders become exact again once permission is granted.
- **`ReminderScheduler.ensureAll()`** is shared by both workers. Re-arming is idempotent: setting the same PendingIntent again replaces the alarm at the same time. It also repairs alarms that some battery savers drop without clearing the PendingIntent, which a "missing" check alone can't detect.
- Added `isScheduled()` and `dueAt()` helpers to the task, vaccination and record reminders.

### Why the hybrid approach
- **Exact timing → AlarmManager.** Care reminders must appear at the scheduled minute. WorkManager can't guarantee that: the system may defer work by minutes or hours to save battery.
- **Reliability → WorkManager.** Re-creating alarms after a reboot, update or clock change, and the daily safety check, must happen *eventually* and *reliably*. WorkManager saves its queue to disk, retries with back-off, survives process death and reboots, respects constraints, and isn't limited to the ~10 s a broadcast receiver gets.

### Testing (Android 10 / API 29 emulator)
| Check | Result |
|---|---|
| `ReminderWorkersTest`: missing alarm detected and re-created; existing alarms kept; worker restores alarms after boot; periodic check enqueued only once | ✅ 4/4 |
| Real reboot → `RescheduleRemindersWorker` ran ~40 s after boot and re-created the lost alarm | ✅ `SUCCESS` |
| Real `TIME_SET` broadcast → worker re-armed the exact alarm for 19:00:00.000 | ✅ `SUCCESS` |
| Exact delivery: notification posted at **19:00:00.053** (53 ms after the scheduled minute) | ✅ |
| Daily check registered as a system job with `BATTERY_NOT_LOW` | ✅ |

### Files
- New: `ReminderWorkers.kt`, `ReminderWorkersTest.kt`
- Updated: `Reminders.kt`, `VaccineReminder.kt`, `ReminderReceiver.kt`, `AndroidManifest.xml`, `PetCareApplication.kt`

### Report text
> Care reminders use a hybrid scheduling design: AlarmManager exact alarms deliver each reminder at its precise time, while WorkManager handles deferrable maintenance that must be guaranteed but not time-critical. A one-time worker re-creates all alarms after a reboot, app update, clock or time-zone change, or when exact-alarm permission is granted. A periodic worker checks once a day for missing alarms and re-arms them. Testing on Android 10 showed that the alarms were restored automatically after a reboot and that the reminder notification was posted within 60 ms of its scheduled time.

---

## Build configuration changes

`gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`:

| Dependency | Version | Purpose |
|---|---|---|
| `lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx` | 2.9.4 | `lifecycleScope`, ViewModels |
| Room (`runtime`, `ktx`, `compiler`) | 2.8.5 | Database layer |
| KSP plugin | 2.3.12 | Room code generation (works with AGP 9's built-in Kotlin) |
| `room-testing` | 2.8.5 | MigrationTestHelper |
| `kotlinx-serialization-core` | 1.8.1 | Needed by room-testing to read exported schemas; another library pinned 1.7.3 |
| WorkManager (`work-runtime-ktx`, `work-testing`) | 2.12.0 | Reminder workers |

Room schemas are exported to `app/schemas/` and packaged into the androidTest assets.

---

## Test summary

| Suite | Type | Result |
|---|---|---|
| `PasswordHasherTest` | JVM | ✅ 10/10 |
| `TaskScheduleTest` (existing) | JVM | ✅ 6/6 |
| `RecordImporterTest` (existing) | JVM | ✅ 7/7 |
| `ExampleUnitTest` (existing) | JVM | ✅ 1/1 |
| `MigrationTest` | Instrumented | ✅ 2/2 |
| `PetCareDaoTest` | Instrumented | ✅ 3/3 |
| `ReminderWorkersTest` | Instrumented | ✅ 4/4 |
| `PasswordHasherTimingTest` | Instrumented (measurement) | ✅ 2/2 |

Instrumented tests were run on the Android 10 (API 29) emulator. All except the timing and worker tests were also run on the API 36 emulator.

## Build and run confirmation

| Target | Builds | Runs |
|---|---|---|
| Android 10 (API 29) emulator | ✅ | ✅ (tests, reboot test and timing test above) |
| API 36 emulator | ✅ | ✅ (manual walkthrough, real-data migration) |
| Android 14 device | – | ❌ Not tested: no Android 14 device was available (the connected phone runs Android 16 and did not get the final build) |

---

## Other changes and fixes

- **Behaviour changes:**
  - saved places are now shown **per user** (before, every account saw all places);
  - reminders are also re-created after a clock or time-zone change.
- **Bug fixed:** after saving a place, a pin dropped on the map was overwritten by the geocoded address, because the coordinates were reset before they were checked. The coordinates are now captured first.
- **Pre-existing build errors fixed:** a stray `sa` in the old database helper and a stray `x` in `LocationMapActivity.kt`.

## Known risks and limitations

1. **App standby on Android 9–11.** When the app hasn't been opened for a long time, Android places it in a low standby bucket and delays even exact alarms: up to ~2 h in "rare" and up to a day in "never". This was observed in testing. Only `setAlarmClock()` is exempt, but it shows an alarm icon in the status bar. It is not used.
2. **Android 14+ exact-alarm permission** is denied by default, so reminders arrive close to their time but not exactly until the user grants "Alarms & reminders". No in-app prompt was added; the worker restores exact alarms once the permission is granted.
3. **PBKDF2 at 100,000 iterations** takes ~1.2–1.3 s on the Android 10 emulator (debug build). 75,000 is suggested if a strict ≤ 1 s login is required.
4. **Forgot Password** still resets any account with only an email address. This is pre-existing and was out of scope.
5. The Room migration supports **version 14 → 15 only**; databases from versions below 14 aren't supported.
6. **Foreign keys are not enforced**, by design (see Task 2); deletes are explicit transactions.
7. **Not tested by hand in the UI:** expense export, record import, geotagging (save/view places), SMS delegation and pet history. These compile against the repositories and run their database work in the background, but weren't clicked through.
8. **Not tested on an Android 14 device.**
9. No changes have been committed to git.

## Environment notes
- An Android 10 (API 29, Google APIs, x86_64) system image was added to the SDK. A new AVD `Android10_API29` is stored at `E:\AndroidAvd\` because drive C: was 98% full.
- Instrumented tests were run only on emulators, using `adb -s <serial>`, never Gradle's `connectedAndroidTest`, so the connected phone's app and data were never uninstalled.
