# PetCare – Colour Palette & Design Prompt

CET343 Android Mobile Development – PetCare prototype

- Part 1: Colour palette (light, dark, categories, themes)
- Part 2: Design prompt (master prompt + 28 screen prompts)

---

# PART 1 – Colour Palette

Seed colour: **Forest green #2E6A4E**
Font: **Figtree** (Google Fonts) – weights 400, 500, 600, 700, 800

> Matches the design canvas https://claude.ai/artifact/QmxosGfBsakLuXeQs3YJWv (28 artboards).

> Android tip: add `FF` in front of any hex for ARGB, e.g. `#2E6A4E` → `0xFF2E6A4E`.

---

## 1. Main theme (Material 3 roles)

| Role | Light Hex | Light RGB | Dark Hex | Dark RGB |
|---|---|---|---|---|
| Primary | #2E6A4E | 46, 106, 78 | #96D5B2 | 150, 213, 178 |
| On Primary | #FFFFFF | 255, 255, 255 | #00391F | 0, 57, 31 |
| Primary Container | #B2F1CD | 178, 241, 205 | #115136 | 17, 81, 54 |
| On Primary Container | #002113 | 0, 33, 19 | #B2F1CD | 178, 241, 205 |
| Secondary | #4D6356 | 77, 99, 86 | #B4CCBB | 180, 204, 187 |
| Secondary Container | #CFE9D7 | 207, 233, 215 | #364B3F | 54, 75, 63 |
| On Secondary Container | #0A1F15 | 10, 31, 21 | #CFE9D7 | 207, 233, 215 |
| Tertiary (health) | #3B6470 | 59, 100, 112 | #A3CDDB | 163, 205, 219 |
| Tertiary Container | #BFE9F8 | 191, 233, 248 | #214C58 | 33, 76, 88 |
| On Tertiary Container | #001F27 | 0, 31, 39 | #BFE9F8 | 191, 233, 248 |
| Error | #BA1A1A | 186, 26, 26 | #FFB4AB | 255, 180, 171 |
| Error Container | #FFDAD6 | 255, 218, 214 | #93000A | 147, 0, 10 |
| On Error Container | #410002 | 65, 0, 2 | #FFDAD6 | 255, 218, 214 |
| Background / Surface | #F5FBF5 | 245, 251, 245 | #0F1511 | 15, 21, 17 |
| Surface Container Low | #EFF5EF | 239, 245, 239 | #171D19 | 23, 29, 25 |
| Surface Container | #E9EFE9 | 233, 239, 233 | #1B211D | 27, 33, 29 |
| Surface Container High | #E3EAE3 | 227, 234, 227 | #252B27 | 37, 43, 39 |
| Surface Container Highest | #DEE4DE | 222, 228, 222 | #303632 | 48, 54, 50 |
| On Surface (text) | #171D1A | 23, 29, 26 | #DEE4DE | 222, 228, 222 |
| On Surface Variant | #404943 | 64, 73, 67 | #C0C9C2 | 192, 201, 194 |
| Outline | #707973 | 112, 121, 115 | #8A938C | 138, 147, 140 |
| Outline Variant | #C0C9C2 | 192, 201, 194 | #404943 | 64, 73, 67 |

---

## 2. Warning / reminder colours
Used for the budget alert and "vaccination due" cards.

| Role | Light Hex | Light RGB | Dark Hex | Dark RGB |
|---|---|---|---|---|
| Warning | #825500 | 130, 85, 0 | #FFB960 | 255, 185, 96 |
| Warning Container | #FFDDB8 | 255, 221, 184 | #633F00 | 99, 63, 0 |
| On Warning Container | #4A2800 | 74, 40, 0 | #FFDDB8 | 255, 221, 184 |

---

## 3. Task category colours
Light theme values. In dark theme, swap background and icon/text colour.

