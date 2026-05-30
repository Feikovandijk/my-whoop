package com.openwhoop.sync

import java.text.NumberFormat
import java.util.Locale

object MetricExplanationCopy {
    fun lines(metricKey: String, metric: ExplainedMetric): List<String> {
        val method = when (metric.algorithm) {
            "hrr_edwards_trimp_log21" -> "HRR + Edwards TRIMP + logarithmic 0-21 scale"
            "personal_baseline_hrv_rhr_resp_sleep_logistic" -> "Personal HRV, resting HR, respiration, and sleep baseline"
            "sleep_detection_motion_hr_rr" -> "Wearable sleep detection from HR, RR, and movement"
            "uncalibrated_type47_nightly_signals" -> "Type-47 nightly sensor conversion"
            else -> metric.algorithm.ifBlank { metricKey }
        }
        val out = mutableListOf(
            "Status: ${metric.status.ifBlank { "unknown" }}",
            "Method: $method"
        )
        inputsLine(metricKey, metric.inputs)?.let { out.add(it) }
        metric.limitation?.takeIf { it.isNotBlank() }?.let { out.add("Limit: $it") }
        return out
    }

    private fun inputsLine(metricKey: String, inputs: Map<String, Any>): String? {
        val parts = when (metricKey) {
            "strain" -> listOfNotNull(
                countPhrase(inputs["hr_samples"], "HR sample"),
                countPhrase(inputs["exercise_sessions"], "exercise session")
            )
            "recovery" -> listOfNotNull(
                countPhrase(inputs["rr_samples"], "RR interval"),
                countPhrase(inputs["resp_samples"], "resp sample"),
                countPhrase(inputs["sleep_sessions"], "sleep session")
            )
            "sleep" -> listOfNotNull(
                countPhrase(inputs["sleep_sessions"], "sleep session"),
                countPhrase(inputs["gravity_samples"], "movement sample"),
                countPhrase(inputs["hr_samples"], "HR sample")
            )
            else -> inputs.map { (key, value) -> "${cleanKey(key)} ${formatNumber(value)}" }
        }
        if (parts.isEmpty()) return null
        return "Inputs: ${parts.joinToString(", ")}"
    }

    private fun countPhrase(value: Any?, singular: String): String? {
        val n = numeric(value)?.toLong() ?: return null
        val label = if (n == 1L) singular else "${singular}s"
        return "${NumberFormat.getIntegerInstance(Locale.US).format(n)} $label"
    }

    private fun formatNumber(value: Any): String {
        val n = numeric(value)
        return if (n != null) {
            NumberFormat.getNumberInstance(Locale.US).format(n)
        } else {
            value.toString()
        }
    }

    private fun numeric(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun cleanKey(key: String): String {
        return key.replace("_", " ")
    }
}
