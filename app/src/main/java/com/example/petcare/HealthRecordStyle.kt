package com.example.petcare

/** Icon and colour pair for a health record type (Vaccination, Check-up, Surgery, Medication…). */
data class HealthRecordStyle(val icon: Int, val bg: Int, val fg: Int)

fun healthRecordStyle(type: String): HealthRecordStyle {
    val t = type.lowercase()
    return when {
        "vacc" in t -> HealthRecordStyle(R.drawable.ic_meds, R.color.cat_healthcare_bg, R.color.cat_healthcare_fg)
        "check" in t || "exam" in t -> HealthRecordStyle(R.drawable.ic_status_check, R.color.cat_exercise_bg, R.color.cat_exercise_fg)
        "surg" in t -> HealthRecordStyle(R.drawable.ic_status_alert, R.color.cat_medication_bg, R.color.cat_medication_fg)
        "med" in t -> HealthRecordStyle(R.drawable.ic_meds, R.color.cat_grooming_bg, R.color.cat_grooming_fg)
        else -> HealthRecordStyle(R.drawable.ic_paw, R.color.cat_cleaning_bg, R.color.cat_cleaning_fg)
    }
}