| Category | Background Hex | Background RGB | Icon/Text Hex | Icon/Text RGB |
|---|---|---|---|---|
| Feeding | #FFDDB8 | 255, 221, 184 | #653E00 | 101, 62, 0 |
| Exercise | #B2F1CD | 178, 241, 205 | #00522F | 0, 82, 47 |
| Grooming | #D6E3FF | 214, 227, 255 | #004494 | 0, 68, 148 |
| Medication | #F4DAFF | 244, 218, 255 | #5B2B73 | 91, 43, 115 |
| Healthcare | #FFD9DE | 255, 217, 222 | #8C1D3A | 140, 29, 58 |
| Cleaning | #E0E3D8 | 224, 227, 216 | #434839 | 67, 72, 57 |

---

## 4. Selectable colour themes (Appearance screen)

| Theme | Primary Hex | Primary RGB | Container Hex | Container RGB |
|---|---|---|---|---|
| Forest (default) | #2E6A4E | 46, 106, 78 | #B2F1CD | 178, 241, 205 |
| Ocean | #305F95 | 48, 95, 149 | #D6E3FF | 214, 227, 255 |
| Sunset | #8F4C1B | 143, 76, 27 | #FFDCC2 | 255, 220, 194 |
| Lavender | #6A4F86 | 106, 79, 134 | #EFDBFF | 239, 219, 255 |

---

## 5. Other UI colours used in the design

| Use | Hex | RGB |
|---|---|---|
| Snackbar background | #2C322E | 44, 50, 46 |
| Snackbar text | #ECF2EC | 236, 242, 236 |
| Snackbar action ("Undo") | #96D5B2 | 150, 213, 178 |
| Dialog / sheet scrim | #000000 at 38% | 0, 0, 0, 0.38 |
| Map placeholder – land | #E7ECE3 | 231, 236, 227 |
| Map placeholder – park | #CBE3C4 | 203, 227, 196 |
| Map placeholder – water | #C9DEEA | 201, 222, 234 |
| Map placeholder – main road | #FBF3D9 | 251, 243, 217 |
| Photo placeholder – Max | #E6DCC6 | 230, 220, 198 |
| Photo placeholder – Luna | #D9DEE6 | 217, 222, 230 |
| Photo placeholder – label/icon | #5B4A2A | 91, 74, 42 |

---

## 6. Accessibility notes (for the report)

