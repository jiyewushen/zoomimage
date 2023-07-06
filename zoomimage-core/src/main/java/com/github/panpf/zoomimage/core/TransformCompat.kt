package com.github.panpf.zoomimage.core

data class TransformCompat(
    val scale: ScaleFactorCompat,
    val offset: OffsetCompat,
    val rotation: Float = 0f
) {

    companion object {
        val Origin = TransformCompat(
            scale = ScaleFactorCompat(1f, 1f),
            offset = OffsetCompat.Zero,
            rotation = 0f
        )
    }

    override fun toString(): String {
        return "Transform(scale=${scale.toShortString()}, offset=${offset.toShortString()}, rotation=$rotation)"
    }
}

fun TransformCompat.toShortString(): String =
    "(${scale.toShortString()},${offset.toShortString()},$rotation)"

fun TransformCompat.times(scaleFactor: ScaleFactorCompat): TransformCompat {
    return this.copy(
        scale = ScaleFactorCompat(
            scaleX = scale.scaleX * scaleFactor.scaleX,
            scaleY = scale.scaleY * scaleFactor.scaleY,
        ),
        offset = OffsetCompat(
            x = offset.x * scaleFactor.scaleX,
            y = offset.y * scaleFactor.scaleY,
        ),
    )
}

fun TransformCompat.div(scaleFactor: ScaleFactorCompat): TransformCompat {
    return this.copy(
        scale = ScaleFactorCompat(
            scaleX = scale.scaleX / scaleFactor.scaleX,
            scaleY = scale.scaleY / scaleFactor.scaleY,
        ),
        offset = OffsetCompat(
            x = offset.x / scaleFactor.scaleX,
            y = offset.y / scaleFactor.scaleY,
        ),
    )
}

fun TransformCompat.concat(other: TransformCompat): TransformCompat {
    return TransformCompat(
        scale = scale.times(other.scale),
        offset = offset + other.offset,
        rotation = rotation + other.rotation,
    )
}