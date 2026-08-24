package com.aistudio.pocketpad.filter

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/**
 * 1€ (One Euro) Adaptive Filter
 * Reference: Casiez, Roussel, Vogel (CHI 2012)
 * Eliminates jitter when static while providing near-zero lag during rapid gestures.
 */
class LowPassFilter(private var alpha: Double = 1.0, initVal: Double = 0.0) {
    var y: Double = initVal
        private set
    private var initialized: Boolean = false

    fun filter(value: Double, a: Double): Double {
        alpha = a
        if (!initialized) {
            y = value
            initialized = true
            return value
        }
        y = (a * value) + ((1.0 - a) * y)
        return y
    }

    fun reset() {
        initialized = false
        y = 0.0
    }
}

class OneEuroFilter(
    private val minCutoff: Double = 0.85,
    private val beta: Double = 0.015,
    private val dCutoff: Double = 1.0
) {
    private val xFilter = LowPassFilter()
    private val dxFilter = LowPassFilter()
    private var lastTimeMs: Long? = null

    private fun calcAlpha(rate: Double, cutoff: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        val te = 1.0 / rate
        return 1.0 / (1.0 + tau / te)
    }

    fun filter(value: Double, timestampMs: Long): Double {
        val last = lastTimeMs
        if (last == null) {
            lastTimeMs = timestampMs
            return xFilter.filter(value, 1.0)
        }

        val dt = max(0.0005, (timestampMs - last) / 1000.0)
        lastTimeMs = timestampMs
        val rate = 1.0 / dt

        val dx = (value - xFilter.y) / dt
        val edx = dxFilter.filter(dx, calcAlpha(rate, dCutoff))
        val cutoff = minCutoff + (beta * abs(edx))
        return xFilter.filter(value, calcAlpha(rate, cutoff))
    }

    fun reset() {
        xFilter.reset()
        dxFilter.reset()
        lastTimeMs = null
    }
}
