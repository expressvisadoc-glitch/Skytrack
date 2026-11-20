package com.skypass.skytrack.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.skypass.skytrack.data.remote.AttendanceDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the check-in and break reminders on the phone with AlarmManager. Nothing is sent from
 * the server. [ReminderReceiver] decides when an alarm goes off whether the reminder is still needed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /** Next reminder before [workShiftName]'s start; cleared when the shift has no start time. */
    fun scheduleCheckIn(workShiftName: String?, now: Instant = Instant.now()) {
        val start = ReminderRules.shiftStart(workShiftName)
        if (start == null) {
            cancel(ACTION_CHECK_IN)
            return
        }
        val (shiftDate, remindAt) = ReminderRules.nextCheckInReminder(now, start)
        // If it can't be exact, better a few minutes early than after the shift has started
        set(remindAt, pendingIntent(ACTION_CHECK_IN) { putExtra(EXTRA_SHIFT_DATE, shiftDate.toString()) }, early = true)
    }

    /** Keeps the break reminder in step with the latest attendance: set while on a break, cleared otherwise. */
    fun syncBreak(attendance: AttendanceDto?) {
        val breakStartedAt = attendance?.takeIf { it.logout.isNullOrBlank() }?.breakStartedAt
        scheduleBreak(breakStartedAt)
    }

    fun scheduleBreak(breakStartedAt: String?) {
        val remindAt = ReminderRules.breakReminderAt(breakStartedAt)
        if (breakStartedAt == null || remindAt == null || !remindAt.isAfter(Instant.now())) {
            cancelBreak()
            return
        }
        set(remindAt, pendingIntent(ACTION_BREAK) { putExtra(EXTRA_BREAK_STARTED_AT, breakStartedAt) }, early = false)
    }

    fun cancelBreak() = cancel(ACTION_BREAK)

    /** On sign-out: nothing should remind the previous employee */
    fun cancelAll() {
        cancel(ACTION_CHECK_IN)
        cancel(ACTION_BREAK)
    }

    private fun set(at: Instant, intent: PendingIntent, early: Boolean) {
        val manager = alarmManager ?: return
        val millis = at.toEpochMilli()
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canBeExact) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
                return
            } catch (_: SecurityException) {
                // Exact alarm access withdrawn: fall through
            }
        }
        // Inexact alarms may otherwise arrive up to an hour late; keep it within a 10-minute window
        val window = INEXACT_WINDOW_MS
        manager.setWindow(AlarmManager.RTC_WAKEUP, if (early) millis - window else millis, window, intent)
    }

    private fun cancel(action: String) {
        alarmManager?.cancel(pendingIntent(action) {})
    }

    private fun pendingIntent(action: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(
            context,
            if (action == ACTION_CHECK_IN) REQUEST_CHECK_IN else REQUEST_BREAK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_CHECK_IN = "com.skypass.skytrack.reminder.CHECK_IN"
        const val ACTION_BREAK = "com.skypass.skytrack.reminder.BREAK"
        const val EXTRA_SHIFT_DATE = "shift_date"
        const val EXTRA_BREAK_STARTED_AT = "break_started_at"
        private const val REQUEST_CHECK_IN = 7101
        private const val REQUEST_BREAK = 7102
        private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L
    }
}
