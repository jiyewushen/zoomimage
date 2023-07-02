package com.github.panpf.zoomimage.internal

data class Translation(
    val translationX: Float,
    val translationY: Float
) {

    operator fun times(operand: Float) = Translation(translationX * operand, translationY * operand)

    operator fun div(operand: Float) = Translation(translationX / operand, translationY / operand)

    override fun toString() =
        "Translation(${translationX.roundToTenths()}, ${translationY.roundToTenths()})"

    companion object {
        val Unspecified = Translation(translationX = Float.NaN, translationY = Float.NaN)

        val Empty = Translation(translationX = 0f, translationY = 0f)
    }
}

private fun Float.roundToTenths(): Float {
    val shifted = this * 10
    val decimal = shifted - shifted.toInt()
    // Kotlin's round operator rounds 0.5f down to 0. Manually compare against
    // 0.5f and round up if necessary
    val roundedShifted = if (decimal >= 0.5f) {
        shifted.toInt() + 1
    } else {
        shifted.toInt()
    }
    return roundedShifted.toFloat() / 10
}

/**
 * `false` when this is [Translation.Unspecified].
 */
inline val Translation.isSpecified: Boolean
    get() = !translationX.isNaN() && !translationY.isNaN()

/**
 * `true` when this is [Translation.Unspecified].
 */
inline val Translation.isUnspecified: Boolean
    get() = translationX.isNaN() || translationY.isNaN()

/**
 * If this [Translation] [isSpecified] then this is returned, otherwise [block] is executed
 * and its result is returned.
 */
inline fun Translation.takeOrElse(block: () -> Translation): Translation =
    if (isSpecified) this else block()

/**
 * Linearly interpolate between two [Translation] parameters
 *
 * The [fraction] argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning [start] (or something
 * equivalent to [start]), 1.0 meaning that the interpolation has finished,
 * returning [stop] (or something equivalent to [stop]), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid (and can
 * easily be generated by curves).
 *
 * Values for [fraction] are usually obtained from an [Animation<Float>], such as
 * an `AnimationController`.
 */
fun lerp(start: Translation, stop: Translation, fraction: Float): Translation {
    return Translation(
        lerp(start.translationX, stop.translationX, fraction),
        lerp(start.translationY, stop.translationY, fraction)
    )
}

/**
 * Linearly interpolate between [start] and [stop] with [fraction] fraction between them.
 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

fun Translation.toShortString(): String =
    "(${translationX.format(1)},${translationY.format(1)})"