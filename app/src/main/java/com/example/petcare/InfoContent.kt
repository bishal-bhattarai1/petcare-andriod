package com.example.petcare

/**
 * Text for the Help Center, Privacy Policy and Terms screens ([InfoActivity]).
 * Keep this in sync with what the app actually does; the policy describes real data handling.
 */
object InfoContent {

    /** Where "Contact support" emails go. Replace with the real support inbox before release. */
    const val SUPPORT_EMAIL = "support@petcare.app"

    const val LAST_UPDATED = "26 September 2026"

    data class Section(val title: String, val body: String)

    val faq = listOf(
        Section(
            "How do I add a pet?",
            "On Home, tap Add pet (or the + button). Only the name is required; you can add photos, species, breed, age, weight, diet, allergies and a vaccination date now or later."
        ),
        Section(
            "How do I set up daily care routines?",
            "Open the Tasks tab and tap +. Choose the pet, the category (feeding, walk, grooming, medication…), a time and how often it repeats. Routines appear on Home and in each pet's checklist."
        ),
        Section(
            "How do I mark a routine as done?",
            "Tap the circle next to it in the checklist, or swipe the task right on the Tasks page. Tap again (or use Undo) to mark it as not done."
        ),
        Section(
            "Why can't I edit a completed routine?",
            "Completed routines are locked so your history stays accurate. Mark it as not done first, then edit it."
        ),
        Section(
            "How do I track and download expenses?",
            "Open the Expenses tab and tap + to add one. Use the filters to choose a pet or time period, then tap the download icon to save a PDF report or CSV spreadsheet to Downloads/PetCare."
        ),
        Section(
            "How do reminders work?",
            "Turn on notifications in Profile. For vaccinations, add a date on the pet's profile and keep the reminder switch on; you'll be notified at 9:00 AM that day."
        ),
        Section(
            "What does shaking my phone do?",
            "On the main screens, shaking the phone offers to reset today's completed routines. Nothing changes unless you confirm."
        ),
        Section(
            "How do I delete a pet?",
            "Open the pet's profile, tap the ⋮ menu and choose Delete. This also removes its routines, expenses, photos and health records and can't be undone."
        ),
        Section(
            "Is my data backed up?",
            "Your data is stored on this phone. Uninstalling the app or clearing its data removes it. Android's own device backup may include it if backup is turned on in your phone's settings."
        )
    )

    val privacy = listOf(
        Section(
            "Overview",
            "PetCare helps you look after your pets. This policy explains what information the app uses, where it is kept and the choices you have. By using PetCare you agree to this policy."
        ),
        Section(
            "Information you provide",
            "• Account details: your name, email address and password (stored as a one-way hash, never as plain text).\n" +
                "• Pet details: names, photos, species, breed, age, weight, diet, allergies, notes and health records.\n" +
                "• Care routines, expenses, saved places and your emergency contact."
        ),
        Section(
            "Where your data is stored",
            "Everything above is stored in a database on your device. PetCare does not upload your pets, routines, expenses or photos to our servers, and we do not sell your information or use it for advertising."
        ),
        Section(
            "Photos",
            "When you add a pet photo, the app keeps a reference to the image you chose on your device; it does not copy it elsewhere. If you delete the original photo from your phone it will no longer appear in the app."
        ),
        Section(
            "Location and maps",
            "If you allow location access, it is used only to show your position on the map when you save a place. Maps and address search are provided by OpenStreetMap services, which receive the map area you view and the text you search for. See openstreetmap.org for their privacy policy."
        ),
        Section(
            "Google Sign-In",
            "If you sign in with Google, Google shares your name and email address with the app to create your account. Google's own privacy policy applies to that sign-in."
        ),
        Section(
            "Notifications and biometrics",
            "Reminders are scheduled on your device. Fingerprint or face unlock is handled by Android; PetCare never receives or stores your biometric data."
        ),
        Section(
            "Exports and sharing",
            "Expense reports you download are saved to your phone's Downloads folder. Once you share or move a file, it is handled by the app or person you shared it with."
        ),
        Section(
            "Your choices",
            "You can edit or delete pets, routines, expenses and records at any time. Logging out keeps your data on the device; uninstalling the app or clearing its data deletes it. You can turn off notifications or location access in your phone settings."
        ),
        Section(
            "Children",
            "PetCare is not directed at children under 13 and we do not knowingly collect their information."
        ),
        Section(
            "Changes and contact",
            "We may update this policy; the date above shows the latest version. Questions? Email $SUPPORT_EMAIL."
        )
    )

    val terms = listOf(
        Section(
            "Acceptance",
            "By creating an account or using PetCare you agree to these terms. If you don't agree, please don't use the app."
        ),
        Section(
            "What PetCare is",
            "PetCare is a personal organiser for pet care: routines, reminders, health records and expenses. It is not a veterinary service."
        ),
        Section(
            "Not veterinary advice",
            "Tips, reminders and records in the app are for general information only. Always follow your veterinarian's advice, and contact a vet or emergency clinic immediately if your pet is unwell."
        ),
        Section(
            "Your account",
            "You are responsible for keeping your login details and your device secure, and for the accuracy of the information you enter."
        ),
        Section(
            "Your content",
            "Photos, notes and other information you add remain yours. Only add photos and information you have the right to use."
        ),
        Section(
            "Reminders and availability",
            "We work to make reminders reliable, but they depend on your device settings (notifications, battery saving, exact alarms) and may be delayed or missed. Don't rely on the app as the only reminder for critical medication or treatment."
        ),
        Section(
            "Data and backups",
            "Your data is stored on your device. You are responsible for keeping backups; we can't recover data lost when the app is uninstalled, the device is reset or its data is cleared."
        ),
        Section(
            "Acceptable use",
            "Don't misuse the app, attempt to break its security, or use it for anything unlawful."
        ),
        Section(
            "Limitation of liability",
            "PetCare is provided \"as is\". To the extent permitted by law, we are not liable for any loss or harm arising from use of the app, including missed reminders or lost data."
        ),
        Section(
            "Changes",
            "We may update these terms. Continuing to use PetCare after an update means you accept the new terms."
        ),
        Section(
            "Contact",
            "Questions about these terms? Email $SUPPORT_EMAIL."
        )
    )
}
