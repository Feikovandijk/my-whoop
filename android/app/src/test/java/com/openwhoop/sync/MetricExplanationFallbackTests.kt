package com.openwhoop.sync

import com.openwhoop.ble.protocol.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricExplanationFallbackTests {
    @Test
    fun buildsUsefulStrainExplanationFromCachedDailyMetric() {
        val metric = DailyMetric(
            day = "2025-10-30",
            totalSleepMin = 430.0,
            efficiency = 0.91,
            deepMin = 82.0,
            remMin = 96.0,
            lightMin = 252.0,
            disturbances = 4,
            restingHr = 49,
            avgHrv = 71.2,
            recovery = 0.76,
            strain = 12.4,
            exerciseCount = 1,
            spo2Pct = 96.7,
            skinTempDevC = 0.2,
            respRateBpm = 15.8
        )

        val explanation = MetricExplanationFallback.fromDailyMetric("my-whoop", metric)

        assertEquals("my-whoop", explanation.deviceId)
        assertEquals("2025-10-30", explanation.date)
        assertEquals(12.4, explanation.metrics["strain"]?.value as Double, 0.001)
        assertEquals("cached", explanation.metrics["strain"]?.status)
        assertEquals("hrr_edwards_trimp_log21", explanation.metrics["strain"]?.algorithm)
        assertTrue(explanation.metrics["strain"]?.limitation?.contains("Cardiovascular only") == true)
        assertEquals(1.0, explanation.metrics["strain"]?.inputs?.get("exercise_sessions") as Double, 0.001)
    }
}
