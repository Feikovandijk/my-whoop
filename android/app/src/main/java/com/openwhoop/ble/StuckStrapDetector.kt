package com.openwhoop.ble

class StuckStrapDetector(
    val stuckAfterSeconds: Double,
    val behindGapSeconds: Int = 300
) {
    private var lastFrontierTs: Long? = null
    private var lastAdvanceWall: Double? = null

    fun observe(strapNewestTs: Long?, ourFrontierTs: Long?, now: Double): Boolean {
        if (strapNewestTs == null || ourFrontierTs == null) return false
        val last = lastFrontierTs
        if (last == null) {
            lastFrontierTs = ourFrontierTs
            lastAdvanceWall = now
            return false
        }
        if (ourFrontierTs > last) {
            lastFrontierTs = ourFrontierTs
            lastAdvanceWall = now
            return false
        }
        val behind = (strapNewestTs - ourFrontierTs) > behindGapSeconds
        if (!behind) {
            lastAdvanceWall = now
            return false
        }
        val lastAdvance = lastAdvanceWall ?: now
        return (now - lastAdvance) >= stuckAfterSeconds
    }
}
