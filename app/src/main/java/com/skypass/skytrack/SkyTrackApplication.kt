package com.skypass.skytrack

import android.app.Application
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.notifications.PushNotifier
import com.skypass.skytrack.data.reminders.ReminderScheduler
import com.skypass.skytrack.data.sync.OfflineSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class SkyTrackApplication : Application() {

    @Inject
    lateinit var offlineSyncManager: OfflineSyncManager

    @Inject
    lateinit var sessionDataStore: SessionDataStore

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        offlineSyncManager.startObserving()
        PushNotifier.createChannel(this)

        // Check-in reminder follows the signed-in employee and their shift; sign-out clears all reminders
        appScope.launch {
            sessionDataStore.session
                .map { session -> session?.let { it.employeeId to it.workShiftName } }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn == null) {
                        reminderScheduler.cancelAll()
                    } else {
                        reminderScheduler.scheduleCheckIn(signedIn.second)
                    }
                }
        }
    }
}
