package com.openwhoop

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepUiMetricsTests {
    @Test
    fun stageFractionReturnsZeroWhenTotalSleepIsZero() {
        assertEquals(0f, SleepUiMetrics.stageFraction(stageMinutes = 42.0, totalSleepMinutes = 0.0), 0.001f)
    }

    @Test
    fun stageFractionClampsValidValues() {
        assertEquals(1f, SleepUiMetrics.stageFraction(stageMinutes = 500.0, totalSleepMinutes = 250.0), 0.001f)
        assertEquals(0.5f, SleepUiMetrics.stageFraction(stageMinutes = 125.0, totalSleepMinutes = 250.0), 0.001f)
    }
}
