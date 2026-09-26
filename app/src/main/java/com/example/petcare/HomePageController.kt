package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Drives the Home tab of [DashboardActivity]: greeting, today's progress, stats, up next, pets. */
class HomePageController(
    private val activity: AppCompatActivity,
    private val page: View,
    private val database: AuthDatabaseHelper,
    private val sessionManager: SessionManager,
    private val onOpenTasks: () -> Unit,
    private val onOpenExpenses: () -> Unit,
    private val onOpenProfile: () -> Unit
) {
    /** Everything the page shows, loaded together off the main thread. */
    private data class HomeData(
        val pets: List<PetDashboardModel>,
        val tasks: List<CareTask>,
        val monthSpend: Double
    )

    private data class Pending(val task: CareTask, val minutes: Int?)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val petAdapter = PetAdapter(emptyList())

    private val greeting: TextView = page.findViewById(R.id.textGreetingLabel)
    private val userName: TextView = page.findViewById(R.id.textUserName)
    private val avatar: ShapeableImageView = page.findViewById(R.id.imageDashboardAvatar)
    private val initials: TextView = page.findViewById(R.id.textAvatarInitials)
    private val notificationsButton: View = page.findViewById(R.id.buttonNotifications)
    private val notificationBadge: View = page.findViewById(R.id.viewNotificationBadge)
    private val todayProgress: TextView = page.findViewById(R.id.textTodayProgress)
    private val todaySummary: TextView = page.findViewById(R.id.textDailySummary)
    private val todayRing: CircularProgressIndicator = page.findViewById(R.id.progressToday)
    private val todayPercent: TextView = page.findViewById(R.id.textTodayPercent)
    private val statPets: TextView = page.findViewById(R.id.textStatPets)
    private val statPetsLabel: TextView = page.findViewById(R.id.textPetCount)
    private val statDue: TextView = page.findViewById(R.id.textStatDue)
    private val statDueLabel: TextView = page.findViewById(R.id.textStatDueLabel)
    private val statSpend: TextView = page.findViewById(R.id.textStatSpend)
    private val upNextList: LinearLayout = page.findViewById(R.id.layoutUpNext)
    private val upNextEmpty: TextView = page.findViewById(R.id.textUpNextEmpty)
    private val petsEmpty: View = page.findViewById(R.id.cardEmptyPets)
    private val emergencyCard: View = page.findViewById(R.id.cardEmergencyContact)
    private val emergencyText: TextView = page.findViewById(R.id.textEmergencyContact)
    private val emergencyAction: TextView = page.findViewById(R.id.textEmergencyAction)

    fun setup() {
        page.findViewById<RecyclerView>(R.id.recyclerViewPets).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = petAdapter
        }

        page.findViewById<View>(R.id.buttonProfile).setOnClickListener { onOpenProfile() }
        notificationsButton.setOnClickListener { open(NotificationsActivity::class.java) }
        page.findViewById<View>(R.id.cardSummary).setOnClickListener { onOpenTasks() }
        page.findViewById<View>(R.id.cardStatDue).setOnClickListener { onOpenTasks() }
        page.findViewById<View>(R.id.buttonSeeAllTasks).setOnClickListener { onOpenTasks() }
        page.findViewById<View>(R.id.cardStatSpend).setOnClickListener { onOpenExpenses() }
        page.findViewById<View>(R.id.cardStatPets).setOnClickListener { open(AddEditPetActivity::class.java) }
        page.findViewById<View>(R.id.buttonAddPetInline).setOnClickListener { open(AddEditPetActivity::class.java) }
        petsEmpty.setOnClickListener { open(AddEditPetActivity::class.java) }

        buildQuickActions()
        page.findViewById<TextView>(R.id.textCareTip).text = tipOfTheDay()
    }

    /** Re-binds session-backed UI immediately and reloads database content in the background. */
    fun refresh() {
        bindHeader()
        bindEmergencyContact()
        notificationsButton.visibility = if (sessionManager.areNotificationsEnabled()) View.VISIBLE else View.GONE

        if (executor.isShutdown) return
        executor.execute {
            val data = loadData()
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) render(data)
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    // region Loading

    private fun loadData(): HomeData {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val monthSpend = database.getExpenses()
            .filter { parseExpenseDate(it.date)?.let { d -> !d.before(monthStart) } ?: false }
            .sumOf { it.amount }
        return HomeData(database.getAllPets(), database.getCareTasks(), monthSpend)
    }

    // endregion

    // region Rendering

    private fun render(data: HomeData) {
        val total = data.tasks.size
        val done = data.tasks.count { it.isCompleted }
        val nowMinutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val pending = data.tasks
            .filter { !it.isCompleted }
            .map { Pending(it, careTimeMinutes(it.scheduledTime)) }
            .sortedBy { it.minutes ?: Int.MAX_VALUE }
        val overdue = pending.count { it.minutes != null && it.minutes < nowMinutes }

        // Today card
        val percent = if (total > 0) (done * 100f / total).roundToInt() else 0
        todayRing.setProgressCompat(percent, true)
        todayPercent.text = "$percent%"
        todayProgress.text = if (total == 0) "No routines yet" else "$done of $total done"
        todaySummary.text = when {
            data.pets.isEmpty() -> "Add a pet to start tracking care."
            total == 0 -> "Add a care routine to plan the day."
            pending.isEmpty() -> "All routines complete. Great work!"
            else -> {
                val next = pending.first()
                val prefix = if (next.minutes != null && next.minutes < nowMinutes) "Overdue" else "Next"
                "$prefix: ${next.task.titleOrDefault()} · ${next.task.petName}${next.task.scheduledTime.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()}"
            }
        }

        // Stats
        statPets.text = data.pets.size.toString()
        statPetsLabel.text = if (data.pets.size == 1) "Pet" else "Pets"
        statDue.text = pending.size.toString()
        statDueLabel.text = if (overdue > 0) "$overdue overdue" else "Due today"
        statDueLabel.setTextColor(color(if (overdue > 0) R.color.app_accent_red else R.color.app_text_secondary))
        statSpend.text = formatMoney(data.monthSpend)
        notificationBadge.visibility = if (pending.isNotEmpty()) View.VISIBLE else View.GONE

        renderUpNext(pending, nowMinutes, total)

        petAdapter.submit(data.pets)
        petsEmpty.visibility = if (data.pets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderUpNext(pending: List<Pending>, nowMinutes: Int, total: Int) {
        upNextList.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        pending.take(UP_NEXT_LIMIT).forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_home_up_next, upNextList, false)
            val isOverdue = item.minutes != null && item.minutes < nowMinutes
            row.findViewById<TextView>(R.id.upNextTime).apply {
                text = item.task.scheduledTime.ifBlank { "Anytime" }
                backgroundTintList = ContextCompat.getColorStateList(
                    activity, if (isOverdue) R.color.md_error_container else R.color.md_surface_container_high
                )
                setTextColor(color(if (isOverdue) R.color.md_on_error_container else R.color.app_text_primary))
            }
            row.findViewById<TextView>(R.id.upNextTitle).text = item.task.titleOrDefault()
            row.findViewById<TextView>(R.id.upNextMeta).text = listOf(
                item.task.petName,
                item.task.category,
                if (isOverdue) "Overdue" else ""
            ).filter { it.isNotBlank() }.joinToString(" · ")
            row.setOnClickListener { openChecklist(item.task) }
            if (index > 0) upNextList.addView(divider())
            upNextList.addView(row)
        }

        upNextEmpty.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        upNextList.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        upNextEmpty.text = if (total == 0) "No routines scheduled for today." else "You're all caught up for today."
    }

    private fun bindHeader() {
        val name = sessionManager.getUserName().orEmpty().trim().ifBlank { "Pet lover" }
        userName.text = name

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val salutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Calendar.getInstance().time)
        greeting.text = "$salutation · $date"

        // Profile photo if set, otherwise the user's initials.
        initials.text = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
        val uri = sessionManager.getProfileImageUri()
        avatar.setImageDrawable(null)
        if (!uri.isNullOrBlank()) {
            try {
                avatar.setImageURI(Uri.parse(uri))
            } catch (_: Exception) {
                avatar.setImageDrawable(null)
            }
        }
        val hasPhoto = avatar.drawable != null
        avatar.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        initials.visibility = if (hasPhoto) View.GONE else View.VISIBLE
    }

    private fun bindEmergencyContact() {
        val contact = sessionManager.getDefaultDelegateContact()
        if (contact.isNotBlank()) {
            emergencyText.text = contact
            emergencyAction.text = "Call"
            emergencyCard.setOnClickListener {
                try {
                    activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact")))
                } catch (_: Exception) {
                    Toast.makeText(activity, "Unable to open the dialer", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            emergencyText.text = "Not set up yet"
            emergencyAction.text = "Set up"
            emergencyCard.setOnClickListener { open(PersonalInfoActivity::class.java) }
        }
    }

    private fun buildQuickActions() {
        val container = page.findViewById<LinearLayout>(R.id.layoutQuickActions)
        container.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        listOf(
            QuickAction("Add pet", R.drawable.ic_paw, R.color.cat_feeding_bg, R.color.cat_feeding_fg, AddEditPetActivity::class.java),
            QuickAction("Add task", R.drawable.ic_nav_tasks, R.color.cat_exercise_bg, R.color.cat_exercise_fg, AddTaskActivity::class.java),
            QuickAction("Expense", R.drawable.ic_nav_expenses, R.color.cat_grooming_bg, R.color.cat_grooming_fg, AddExpenseActivity::class.java),
            QuickAction("Delegate", R.drawable.ic_person, R.color.cat_medication_bg, R.color.cat_medication_fg, DelegateContactActivity::class.java),
            QuickAction("Places", R.drawable.ic_location_pin, R.color.cat_healthcare_bg, R.color.cat_healthcare_fg, LocationsActivity::class.java)
        ).forEach { action ->
            val item = inflater.inflate(R.layout.item_quick_action, container, false)
            item.findViewById<FrameLayout>(R.id.quickActionIconBg).backgroundTintList =
                ContextCompat.getColorStateList(activity, action.bg)
            item.findViewById<ImageView>(R.id.quickActionIcon).apply {
                setImageResource(action.icon)
                imageTintList = ContextCompat.getColorStateList(activity, action.fg)
            }
            item.findViewById<TextView>(R.id.quickActionLabel).text = action.label
            item.contentDescription = action.label
            item.setOnClickListener { open(action.target) }
            container.addView(item)
        }
    }

    private data class QuickAction(val label: String, val icon: Int, val bg: Int, val fg: Int, val target: Class<*>)

    // endregion

    // region Helpers

    private fun tipOfTheDay(): String {
        val tips = listOf(
            "Regular grooming helps prevent skin issues and keeps your pet's coat healthy.",
            "A daily walk improves your dog's mental health and physical fitness.",
            "Interactive toys help reduce anxiety and boredom.",
            "Give your pet a comfortable, quiet place to sleep and rest.",
            "Fresh water should be available all day. Clean the bowl daily.",
            "Check for ticks and fleas after every outdoor walk.",
            "Treats are great, but keep an eye on your pet's daily calorie intake.",
            "Positive reinforcement is the most effective way to train your pet."
        )
        // Same tip all day, a new one tomorrow.
        return tips[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % tips.size]
    }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(color(R.color.md_outline_variant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = (14 * activity.resources.displayMetrics.density).roundToInt()
            marginEnd = marginStart
        }
    }

    private fun openChecklist(task: CareTask) {
        activity.startActivity(
            Intent(activity, ChecklistActivity::class.java)
                .putExtra(ChecklistActivity.EXTRA_PET_ID, task.petId)
                .putExtra(ChecklistActivity.EXTRA_PET_NAME, task.petName)
        )
    }

    private fun open(target: Class<*>) = activity.startActivity(Intent(activity, target))

    private fun color(res: Int): Int = ContextCompat.getColor(activity, res)

    private fun CareTask.titleOrDefault(): String = description.ifBlank { "Care task" }

    /** Parses stored times like "08:30 AM" into minutes after midnight. */
    private fun careTimeMinutes(time: String): Int? {
        if (time.isBlank()) return null
        return try {
            val parsed = SimpleDateFormat("hh:mm a", Locale.US).parse(time.trim()) ?: return null
            Calendar.getInstance().apply { this.time = parsed }
                .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        } catch (_: Exception) {
            null
        }
    }

    // endregion

    companion object {
        private const val UP_NEXT_LIMIT = 3
    }
}
