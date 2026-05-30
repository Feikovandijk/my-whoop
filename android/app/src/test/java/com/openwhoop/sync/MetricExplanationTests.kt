package com.openwhoop.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricExplanationTests {
    @Test
    fun parsesStrainExplanationAndQualityCounts() {
        val json = """
            {
              "device_id": "my-whoop",
              "date": "2025-10-30",
              "data_quality": {
                "stream_counts": {"hr": 12345, "rr": 800, "resp": 100},
                "sleep_sessions": 1,
                "exercise_sessions": 1
              },
              "metrics": {
                "strain": {
                  "value": 12.4,
                  "algorithm": "hrr_edwards_trimp_log21",
                  "status": "approx",
                  "inputs": {"hr_samples": 12345, "exercise_sessions": 1},
                  "limitation": "Cardiovascular only. Uses HRR-based Edwards TRIMP."
                }
              }
            }
        """.trimIndent()

        val explanation = MetricExplanationParser.fromJson(json)

        assertEquals("my-whoop", explanation.deviceId)
        assertEquals("2025-10-30", explanation.date)
        assertEquals(12345, explanation.dataQuality.streamCounts["hr"])
        assertEquals("hrr_edwards_trimp_log21", explanation.metrics["strain"]?.algorithm)
        assertEquals("approx", explanation.metrics["strain"]?.status)
        assertEquals(12.4, explanation.metrics["strain"]?.value as Double, 0.001)
        assertTrue(explanation.metrics["strain"]?.limitation?.contains("Cardiovascular only") == true)
    }
}
