package com.openwhoop.sync

import com.openwhoop.ble.protocol.DailyMetric

object MetricExplanationFallback {
    fun fromDailyMetric(deviceId: String, metric: DailyMetric): MetricExplanation {
        val sleepSessions = if ((metric.totalSleepMin ?: 0.0) > 0.0) 1 else 0
        val exerciseSessions = metric.exerciseCount ?: 0

        return MetricExplanation(
            deviceId = deviceId,
            date = metric.day,
            dataQuality = ExplanationQuality(
                streamCounts = emptyMap(),
                sleepSessions = sleepSessions,
                exerciseSessions = exerciseSessions
            ),
            metrics = mapOf(
                "sleep" to ExplainedMetric(
                    value = metric.totalSleepMin,
                    algorithm = "sleep_detection_motion_hr_rr",
                    status = if (metric.totalSleepMin != null) "cached" else "pending",
                    inputs = mapOf(
                        "sleep_sessions" to sleepSessions.toDouble(),
                        "disturbances" to (metric.disturbances ?: 0).toDouble()
                    ),
                    limitation = "Cached daily summary. Sync with the server explanation endpoint for exact input counts."
                ),
                "recovery" to ExplainedMetric(
                    value = metric.recovery?.let { it * 100.0 },
                    algorithm = "personal_baseline_hrv_rhr_resp_sleep_logistic",
                    status = if (metric.recovery != null) "cached" else "pending",
                    inputs = mapOf(
                        "sleep_sessions" to sleepSessions.toDouble(),
                        "resting_hr" to (metric.restingHr ?: 0).toDouble(),
                        "avg_hrv" to (metric.avgHrv ?: 0.0)
                    ),
                    limitation = "Cached daily summary. Exact provenance appears after the server explanation endpoint is available."
                ),
                "strain" to ExplainedMetric(
                    value = metric.strain,
                    algorithm = "hrr_edwards_trimp_log21",
                    status = if (metric.strain != null) "cached" else "pending",
                    inputs = mapOf(
                        "exercise_sessions" to exerciseSessions.toDouble(),
                        "resting_hr" to (metric.restingHr ?: 0).toDouble()
                    ),
                    limitation = "Cardiovascular only. Uses HRR-based Edwards TRIMP and a logarithmic 0-21 scale; does not include WHOOP's muscular strain component."
                ),
                "nightly_biometrics" to ExplainedMetric(
                    value = mapOf(
                        "spo2_pct" to metric.spo2Pct,
                        "skin_temp_dev_c" to metric.skinTempDevC,
                        "resp_rate_bpm" to metric.respRateBpm
                    ),
                    algorithm = "uncalibrated_type47_nightly_signals",
                    status = "cached",
                    inputs = emptyMap(),
                    limitation = "Cached nightly signals. Exact input counts need the server explanation endpoint."
                )
            )
        )
    }
}
