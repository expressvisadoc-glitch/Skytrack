package com.skypass.skytrack

import com.skypass.skytrack.data.sync.MAX_SERVER_RETRIES
import com.skypass.skytrack.data.sync.Next
import com.skypass.skytrack.data.sync.classifyHttpFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRulesTest {

    @Test
    fun loginProblems_waitForSignIn_andKeepTheAction() {
        assertEquals(Next.SIGN_IN, classifyHttpFailure(401, null, null, 0))
        assertEquals(Next.SIGN_IN, classifyHttpFailure(400, "invalid_session", "Session is invalid", 0))
        assertEquals(Next.SIGN_IN, classifyHttpFailure(403, null, "Your session expired (session_expired)", 0))
    }

    @Test
    fun serverUnreachableOrBusy_isRetried() {
        for (status in listOf(408, 429, 502, 503, 504)) {
            assertEquals("HTTP $status", Next.RETRY, classifyHttpFailure(status, null, null, 50))
        }
    }

    @Test
    fun unexplainedServerError_isRetried_butNotForever() {
        assertEquals(Next.RETRY, classifyHttpFailure(500, null, null, 0))
        assertEquals(Next.RETRY, classifyHttpFailure(500, null, null, MAX_SERVER_RETRIES - 1))
        assertEquals(Next.GIVE_UP, classifyHttpFailure(500, null, null, MAX_SERVER_RETRIES))
    }

    @Test
    fun explainedRefusals_areDropped_soTheQueueKeepsMoving() {
        // Live server: start_work RPC refusal comes back as 500 with a reason
        assertEquals(Next.GIVE_UP, classifyHttpFailure(500, "start_work_error", "Work already started today", 0))
        assertEquals(Next.GIVE_UP, classifyHttpFailure(400, null, "No active break found", 0))
        assertEquals(Next.GIVE_UP, classifyHttpFailure(409, "action_date_passed", "Tapped on an earlier day", 0))
        assertEquals(Next.GIVE_UP, classifyHttpFailure(404, "attendance_not_found", null, 0))
    }
}
