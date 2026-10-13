package com.ember.reader.ui.reader.epub

/** The speeds auto-scroll can be set to, in CSS pixels a second. */
internal object AutoScrollSpeed {

    /** Slowest to fastest; each is about a fifth faster than the one before. */
    val STEPS = listOf(4f, 5f, 6f, 8f, 10f, 12f, 15f, 18f, 22f, 26f, 32f, 40f, 50f)

    /** About a line every two seconds at the default text size. */
    const val DEFAULT = 12f

    /** The step next to [speed], or [speed]'s own step at either end of the range. */
    fun step(speed: Float, faster: Boolean): Float {
        val index = (level(speed) - 1) + if (faster) 1 else -1
        return STEPS[index.coerceIn(0, STEPS.lastIndex)]
    }

    /** [speed] as a number from 1 (slowest) up, taking the nearest step for a speed between two. */
    fun level(speed: Float): Int = STEPS.indices.minBy { kotlin.math.abs(STEPS[it] - speed) } + 1

    fun isSlowest(speed: Float): Boolean = level(speed) == 1

    fun isFastest(speed: Float): Boolean = level(speed) == STEPS.size
}
