package com.skypass.skytrack.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.notifications.NotificationPreferences
import com.skypass.skytrack.data.notifications.PushNotifier
import com.skypass.skytrack.data.repository.AttendanceRepository
import com.skypass.skytrack.data.repository.LeaveRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs when a reminder alarm goes off, and after a reboot, an app update or a clock change
 * (Android clears alarms then). Checks with the server that the reminder is still needed; when the
 * server can't be reached the reminder is shown anyway, since a missed check-in costs more than a
 * spare notification.
 */
open class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun sessionDataStore(): SessionDataStore
        fun notificationPreferences(): NotificationPreferences
        fun reminderScheduler(): ReminderScheduler
        fun attendanceRepository(): AttendanceRepository
        fun leaveRepository(): LeaveRepository
        fun attendanceDao(): AttendanceDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val deps = EntryPointAccessors.fromApplication(appContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Android allows about 10 seconds here
                withTimeoutOrNull(9_000) { handle(appContext, intent, deps) }
            } catch (e: Exception) {
                Log.w(TAG, "Reminder failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, deps: Dependencies) {
        val session = deps.sessionDataStore().session.first()
        val scheduler = deps.reminderScheduler()
        if (session == null) {
            scheduler.cancelAll()
            return
        }

        when (intent.action) {
            ReminderScheduler.ACTION_CHECK_IN -> {
                val shiftDate = intent.getStringExtra(ReminderScheduler.EXTRA_SHIFT_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now(ReminderRules.OFFICE_ZONE)
                // Book the next working day's reminder before anything can fail. Counted from this
                // reminder's own time, so one delivered a little early is not booked again for today.
                val thisReminder = ReminderRules.shiftStart(session.workShiftName)
                    ?.let { ReminderRules.checkInReminderFor(shiftDate, it) }
                val bookFrom = listOfNotNull(Instant.now(), thisReminder).maxOrNull()!!.plusSeconds(60)
                scheduler.scheduleCheckIn(session.workShiftName, bookFrom)
                if (!deps.notificationPreferences().isEnabled() || !PushNotifier.canShow(context)) return
                val token = session.token
                val attendance = CoroutineScope(Dispatchers.IO).async {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { deps.attendanceRepository().getToday(token).getOrNull() }
                }
                val holidays = CoroutineScope(Dispatchers.IO).async {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { deps.leaveRepository().publicHolidays(token).getOrNull() }
                        .orEmpty().map { it.date }
                }
                val leaves = CoroutineScope(Dispatchers.IO).async {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { deps.leaveRepository().history(token).getOrNull() }.orEmpty()
                }
                val remind = ReminderRules.shouldRemindCheckIn(
                    shiftDate = shiftDate,
                    attendance = attendance.await(),
                    holidays = holidays.await(),
                    leaves = leaves.await(),
                    joiningDate = session.joiningDate
                )
                if (!remind) return

                val start = ReminderRules.shiftStart(session.workShiftName) ?: return
                val startText = start.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                PushNotifier.show(
                    context,
                    id = "checkin-$shiftDate",
                    title = "Time to check in",
                    body = "Your shift starts at $startText. Remember to check in on SkyTrack.",
                    screen = "home"
                )
            }

            ReminderScheduler.ACTION_BREAK -> {
                val breakStartedAt = intent.getStringExtra(ReminderScheduler.EXTRA_BREAK_STARTED_AT) ?: return
                if (!deps.notificationPreferences().isEnabled() || !PushNotifier.canShow(context)) return
                val latest = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    deps.attendanceRepository().getToday(session.token).getOrNull()
                }
                // Resumed or checked out in the meantime (e.g. from another phone): nothing to say
                if (latest != null && !ReminderRules.isStillOnBreak(latest, breakStartedAt)) return
                PushNotifier.show(
                    context,
                    id = "break-$breakStartedAt",
                    title = "Your break is over 30 minutes",
                    body = "You have been on break for ${ReminderRules.BREAK_LIMIT_MINUTES} minutes. " +
                        "Tap End Break in SkyTrack when you are back.",
                    screen = "home"
                )
            }

            // Alarms are lost on reboot and app update, and a clock change can move them
            in RESTORE_ACTIONS -> {
                scheduler.scheduleCheckIn(session.workShiftName)
                val today = LocalDate.now().toString()
                scheduler.syncBreak(deps.attendanceDao().getAttendance(today)?.toDto())
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val NETWORK_TIMEOUT_MS = 5_000L
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}

/**
 * Receives only the system broadcasts that clear or move alarms, and books the reminders again.
 * Kept apart from [ReminderReceiver] (not exported), so other apps cannot trigger a reminder.
 */
class ReminderRestoreReceiver : ReminderReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESTORE_ACTIONS) super.onReceive(context, intent)
    }
}
