package com.example.petcare

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Read-only history for one pet at a time: overview, care activity, expenses and health records.
 * Nothing here can be edited or deleted; that happens on the pet's own screens.
 */
class PetHistoryActivity : AppCompatActivity() {

    private data class History(
        val name: String,
        val species: String,
        val breed: String,
        val age: Int,
        val weight: Double,
        val diet: String,
        val allergies: String,
        val vaccineDate: String,
        val createdAt: Long,
        val photo: String?,
        val activeRoutines: Int,
        val completions: List<CompletionEntry>,
        val expenses: List<ExpenseTransaction>,
        val records: List<HealthcareRecord>
    )

    private data class Row(
        val title: String,
        val subtitle: String,
        val trailing: String,
        val icon: Int,
        val iconBg: Int,
        val iconFg: Int,
        val trailingColor: Int = R.color.app_text_primary
    )

    private lateinit var database: AuthDatabaseHelper
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var selectedPetId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pet_history)
        database = AuthDatabaseHelper(this)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        selectedPetId = savedInstanceState?.getLong(STATE_PET_ID) ?: intent.getLongExtra(EXTRA_PET_ID, -1L)
        loadPets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_PET_ID, selectedPetId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // region Loading

    private fun loadPets() {
        runInBackground({ database.getPetOptions() }) { pets ->
            if (pets.isEmpty()) {
                findViewById<View>(R.id.textHistoryEmpty).visibility = View.VISIBLE
                return@runInBackground
            }
            if (pets.none { it.id == selectedPetId }) selectedPetId = pets.first().id

            val group = findViewById<ChipGroup>(R.id.chipGroupHistoryPets)
            group.removeAllViews()
            pets.forEach { pet ->
                val chip = Chip(this).apply {
                    id = View.generateViewId()
                    tag = pet.id
                    text = pet.name
                    isCheckable = true
                    isCheckedIconVisible = false
                    chipStrokeWidth = resources.displayMetrics.density
                    setTextColor(ContextCompat.getColorStateList(this@PetHistoryActivity, R.color.chip_selectable_text))
                    chipBackgroundColor = ContextCompat.getColorStateList(this@PetHistoryActivity, R.color.chip_selectable_bg)
                    chipStrokeColor = ContextCompat.getColorStateList(this@PetHistoryActivity, R.color.chip_selectable_stroke)
                }
                group.addView(chip)
                if (pet.id == selectedPetId) group.check(chip.id)
            }
            group.setOnCheckedStateChangeListener { g, ids ->
                val petId = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Long } ?: return@setOnCheckedStateChangeListener
                selectedPetId = petId
                loadHistory(petId)
            }
            loadHistory(selectedPetId)
        }
    }

    private fun loadHistory(petId: Long) {
        runInBackground({ readHistory(petId) }) { history ->
            // Ignore late results if the user already switched to another pet.
            if (history == null || petId != selectedPetId) return@runInBackground
            render(history)
        }
    }

    private fun readHistory(petId: Long): History? {
        val pet = database.getPetById(petId) ?: return null
        return History(
            name = pet.getAsString("name").orEmpty(),
            species = pet.getAsString("species").orEmpty(),
            breed = pet.getAsString("breed").orEmpty(),
            age = pet.getAsInteger("age") ?: 0,
            weight = pet.getAsDouble("weight") ?: 0.0,
            diet = pet.getAsString("diet").orEmpty(),
            allergies = pet.getAsString("allergies").orEmpty(),
            vaccineDate = pet.getAsString("vaccine_date").orEmpty(),
            createdAt = pet.getAsLong("created_at") ?: 0L,
            photo = database.getPetPhotos(petId).firstOrNull(),
            activeRoutines = database.getCareTasks(petId).size,
            completions = database.getCompletionHistory(petId),
            expenses = database.getExpenses(petId)
                .sortedByDescending { parseExpenseDate(it.date)?.time ?: Long.MIN_VALUE },
            records = database.getHealthcareHistory(petId)
        )
    }

    // endregion

    // region Rendering

    private fun render(h: History) {
        findViewById<TextView>(R.id.textHistoryName).text = h.name.ifBlank { "Unnamed pet" }
        findViewById<TextView>(R.id.textHistorySubtitle).text = listOfNotNull(
            h.species.takeIf { it.isNotBlank() && it != "Unknown" },
            h.breed.takeIf { it.isNotBlank() },
            h.createdAt.takeIf { it > 0 }?.let { "With you since ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(it)}" }
        ).joinToString(" · ")

        val avatar = findViewById<ShapeableImageView>(R.id.imageHistoryAvatar)
        val placeholder = findViewById<View>(R.id.imageHistoryAvatarPlaceholder)
        if (h.photo != null) {
            placeholder.visibility = View.GONE
            PetImageLoader.load(avatar, h.photo, 64.dp() * 2) { placeholder.visibility = View.VISIBLE }
        } else {
            avatar.tag = null
            avatar.setImageDrawable(null)
            placeholder.visibility = View.VISIBLE
        }

        renderFacts(h)
        findViewById<TextView>(R.id.textHistoryDone).text = h.completions.size.toString()
        findViewById<TextView>(R.id.textHistorySpent).text = formatMoney(h.expenses.sumOf { it.amount })
        findViewById<TextView>(R.id.textHistoryRecords).text = h.records.size.toString()

        val sections = findViewById<LinearLayout>(R.id.layoutHistorySections)
        sections.removeAllViews()
        sections.addView(careSection(h))
        sections.addView(expenseSection(h))
        sections.addView(healthSection(h))

        findViewById<View>(R.id.layoutHistoryContent).visibility = View.VISIBLE
    }

    private fun renderFacts(h: History) {
        val allergiesSet = h.allergies.isNotBlank() && h.allergies.lowercase() !in listOf("none", "no known allergies")
        val facts = listOf(
            "Age" to (if (h.age > 0) "${h.age} ${if (h.age == 1) "yr" else "yrs"}" else "Not added"),
            "Weight" to (if (h.weight > 0) "${formatNumber(h.weight)} kg" else "Not added"),
            "Diet" to h.diet.ifBlank { "Not added" },
            "Allergies" to h.allergies.ifBlank { "Not added" },
            "Next vaccination" to vaccineText(h.vaccineDate),
            "Active routines" to h.activeRoutines.toString()
        )
        val container = findViewById<LinearLayout>(R.id.layoutHistoryFacts)
        container.removeAllViews()
        facts.forEach { (label, value) ->
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6.dp(), 0, 6.dp())
                addView(TextView(this@PetHistoryActivity).apply {
                    text = label
                    setTextColor(color(R.color.app_text_secondary))
                    textSize = 13f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f))
                addView(TextView(this@PetHistoryActivity).apply {
                    text = value
                    gravity = Gravity.END
                    textSize = 13f
                    typeface = context.figtree(android.graphics.Typeface.BOLD)
                    setTextColor(color(
                        when {
                            label == "Allergies" && allergiesSet -> R.color.app_accent_red
                            label == "Next vaccination" && value.startsWith("Overdue") -> R.color.app_accent_red
                            value == "Not added" -> R.color.app_text_secondary
                            else -> R.color.app_text_primary
                        }
                    ))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f))
            })
        }
    }

    private fun vaccineText(stored: String): String {
        val date = parseExpenseDate(stored) ?: return stored.ifBlank { "Not scheduled" }
        val days = daysFromToday(date.time)
        val shown = displayDate(stored)
        return when {
            days < 0 -> "Overdue · $shown"
            days == 0 -> "Today"
            else -> "$shown (in $days d)"
        }
    }

    private fun careSection(h: History): View {
        val byDay = h.completions.groupBy { it.date }
        val dayFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val rows = byDay.map { (date, entries) ->
            Row(
                title = runCatching { dayFormat.format(keyFormat.parse(date)!!) }.getOrDefault(date),
                subtitle = entries.joinToString(", ") { it.description.ifBlank { it.category.ifBlank { "Routine" } } },
                trailing = "${entries.size} done",
                icon = R.drawable.ic_status_check,
                iconBg = R.color.cat_exercise_bg,
                iconFg = R.color.cat_exercise_fg,
                trailingColor = R.color.app_text_secondary
            )
        }
        val since = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -29) }
        val last30 = h.completions.count { it.date >= AuthDatabaseHelper.dateKey(since) }
        return section(
            title = "Care activity",
            summary = "$last30 routines done in the last 30 days · ${byDay.size} active days in total",
            rows = rows,
            empty = "No routines completed yet."
        )
    }

    private fun expenseSection(h: History): View {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val thisMonth = h.expenses.filter { (parseExpenseDate(it.date)?.time ?: 0L) >= monthStart }.sumOf { it.amount }
        val rows = h.expenses.map { e ->
            val style = e.category.expenseCategoryStyle()
            Row(
                title = e.description.ifBlank { style.label },
                subtitle = "${style.label} · ${displayDate(e.date)}",
                trailing = formatMoney(e.amount),
                icon = style.iconRes,
                iconBg = style.backgroundRes,
                iconFg = style.colorRes
            )
        }
        return section(
            title = "Expenses",
            summary = "${h.expenses.size} transactions · ${formatMoney(thisMonth)} this month",
            rows = rows,
            empty = "No expenses recorded."
        )
    }

    private fun healthSection(h: History): View {
        val today = startOfToday()
        val (upcoming, past) = h.records.partition { (parseExpenseDate(it.date)?.time ?: Long.MIN_VALUE) > today }
        val ordered = upcoming.sortedBy { parseExpenseDate(it.date)!!.time } +
            past.sortedByDescending { parseExpenseDate(it.date)?.time ?: Long.MIN_VALUE }
        val rows = ordered.map { r ->
            val style = healthRecordStyle(r.type)
            val isUpcoming = r in upcoming
            Row(
                title = r.type.ifBlank { "Record" },
                subtitle = listOf(displayDate(r.date), r.notes).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = if (isUpcoming) "Upcoming" else "",
                icon = style.icon,
                iconBg = style.bg,
                iconFg = style.fg,
                trailingColor = R.color.md_warning
            )
        }
        return section(
            title = "Health records",
            summary = "${h.records.size} records" + if (upcoming.isNotEmpty()) " · ${upcoming.size} upcoming" else "",
            rows = rows,
            empty = "No health records yet."
        )
    }

    /** A titled card listing [rows]; long lists start collapsed with a "Show all" toggle. */
    private fun section(title: String, summary: String, rows: List<Row>, empty: String): View {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(TextView(this).apply {
            text = title
            setTextColor(color(R.color.app_text_primary))
            textSize = 17f
            typeface = context.figtree(android.graphics.Typeface.BOLD)
            setPadding(4.dp(), 24.dp(), 0, 0)
        })
        wrapper.addView(TextView(this).apply {
            text = summary
            setTextColor(color(R.color.app_text_secondary))
            textSize = 12f
            setPadding(4.dp(), 2.dp(), 0, 8.dp())
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(color(R.color.card_bg))
            radius = 20.dp().toFloat()
            cardElevation = 0f
            strokeColor = color(R.color.md_outline_variant)
            strokeWidth = 1.dp()
            addView(list)
        }
        wrapper.addView(card)

        if (rows.isEmpty()) {
            list.addView(TextView(this).apply {
                text = empty
                gravity = Gravity.CENTER
                setTextColor(color(R.color.app_text_secondary))
                textSize = 13f
                setPadding(16.dp(), 20.dp(), 16.dp(), 20.dp())
            })
            return wrapper
        }

        val inflater = LayoutInflater.from(this)
        rows.forEachIndexed { index, row ->
            val view = inflater.inflate(R.layout.item_history_row, list, false)
            view.findViewById<View>(R.id.historyIconBg).backgroundTintList = ContextCompat.getColorStateList(this, row.iconBg)
            view.findViewById<ImageView>(R.id.historyIcon).apply {
                setImageResource(row.icon)
                imageTintList = ContextCompat.getColorStateList(this@PetHistoryActivity, row.iconFg)
            }
            view.findViewById<TextView>(R.id.historyTitle).text = row.title
            view.findViewById<TextView>(R.id.historySubtitle).apply {
                text = row.subtitle
                visibility = if (row.subtitle.isBlank()) View.GONE else View.VISIBLE
            }
            view.findViewById<TextView>(R.id.historyTrailing).apply {
                text = row.trailing
                setTextColor(color(row.trailingColor))
                visibility = if (row.trailing.isBlank()) View.GONE else View.VISIBLE
            }
            // Tag each row (and the divider above it) with its position for "Show all".
            if (index > 0) list.addView(divider().apply { tag = index })
            view.tag = index
            list.addView(view)
        }

        if (rows.size > COLLAPSED_ROWS) {
            // Rows past the first few stay hidden until "Show all" is tapped.
            fun setExpanded(expanded: Boolean) {
                for (i in 0 until list.childCount) {
                    val child = list.getChildAt(i)
                    val index = child.tag as? Int ?: continue
                    child.visibility = if (expanded || index < COLLAPSED_ROWS) View.VISIBLE else View.GONE
                }
            }
            val toggle = TextView(this).apply {
                text = "Show all (${rows.size})"
                gravity = Gravity.CENTER
                setTextColor(color(R.color.app_accent_blue))
                textSize = 13f
                typeface = context.figtree(android.graphics.Typeface.BOLD)
                setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                val bg = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
                setBackgroundResource(bg.resourceId)
            }
            var expanded = false
            toggle.setOnClickListener {
                expanded = !expanded
                setExpanded(expanded)
                toggle.text = if (expanded) "Show less" else "Show all (${rows.size})"
            }
            setExpanded(false)
            list.addView(divider(0))
            list.addView(toggle)
        }
        return wrapper
    }

    // endregion

    // region Helpers

    private fun <T> runInBackground(work: () -> T, onResult: (T) -> Unit) {
        if (executor.isShutdown) return
        executor.execute {
            val result = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) onResult(result) }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysFromToday(time: Long): Int = TimeUnit.MILLISECONDS.toDays(time - startOfToday()).toInt()

    private fun divider(start: Int = 66.dp()) = View(this).apply {
        setBackgroundColor(color(R.color.md_outline_variant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = start }
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

    private fun color(res: Int) = ContextCompat.getColor(this, res)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    // endregion

    companion object {
        const val EXTRA_PET_ID = "extra_history_pet_id"
        private const val STATE_PET_ID = "state_history_pet_id"
        private const val COLLAPSED_ROWS = 5
    }
}
