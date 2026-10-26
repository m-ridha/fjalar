package com.muhammadridha.fjalar.adapters.scheduler

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AndroidAlarmManagerSchedulerAdapterTest {

    private class FakeAlarmManagerGateway(
        var exactAlarmsAllowed: Boolean = true,
    ) : AlarmManagerGateway {

        data class ScheduleCall(
            val requestCode: Int,
            val triggerAtMillis: Long,
            val action: String,
            val alarmId: String,
            val exact: Boolean,
        )

        data class CancelCall(
            val requestCode: Int,
            val action: String,
            val alarmId: String,
        )

        val scheduledCalls = mutableListOf<ScheduleCall>()
        val cancelledCalls = mutableListOf<CancelCall>()

        override fun canScheduleExactAlarms(): Boolean = exactAlarmsAllowed

        override fun schedule(
            requestCode: Int,
            triggerAtMillis: Long,
            action: String,
            alarmId: String,
            exact: Boolean,
        ) {
            scheduledCalls.add(
                ScheduleCall(
                    requestCode = requestCode,
                    triggerAtMillis = triggerAtMillis,
                    action = action,
                    alarmId = alarmId,
                    exact = exact,
                )
            )
        }

        override fun cancel(
            requestCode: Int,
            action: String,
            alarmId: String,
        ) {
            cancelledCalls.add(
                CancelCall(
                    requestCode = requestCode,
                    action = action,
                    alarmId = alarmId,
                )
            )
            scheduledCalls.removeAll { it.requestCode == requestCode && it.alarmId == alarmId }
        }
    }

    @Test
    fun scheduleWakeup_setsExactAlarmWithCorrectTimeAndIntent_whenExactAllowed() = runTest {
        val fakeGateway = FakeAlarmManagerGateway(exactAlarmsAllowed = true)
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 34,
            gateway = fakeGateway,
        )

        val targetInstant = Instant.parse("2026-10-26T07:15:00Z")
        val alarmId = "alarm-manggarai-1"

        adapter.scheduleWakeup(alarmId, targetInstant)

        assertEquals(1, fakeGateway.scheduledCalls.size)
        val call = fakeGateway.scheduledCalls.first()
        assertEquals(alarmId.hashCode(), call.requestCode)
        assertEquals(targetInstant.toEpochMilliseconds(), call.triggerAtMillis)
        assertEquals(AndroidAlarmManagerSchedulerAdapter.ACTION_COMMUTE_WINDOW, call.action)
        assertEquals(alarmId, call.alarmId)
        assertTrue(call.exact, "Alarm should be scheduled with exact timing when allowed")
    }

    @Test
    fun scheduleWakeup_fallsBackDefensivelyToInexact_whenExactNotAllowedOnApi31Plus() = runTest {
        val fakeGateway = FakeAlarmManagerGateway(exactAlarmsAllowed = false)
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 31,
            gateway = fakeGateway,
        )

        val targetInstant = Instant.parse("2026-10-26T08:00:00Z")
        val alarmId = "alarm-sudirman-2"

        adapter.scheduleWakeup(alarmId, targetInstant)

        assertEquals(1, fakeGateway.scheduledCalls.size)
        val call = fakeGateway.scheduledCalls.first()
        assertEquals(alarmId.hashCode(), call.requestCode)
        assertEquals(targetInstant.toEpochMilliseconds(), call.triggerAtMillis)
        assertEquals(AndroidAlarmManagerSchedulerAdapter.ACTION_COMMUTE_WINDOW, call.action)
        assertEquals(alarmId, call.alarmId)
        assertFalse(call.exact, "Alarm should fall back to inexact when exact alarms are not allowed on API 31+")
    }

    @Test
    fun scheduleWakeup_usesExactAlarmOnApi30_regardlessOfCanScheduleExactAlarms() = runTest {
        val fakeGateway = FakeAlarmManagerGateway(exactAlarmsAllowed = false)
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 30, // Android 11: SCHEDULE_EXACT_ALARM permission check not enforced by OS
            gateway = fakeGateway,
        )

        val targetInstant = Instant.parse("2026-10-26T09:30:00Z")
        val alarmId = "alarm-legacy-3"

        adapter.scheduleWakeup(alarmId, targetInstant)

        assertEquals(1, fakeGateway.scheduledCalls.size)
        val call = fakeGateway.scheduledCalls.first()
        assertTrue(call.exact, "API <= 30 should schedule exact alarm without permission check")
    }

    @Test
    fun cancelWakeup_cancelsMatchingPendingAlarm() = runTest {
        val fakeGateway = FakeAlarmManagerGateway()
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 34,
            gateway = fakeGateway,
        )

        val alarmId = "alarm-to-cancel"
        adapter.scheduleWakeup(alarmId, Instant.parse("2026-10-26T10:00:00Z"))
        assertEquals(1, fakeGateway.scheduledCalls.size)

        adapter.cancelWakeup(alarmId)

        assertEquals(1, fakeGateway.cancelledCalls.size)
        val cancelCall = fakeGateway.cancelledCalls.first()
        assertEquals(alarmId.hashCode(), cancelCall.requestCode)
        assertEquals(AndroidAlarmManagerSchedulerAdapter.ACTION_COMMUTE_WINDOW, cancelCall.action)
        assertEquals(alarmId, cancelCall.alarmId)
        assertTrue(fakeGateway.scheduledCalls.isEmpty(), "Scheduled alarm should be removed upon cancellation")
    }

    @Test
    fun multipleAlarms_maintainIsolatedRequestCodes() = runTest {
        val fakeGateway = FakeAlarmManagerGateway()
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 34,
            gateway = fakeGateway,
        )

        val idA = "commute-morning"
        val idB = "commute-evening"
        val instantA = Instant.parse("2026-10-26T07:00:00Z")
        val instantB = Instant.parse("2026-10-26T17:30:00Z")

        adapter.scheduleWakeup(idA, instantA)
        adapter.scheduleWakeup(idB, instantB)

        assertEquals(2, fakeGateway.scheduledCalls.size)
        assertEquals(idA.hashCode(), fakeGateway.scheduledCalls[0].requestCode)
        assertEquals(idB.hashCode(), fakeGateway.scheduledCalls[1].requestCode)

        // Cancel only alarm A
        adapter.cancelWakeup(idA)

        assertEquals(1, fakeGateway.scheduledCalls.size)
        assertEquals(idB.hashCode(), fakeGateway.scheduledCalls[0].requestCode)
    }

    @Test
    fun collidingHashCodes_distinguishPendingIntentsByAlarmId() = runTest {
        val fakeGateway = FakeAlarmManagerGateway()
        val adapter = AndroidAlarmManagerSchedulerAdapter(
            apiLevel = 34,
            gateway = fakeGateway,
        )

        val id1 = "alarm-id-alpha"
        val id2 = "alarm-id-beta"
        val instant = Instant.parse("2026-10-26T08:00:00Z")

        adapter.scheduleWakeup(id1, instant)
        adapter.scheduleWakeup(id2, instant)

        assertEquals(2, fakeGateway.scheduledCalls.size)

        // Cancel only id1
        adapter.cancelWakeup(id1)

        assertEquals(1, fakeGateway.scheduledCalls.size)
        assertEquals(id2, fakeGateway.scheduledCalls.first().alarmId)
    }
}
