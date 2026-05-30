package com.openwhoop

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutDisplayTests {
    @Test
    fun caloriesTextUsesKcalWhenAvailable() {
        assertEquals("351 kcal", WorkoutDisplay.caloriesText(350.6, null))
    }

    @Test
    fun caloriesTextConvertsKilojoulesWhenKcalIsMissing() {
        assertEquals("250 kcal", WorkoutDisplay.caloriesText(null, 1046.0))
    }

    @Test
    fun caloriesTextExplainsMissingProfileWhenBothValuesAreMissing() {
        assertEquals("Profile needed", WorkoutDisplay.caloriesText(null, null))
    }
}
