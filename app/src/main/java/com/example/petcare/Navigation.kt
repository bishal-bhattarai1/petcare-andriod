package com.example.petcare

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.openMainTab(target: Class<out AppCompatActivity>) {
    if (javaClass == DashboardActivity::class.java && target == DashboardActivity::class.java) return

    val startTab = when (target) {
        DashboardActivity::class.java -> DashboardActivity.START_TAB_HOME
        TasksActivity::class.java -> DashboardActivity.START_TAB_TASKS
        ExpensesActivity::class.java -> DashboardActivity.START_TAB_EXPENSES
        ProfileActivity::class.java -> DashboardActivity.START_TAB_PROFILE
        else -> DashboardActivity.START_TAB_HOME
    }

    startActivity(
        Intent(this, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_START_TAB, startTab)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
