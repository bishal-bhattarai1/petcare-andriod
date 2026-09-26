# Care Tasks Full Interactivity & Polish - Tasks

- `[x]` Step 1: Default Routines Seed Logic
  - `[x]` Add `ensureDefaultTasksExist()` in `AuthDatabaseHelper.kt` for automatic realistic initial routines
- `[x]` Step 2: Task Row Item Polish & Popup Menu
  - `[x]` Update `item_task_row.xml` with left vertical accent bar
  - `[x]` Update `TaskAdapter.kt` with direct checkmark completion toggle and `...` PopupMenu (Edit, Checklist, Delete)
- `[x]` Step 3: Pet & Category Chip Filter Styling
  - `[x]` Update `TasksActivity.kt` and `activity_tasks.xml` with `app:singleLine="true"` so all pet chips (`Jojo`, `Pet`, `Ggu`) lay out side-by-side
- `[x]` Step 4: Interactive Header & Date Actions
  - `[x]` Connect Date Picker dialog on calendar/date click
  - `[x]` Connect notification icon to `NotificationsActivity` and avatar to `ProfileActivity`
- `[x]` Step 5: Verification & Walkthrough
  - `[x]` Run `gradle_build("app:assembleDebug")`
  - `[x]` Create `walkthrough.artifact.md`
