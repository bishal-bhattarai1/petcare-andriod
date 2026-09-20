# Implementation Plan - Profile UI Polish & Theme Redirect

Elevate the Settings/Profile UI to a premium Material 3 look and implement the requested redirection to the Home section after a theme change.

## User Review Required

> [!IMPORTANT]
> - **Redirection Logic**: When the user changes the theme, the app will apply the theme and immediately switch to the **Home** tab. Since theme changes often recreate the Activity, I will ensure the "start tab" state is reset to Home during this transition.
> - **UI Enhancements**: I will use Material 3 "Tonal" surfaces for cards, improve icon consistency, and add "chevron" (arrow) indicators to all navigation rows to make the UI feel more "high-end" and intuitive.

## Proposed Changes

### UI & Styling Refinements

#### [MODIFY] [activity_profile.xml](file:///C:/Users/bisha/AndroidStudioProjects/PetCare/app/src/main/res/layout/activity_profile.xml)
- **Profile Header**:
  - Add a thin circular stroke around the avatar.
  - Improve the Camera badge positioning and size.
  - Center-align name and email with better vertical rhythm.
- **Section Headers**: Use a slightly larger, more distinct semi-bold font and add subtle top margins.
- **Cards & Rows**:
  - Update `MaterialCardView` to use a more modern corner radius (16dp).
  - Add a `>` (chevron) icon to every clickable row (Personal Info, Change Password, Help, etc.).
  - Ensure consistent padding (16dp) across all items.
  - Use `app_divider` only between items inside a card, not at the bottom of the card.

### Functional Redirection

#### [MODIFY] [DashboardActivity.kt](file:///C:/Users/bisha/AndroidStudioProjects/PetCare/app/src/main/java/com/example/petcare/DashboardActivity.kt)
- **`setupPreferences`**:
  - After `AppCompatDelegate.setDefaultNightMode(mode)`, call `showTab(MainTab.HOME)`.
  - Update the internal `intent` to ensure that on recreation, the app defaults to the Home tab.

#### [MODIFY] [ProfileActivity.kt](file:///C:/Users/bisha/AndroidStudioProjects/PetCare/app/src/main/java/com/example/petcare/ProfileActivity.kt)
- **`setupPreferences`**:
  - If theme is changed in the standalone activity, navigate to `DashboardActivity` with the Home tab explicitly selected before finishing the current activity.

## Verification Plan

### Automated Tests
- `gradle assembleDebug` to ensure all view IDs and logic compile correctly.

### Manual Verification
1.  **UI Polish**: visually inspect the Profile screen. Verify chevrons are present, spacing is even, and the avatar looks high-quality.
2.  **Theme Redirect**:
    - Go to the Profile tab in Dashboard.
    - Change the theme to Dark.
    - Verify the app switches theme AND immediately shows the **Home** tab.
3.  **Standalone Redirect**:
    - Open the standalone Profile screen.
    - Change the theme.
    - Verify it returns to the Dashboard **Home** tab.
