package com.openwhoop

object SleepUiMetrics {
    fun stageFraction(stageMinutes: Double, totalSleepMinutes: Double): Float {
        if (!stageMinutes.isFinite() || !totalSleepMinutes.isFinite() || totalSleepMinutes <= 0.0) {
            return 0f
        }
        return (stageMinutes / totalSleepMinutes).toFloat().coerceIn(0f, 1f)
    }

    fun awakeFraction(awakeMinutes: Double, totalSleepMinutes: Double): Float {
        return stageFraction(awakeMinutes, totalSleepMinutes).coerceIn(0f, 0.2f)
    }
}
