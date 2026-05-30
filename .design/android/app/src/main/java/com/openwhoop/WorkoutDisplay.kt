package com.openwhoop

import kotlin.math.roundToInt

object WorkoutDisplay {
    fun caloriesText(caloriesKcal: Double?, caloriesKj: Double?): String {
        val kcal = caloriesKcal?.takeIf { it.isFinite() && it > 0.0 }
        if (kcal != null) return "${kcal.roundToInt()} kcal"

        val kj = caloriesKj?.takeIf { it.isFinite() && it > 0.0 }
        if (kj != null) return "${(kj / 4.184).roundToInt()} kcal"

        return "Profile needed"
    }
}
