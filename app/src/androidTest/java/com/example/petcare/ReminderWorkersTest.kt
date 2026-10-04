package com.example.petcare

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.petcare.data.PetCareDatabase
import com.example.petcare.data.PetEntity
import com.example.petcare.data.TaskEntity
import com.example.petcare.data.UserScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WorkManager side of the hybrid reminder design: the workers find and re-create missing exact
 * alarms, and the daily check is enqueued exactly once.
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkersTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = PetCareDatabase.getInstance(context)
    private var petId = -1L
    private var taskId = -1L

    @Before
    fun insertRoutineWithReminder() {
        // Owned by whoever is signed in on the device (or nobody), so the repositories can see it.
        val owner = UserScope(context).currentUserId()
        petId = db.petDao().insert(PetEntity(name = "Worker Test Pet", ownerId = owner))
        taskId = db.taskDao().insert(
            TaskEntity(petId = petId, description = "Worker test feed", repeatType = "Daily", scheduledTime = "08:00 AM", reminderEnabled = 1)
        )
    }

    @After
    fun cleanUp() {
        TaskReminder.cancel(context, taskId)
        db.petDao().deletePetCascade(petId)
    }

    @Test
    fun dailyCheck_recreatesAMissingAlarm() = runBlocking {
        // Simulate an alarm lost to a force-stop or battery saver.
        TaskReminder.cancel(context, taskId)
        assertFalse(TaskReminder.isScheduled(context, taskId))

        val result = TestListenableWorkerBuilder<ReminderCheckWorker>(context).build().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("the missing alarm is reported", result.outputData.getInt(KEY_MISSING, 0) >= 1)
        assertTrue("the alarm exists again", TaskReminder.isScheduled(context, taskId))
    }

    @Test
    fun dailyCheck_leavesExistingAlarmsInPlace() = runBlocking {
        TaskReminder.schedule(context, TaskRepositoryProbe.task(context, taskId))
        assertTrue(TaskReminder.isScheduled(context, taskId))

        val result = TestListenableWorkerBuilder<ReminderCheckWorker>(context).build().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(TaskReminder.isScheduled(context, taskId))
    }

    @Test
    fun rescheduleWorker_restoresAlarmsAfterBoot() = runBlocking {
        TaskReminder.cancel(context, taskId) // after a reboot no alarms exist

        val worker = TestListenableWorkerBuilder<RescheduleRemindersWorker>(context)
            .setInputData(workDataOf(KEY_REASON to "BOOT_COMPLETED"))
            .build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(TaskReminder.isScheduled(context, taskId))
    }

    @Test
    fun periodicCheck_isEnqueuedOnlyOnce() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ReminderWork.schedulePeriodicCheck(context)
        ReminderWork.schedulePeriodicCheck(context) // e.g. the app started twice

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderWork.PERIODIC_CHECK).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    /** Reads the routine back through the repository, the same way the app schedules it. */
    private object TaskRepositoryProbe {
        fun task(context: Context, id: Long): CareTask =
            com.example.petcare.data.TaskRepository(context).getCareTasks().single { it.id == id }
    }
}