- Body text (#171D1A on #F5FBF5) and secondary text (#404943 on #F5FBF5) both exceed the WCAG AA contrast ratio of 4.5:1.
- Category colours differ in lightness as well as hue, and every category always shows an icon and a text label, so users with colour-blindness can still tell them apart.
- Error states use red plus a written message, never colour alone.
- Dark theme uses the Material 3 tonal palette, so contrast is preserved when the user switches theme.

---
---

# PART 2 – Design Prompt

Use this prompt in an AI design tool (Google Stitch, Figma AI / First Draft, Uizard, Visily, v0, or Claude) to regenerate or extend the PetCare screens. Paste the **Master prompt** first, then paste each **Screen prompt** one at a time.

## 2.1 Master prompt (paste first)

```
Design a native Android mobile app called "PetCare" following Google Material Design 3 (Material You).
Frame size: 390 × 844 (phone portrait). Long forms may scroll.

PURPOSE: Help pet owners manage daily pet care: pet profiles, care routines and
checklists, reminders, delegating care tasks by SMS, geotagging pet places on a
map, health records and expense tracking. Sample users: Emily (owner), her
Golden Retriever "Max", her cat "Luna", and her brother "Daniel" (pet sitter).

VISUAL STYLE
- Material 3 tonal colour scheme, seed colour Forest green #2E6A4E.
- Primary #2E6A4E, On Primary #FFFFFF, Primary Container #B2F1CD, On Primary Container #002113.
- Secondary Container #CFE9D7 (selected chips, nav indicator), On Secondary Container #0A1F15.
- Tertiary #3B6470 / Tertiary Container #BFE9F8 for health & appointments.
- Warning Container #FFDDB8 / On #4A2800 for "due soon" and budget alerts.
- Error #BA1A1A / Error Container #FFDAD6.
- Background/Surface #F5FBF5; surface containers #EFF5EF, #E9EFE9, #E3EAE3, #DEE4DE.
- Text #171D1A, secondary text #404943, outline #707973, divider #C0C9C2.
- Font: Figtree. Headline 26–28px bold, title 22px semibold, body 16px, label 14px, caption 12–13px.
- Rounded corners: cards 12–16px, chips 8px, buttons fully rounded (pill), FAB 16px,
  dialogs and bottom sheets 28px, text fields 4px (outlined).
- Icons: outlined line icons, 24px, 2px stroke. No emoji.
- Spacing on an 8dp grid, 16px screen margins, minimum touch target 48dp.
- Clean, friendly, calm; no gradients, no heavy shadows, no fake status bar.

TASK CATEGORY COLOURS (always shown with an icon + text label):
Feeding #FFDDB8 / #653E00 (bowl icon) · Exercise #B2F1CD / #00522F (walking icon) ·
Grooming #D6E3FF / #004494 (scissors) · Medication #F4DAFF / #5B2B73 (pill) ·
Healthcare #FFD9DE / #8C1D3A (heart) · Cleaning #E0E3D8 / #434839 (broom).

COMPONENTS (Material 3): small top app bar (64px), navigation bar with 5 items and
pill-shaped active indicator (Today, Pets, Places, Expenses, More), extended FAB,
filled / tonal / outlined / text buttons, outlined text fields with floating labels,
filter chips, segmented buttons, switches, checkboxes, radio buttons, cards,
modal bottom sheets, dialogs, snackbars with Undo, progress bars.

ACCESSIBILITY: WCAG AA contrast (4.5:1 for text), colour never used alone,
clear error messages under fields, confirmation before destructive actions.

IMPORTANT: Do NOT use swipe or shake gestures for task actions — use checkboxes
and overflow menus. The only "desirable feature" is Geotagging (map of saved places).
```

## 2.2 Screen prompts (paste one at a time)

### A. Entry & account

**1. Home / Welcome**
```
Welcome screen. Top 330px: Primary Container (#B2F1CD) panel with rounded bottom corners (48px)
containing a large rounded-square Primary logo tile with a white paw icon (illustration placeholder).
Below: "PetCare" (40px extra-bold), tagline "Feeding, health records and care routines for every pet — in one place."
Three feature rows with round tonal icon badges: checklists & reminders; share care by SMS; save vet and dog park on a map.
Bottom: full-width filled button "Create account", outlined button "Log in", small Terms & Privacy text.
```

**2. Sign up**
```
Registration screen with back arrow. Title "Create your account", subtitle.
Outlined button "Sign up with Google" (G logo), divider "or".
Outlined text fields with leading icons: Full name, Email, Mobile number (helper: "Used for reminders only"),
Password (eye toggle, helper "At least 8 characters with 1 number"),
Confirm password shown in ERROR state (red border, "Passwords don't match").
Checkbox "I agree to the Terms and Privacy Policy". Filled button "Create account". Link "Already have an account? Log in".
```

**3. Log in**
```
Login screen with back arrow. Paw logo tile, "Welcome back", subtitle.
Fields: Email, Password (eye toggle). Row: checkbox "Remember me" + text button "Forgot password?".
Row: filled "Log in" button + square tonal fingerprint icon button. Divider "or".
Outlined "Continue with Google". Footer link "New to PetCare? Create an account".
```

**4. Forgot password**
```
Back arrow, lock icon tile (Tertiary Container), "Reset your password", explanation text.
Email field, filled button "Send reset link". Success banner in Primary Container with check icon:
"Link sent. Check your inbox… expires in 1 hour." Text button "Back to log in".
```

**5. Fingerprint unlock**
```
Full Primary Container background with PetCare logo and "Welcome back, Emily".
Floating system-style BiometricPrompt card at the bottom (28px radius): "Unlock PetCare",
"Use your fingerprint to continue", large circular fingerprint icon, "Touch the fingerprint sensor",
text buttons "Use password" and "Cancel".
```

**6. More / Settings**
```
Top app bar "More". Profile card: avatar "E", Emily Carter, email, edit icon.
Section REMINDERS: switch rows Feeding (on), Medication (on), Grooming (off), Vet & vaccinations (on).
Section SECURITY & APPEARANCE: Unlock with fingerprint (on), Lock when app closes (on), Appearance → .
Section ACCOUNT & DATA: Delegation history (3 messages sent), Cloud backup switch, Export all data (PDF or CSV),
Change password, Log out (red). Bottom navigation with "More" active.
```

### B. Dashboard & pets

**7. Today (dashboard)**
```
Header: date "Friday, 25 September", "Good morning, Emily", bell icon, avatar.
Primary Container progress card "Today's care – 5 of 9 done" with progress bar and chips "Max · 3 left", "Luna · 1 left".
Filter chips: All pets (selected), Max, Luna. Section "Up next" + "See checklist".
Outlined card with 3 task rows (checkbox, category badge, title, pet · time · detail, overflow ⋮):
Evening meal – Max 18:00; Dinner – Luna 18:30; Evening walk – Max 19:00.
Section "Reminders": Tertiary Container card "Luna · Annual vaccination – Tue 29 Sep · 10:30 · Riverside Vet Clinic".
Bottom navigation, Today active.
```

**8. Today – dark theme**
```
Same as the Today screen but in Material 3 dark theme: background #0F1511, surfaces #171D19–#303632,
primary #96D5B2, primary container #115136, text #DEE4DE, secondary text #C0C9C2.
Category badges use dark containers with light icons. Greeting "Good evening, Emily".
```

**9. My pets**
```
Top app bar "My pets" with search icon. Two elevated cards (16px radius), each with a 150px photo placeholder,
name (22px bold), details line, and status tags:
Max – Golden Retriever · Male · 4 yrs · 32 kg – tags "3 tasks left today", "Check-up 12 Oct".
Luna – Domestic shorthair · Female · 2 yrs · 4.1 kg – warning tag "Vaccination due 29 Sep", "1 task left".
Extended FAB "Add pet". Bottom navigation, Pets active.
```

**10. Pet profile (Max)**
```
250px cover photo with round translucent buttons: back, edit, delete; photo counter "1 / 6".
Name "Max" + "Dog" tag, "Golden Retriever · Male · Born 12 Mar 2022".
Three stat tiles: 32 kg Weight, 4 yrs Age, 12 Oct Next vet.
Tabs: Overview (active), Routine, Health, Photos.
Diet card, Allergies tag "Chicken" (error colour), Favourite toys tags,
card "Weekly care routine – 7 tasks · 3 left today" with buttons "Open checklist" and "Delegate".
```

**11. Edit pet**
```
Top bar: close X, "Edit pet", "Save". Circular profile photo with camera badge, row of photo thumbnails + add tile.
Fields: Name, Species segmented (Dog selected / Cat / Other), Breed, Date of birth + Weight side by side,
Dietary preferences, Allergies chips (removable) + Add, Favourite toys chips + Add,
list item "Vaccination history – 3 records", Notes (multi-line).
Bottom row: outlined red "Delete pet" + filled "Save changes".
```

### C. Care routines & checklist

**12. Create care routine**
```
Top bar: close X, "New care routine", Save. Field "Routine name: Max – weekly care".
"For which pet?" chips Max (selected), Luna, Both pets. "Repeats" segmented Daily / Weekly (selected).
Seven circular day toggles M T W T F S S. Start date field.
"Tasks (5)" with tonal "Add task" button; task rows with drag handle, category badge, name, schedule, edit icon:
Morning meal Daily 07:30, Morning walk Daily 08:00, Evening meal Daily 18:00, Grooming Saturdays 10:00, Flea treatment Monthly 1st.
Required supplies tags + Add. Notes field. Photo tiles (one photo + dashed Add).
Switch "Remind me before each task – 15 minutes before". Filled "Save routine",
caption "Saving generates Max's daily checklist automatically."
```

**13. Edit care task**
```
Top bar: back, "Edit task", delete icon. Task name "Evening meal".
Category chips (Feeding selected, Exercise, Grooming, Medication, Healthcare, Cleaning). Pet chips (Max selected).
Time 18:00 + Repeat Daily side by side. Instructions multi-line. Supplies tags.
Reminder switch (15 minutes before). Location field "Link a saved place". Buttons Cancel / Save changes.
```

**14. Daily checklist**
```
Top bar: back, "Daily checklist", send (delegate) icon, overflow.
Week strip Mon 21 – Sun 27 with Fri 25 selected (filled primary pill).
Chips All pets / Max / Luna. "5 of 9 done" with progress bar.
Grouped list with small green uppercase group headers FEEDING, EXERCISE, GROOMING & MEDICATION, CLEANING.
Each row: checkbox, category badge, title (strikethrough + grey when done), pet · time, overflow ⋮.
Extended FAB "Add task".
```

**15. Task actions (bottom sheet)**
```
Checklist dimmed by a 38% black scrim. Modal bottom sheet (28px top radius, drag handle):
header with Exercise badge "Evening walk – Max · Daily · 19:00 · 30 min".
Options with icons: Mark as done (primary), Edit task, Send by SMS, Link to a saved place, Delete task (red).
```

**16. Delete confirmation**
```
Checklist dimmed. Centered Material 3 dialog (28px radius): trash icon, "Delete this task?",
body "'Evening walk' will be removed from Max's weekly routine and today's checklist.",
text buttons Cancel and Delete (red). Snackbar at the bottom "Task deleted" with "Undo".
```

### D. Delegation by SMS

**17. Delegate care**
```
Top bar: back, "Delegate care". Intro text. "What to send" radio cards:
Care checklist (selected), Feeding schedule, Medication reminders, Full daily routine.
Pet chips. From / To date fields (Sat 26 Sep – Tue 29 Sep).
"Include" checkbox list: Meals, Walks, Flea drops, Grooming (unchecked), Vet contact.
"Send to" field "Daniel Carter" with contacts picker button, helper "07700 900123 · from Contacts".
Filled button "Preview message".
```

**18. Review SMS**
```
Top bar: back, "Review message". "To" chip with avatar "Daniel · 07700 900123".
Right-aligned chat bubble (Primary Container) with the SMS text: Max's meals, walks, flea drops, vet phone, signed Emily.
Caption "251 characters · 2 SMS parts". "Send using" radio cards: My messaging app (selected) / Send directly from PetCare (needs SMS permission).
Info card: copy saved to Delegation history. Buttons "Edit text" (outlined) and "Send SMS" (filled, send icon).
```

### E. Geotagging (desirable feature)

**19. Places map**
```
Full-screen map. Floating search bar "Search saved places" with filter icon.
Horizontal filter chips: All, Vets, Groomers, Parks, Stores. Coloured map pins by category + blue "my location" dot.
Round "my location" button. Bottom sheet "5 saved places" listing Riverside Vet Clinic (1.2 km, open until 18:00),
Greenfield Dog Park (0.8 km), Paws & Suds Grooming (2.4 km, closed). Extended FAB "Tag place". Bottom nav, Places active.
```

**20. Tag a place**
```
Top bar: close, "Tag a place", Save. Mini map with centre pin and hint "Drag map to move the pin".
Tonal button "Use my current location", caption with latitude/longitude and accuracy.
Fields: Place name, Address, Category chips (Vet selected, Groomer, Dog park, Pet store, Shelter),
Phone, Opening hours, Linked pets chips (Max, Luna). Filled "Save place".
```

**21. Place detail**
```
220px map header with pin and back button. "Riverside Vet Clinic", Vet tag, "Open · closes 18:00 · 1.2 km", address.
Four tonal action tiles: Directions, Call, Share, Edit.
"Linked appointments" card: Luna – Annual vaccination Tue 29 Sep 10:30; Max – Check-up Mon 12 Oct 09:00.
Opening hours table. Red text button "Remove this place".
```

### F. Health & expenses

**22. Health records (Max)**
```
Top bar: back, "Max · Health". Segmented Vaccinations (selected) / Medical / Weight.
Warning card "Leptospirosis booster due – Mon 12 Oct · Riverside Vet Clinic" with "Add to checklist" and "View place".
History list: Leptospirosis, DHPP (Due soon tags), Rabies (Up to date).
Medical notes: Ear infection (resolved), Allergy test (chicken). Extended FAB "Add record".
```

**23. Expenses**
```
Top bar "Expenses" with download and filter icons. Month switcher "September 2026".
Primary Container total card "$286.40" with split bar Max $198.20 / Luna $88.20.
Budget card "Monthly budget $300 – 95% used · $13.60 left" with progress bar.
Chips All pets / Max / Luna. "By category" horizontal bars: Food, Vet visits, Grooming, Medication, Toys.
"Recent" list with category badges and amounts. Extended FAB "Add expense". Bottom nav, Expenses active.
```

**24. Add expense**
```
Top bar: close, "Add expense", Save. Large centred amount "$ 45.00" with underline.
Pet chips, Category chips (Grooming selected), Date field, "Where" field linked to saved places,
Note field, dashed "Attach receipt photo" tile, filled "Save expense".
```

**25. Download expense report (bottom sheet)**
```
Expenses screen dimmed. Bottom sheet "Download expense report":
Format segmented "PDF report" (selected) / "CSV (Excel)". Period chips: This month (selected), Last 3 months, This year, Custom.
Pet chips. Checkboxes "Include category chart", "Attach receipt photos".
Caption "Saved to Downloads/PetCare · PetCare_Sep2026.pdf". Buttons Share (outlined) and Download (filled).
```

### G. Extra features

**26. Appearance**
```
Top bar: back, "Appearance". Theme segmented Light / Dark / System (selected).
Switch "Dynamic colour (Material You) – Android 12+". Colour theme swatches: Forest (selected), Ocean, Sunset, Lavender.
Text size slider. Switch "High-contrast text". Preview card showing one task row.
```

**27. Reminder notifications**
```
Dark notification-shade background (#2C322E). Three Android notification cards (24px radius) from PetCare:
"Time for Max's evening meal" – actions Mark done / Snooze 10 min;
"Luna's vaccination tomorrow" – actions Directions / Add to calendar;
"Delegated task completed – Daniel marked 'Morning walk' as done" – action Open checklist.
```

**28. Screen hierarchy diagram (for the report)**
```
Wide 1800 × 1060 diagram titled "PetCare — screen hierarchy".
Home → Sign up / Log in / Fingerprint unlock / Forgot password / Google account (external) →
"Main shell · bottom navigation bar" → six columns:
Today (Checklist → Task actions → Edit task → Delete dialog),
Pets (Pet profile → Edit pet → Create routine → Health records),
Delegate (Contacts picker → Review SMS → SMS app),
Places (Tag a place → Place detail → Google Maps directions),
Expenses (Add expense → Download report → Share sheet),
More (Reminders → Appearance → Reminder notification → Log out).
Legend: green filled = navigation destination, white = screen, dashed = dialog/sheet, blue = external app/system.
```

---

## 2.3 Tips for using the prompts

- Generate one screen per prompt; long single prompts produce inconsistent results.
- If the tool drifts, re-paste the Master prompt and say "keep the same design system as before".
- For the report, export each screen as PNG and caption it with the Material 3 components it uses. The brief rejects screenshots of Android Studio XML layouts, so use these wireframes instead.
