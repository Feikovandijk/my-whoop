package com.openwhoop.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricExplanationCopyTests {
    @Test
    fun describesStrainInputsAndLimitation() {
        val metric = ExplainedMetric(
            value = 12.4,
            algorithm = "hrr_edwards_trimp_log21",
            status = "approx",
            inputs = mapOf("hr_samples" to 12345.0, "exercise_sessions" to 1.0),
            limitation = "Cardiovascular only. Uses HRR-based Edwards TRIMP."
        )

        val lines = MetricExplanationCopy.lines("strain", metric)

        assertEquals("Status: approx", lines[0])
        assertEquals("Method: HRR + Edwards TRIMP + logarithmic 0-21 scale", lines[1])
        assertEquals("Inputs: 12,345 HR samples, 1 exercise session", lines[2])
        assertEquals("Limit: Cardiovascular only. Uses HRR-based Edwards TRIMP.", lines[3])
    }
}
