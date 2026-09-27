package com.example.petcare

/** Types of pet places that can be geotagged. Older free-text categories are mapped onto these. */
enum class PlaceCategory(val label: String, val icon: Int, val bg: Int, val fg: Int) {
    VET("Vet clinic", R.drawable.ic_meds, R.color.cat_healthcare_bg, R.color.cat_healthcare_fg),
    GROOMING("Grooming salon", R.drawable.ic_groom, R.color.cat_grooming_bg, R.color.cat_grooming_fg),
    PARK("Dog park", R.drawable.ic_walk, R.color.cat_exercise_bg, R.color.cat_exercise_fg),
    STORE("Pet store", R.drawable.ic_bowl, R.color.cat_feeding_bg, R.color.cat_feeding_fg),
    SHELTER("Animal shelter", R.drawable.ic_paw, R.color.cat_medication_bg, R.color.cat_medication_fg),
    OTHER("Other", R.drawable.ic_location_pin, R.color.cat_cleaning_bg, R.color.cat_cleaning_fg);

    companion object {
        fun from(stored: String): PlaceCategory {
            val c = stored.lowercase()
            return when {
                "vet" in c || "clinic" in c || "hospital" in c -> VET
                "groom" in c || "salon" in c || "spa" in c -> GROOMING
                "park" in c -> PARK
                "store" in c || "shop" in c || "supply" in c || "supplies" in c -> STORE
                "shelter" in c || "rescue" in c || "adopt" in c -> SHELTER
                else -> OTHER
            }
        }
    }
}
