/*
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UnnecessaryVariable")

package com.github.panpf.zoomimage.view.zoom.internal

import android.view.View
import android.widget.ImageView.ScaleType
import com.github.panpf.zoomimage.Logger
import com.github.panpf.zoomimage.ReadMode
import com.github.panpf.zoomimage.ScrollEdge
import com.github.panpf.zoomimage.util.AlignmentCompat
import com.github.panpf.zoomimage.util.ContentScaleCompat
import com.github.panpf.zoomimage.util.DefaultMediumScaleMinMultiple
import com.github.panpf.zoomimage.util.IntOffsetCompat
import com.github.panpf.zoomimage.util.IntRectCompat
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.util.OffsetCompat
import com.github.panpf.zoomimage.util.ScaleFactorCompat
import com.github.panpf.zoomimage.util.TransformCompat
import com.github.panpf.zoomimage.util.calculateNextStepScale
import com.github.panpf.zoomimage.util.canScrollByEdge
import com.github.panpf.zoomimage.util.center
import com.github.panpf.zoomimage.util.computeContainerVisibleRect
import com.github.panpf.zoomimage.util.computeContentBaseDisplayRect
import com.github.panpf.zoomimage.util.computeContentBaseVisibleRect
import com.github.panpf.zoomimage.util.computeContentDisplayRect
import com.github.panpf.zoomimage.util.computeContentVisibleRect
import com.github.panpf.zoomimage.util.computeLocationUserOffset
import com.github.panpf.zoomimage.util.computeScrollEdge
import com.github.panpf.zoomimage.util.computeTransformOffset
import com.github.panpf.zoomimage.util.containerPointToContentPoint
import com.github.panpf.zoomimage.util.contentPointToContainerPoint
import com.github.panpf.zoomimage.util.isNotEmpty
import com.github.panpf.zoomimage.util.lerp
import com.github.panpf.zoomimage.util.limitScaleWithRubberBand
import com.github.panpf.zoomimage.util.name
import com.github.panpf.zoomimage.util.plus
import com.github.panpf.zoomimage.util.round
import com.github.panpf.zoomimage.util.times
import com.github.panpf.zoomimage.util.toOffset
import com.github.panpf.zoomimage.util.toRect
import com.github.panpf.zoomimage.util.toShortString
import com.github.panpf.zoomimage.util.touchPointToContainerPoint
import com.github.panpf.zoomimage.view.internal.Rect
import com.github.panpf.zoomimage.view.internal.format
import com.github.panpf.zoomimage.view.internal.requiredMainThread
import com.github.panpf.zoomimage.view.internal.toAlignment
import com.github.panpf.zoomimage.view.internal.toContentScale
import com.github.panpf.zoomimage.view.zoom.OnDrawableSizeChangeListener
import com.github.panpf.zoomimage.view.zoom.OnMatrixChangeListener
import com.github.panpf.zoomimage.view.zoom.OnViewSizeChangeListener
import com.github.panpf.zoomimage.view.zoom.ZoomAnimationSpec
import kotlin.math.roundToInt

class ZoomEngine2 constructor(logger: Logger, val view: View) {

    private val logger: Logger = logger.newLogger(module = "ZoomEngine")
    private var lastScaleAnimatable: FloatAnimatable? = null
    private var lastFlingAnimatable: FlingAnimatable? = null
    private var lastTransformCentroid: OffsetCompat? = null
    private var rotation: Int = 0

    private var onMatrixChangeListeners: MutableSet<OnMatrixChangeListener>? = null
    private var onViewSizeChangeListeners: MutableSet<OnViewSizeChangeListener>? = null
    private var onDrawableSizeChangeListeners: MutableSet<OnDrawableSizeChangeListener>? = null

    var baseTransform = TransformCompat.Origin
        private set
    var userTransform = TransformCompat.Origin
        private set
    var transform = TransformCompat.Origin
        private set
    var scaling = false
        set(value) {
            if (field != value) {
                field = value
                notifyMatrixChanged()
            }
        }
    var fling = false
        set(value) {
            if (field != value) {
                field = value
                notifyMatrixChanged()
            }
        }

    var scrollEdge: ScrollEdge = ScrollEdge.Default
        private set

    var containerSize = IntSizeCompat.Zero
        internal set(value) {
            if (field != value) {
                field = value
                reset("containerSizeChanged")
                notifyViewSizeChanged()
            }
        }
    var contentSize = IntSizeCompat.Zero
        internal set(value) {
            if (field != value) {
                field = value
                reset("contentSizeChanged")
                notifyDrawableSizeChanged()
            }
        }
    var contentOriginSize = IntSizeCompat.Zero
        internal set(value) {
            if (field != value) {
                field = value
                reset("contentOriginSizeChanged")
            }
        }
    var contentScale: ContentScaleCompat = ContentScaleCompat.Fit
        internal set(value) {
            if (field != value) {
                field = value
                reset("contentScaleChanged")
            }
        }
    var contentAlignment: AlignmentCompat = AlignmentCompat.Center
        internal set(value) {
            if (field != value) {
                field = value
                reset("contentAlignmentChanged")
            }
        }
    var scaleType: ScaleType = ScaleType.FIT_CENTER
        internal set(value) {
            if (field != value) {
                field = value
                contentScale = value.toContentScale()
                contentAlignment = value.toAlignment()
            }
        }
    var readMode: ReadMode? = null
        internal set(value) {
            if (field != value) {
                field = value
                reset("readModeChanged")
            }
        }
    var mediumScaleMinMultiple: Float = DefaultMediumScaleMinMultiple
        internal set(value) {
            if (field != value) {
                field = value
                reset("mediumScaleMinMultipleChanged")
            }
        }
    var animationSpec: ZoomAnimationSpec = ZoomAnimationSpec.Default

    var threeStepScale: Boolean = false
        internal set
    var rubberBandScale: Boolean = true
        internal set
    var minScale: Float = 1.0f
        private set
    var mediumScale: Float = 1.0f
        private set
    var maxScale: Float = 1.0f
        private set
    var containerVisibleRect: IntRectCompat = IntRectCompat.Zero
        private set
    var contentBaseDisplayRect: IntRectCompat = IntRectCompat.Zero
        private set
    var contentBaseVisibleRect: IntRectCompat = IntRectCompat.Zero
        private set
    var contentDisplayRect: IntRectCompat = IntRectCompat.Zero
        private set
    var contentVisibleRect: IntRectCompat = IntRectCompat.Zero
        private set


    /**************************************** Internal ********************************************/

    init {
        reset("init")
    }

    private fun reset(caller: String) {
        requiredMainThread()
        stopAllAnimation("reset:$caller")

        val containerSize = containerSize
        val contentSize = contentSize
        val contentOriginSize = contentOriginSize
        val readMode = readMode
        val rotation = rotation
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val mediumScaleMinMultiple = mediumScaleMinMultiple

        val initialZoom = com.github.panpf.zoomimage.util.computeInitialZoom(
            containerSize = containerSize,
            contentSize = contentSize,
            contentOriginSize = contentOriginSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            rotation = rotation,
            readMode = readMode,
            mediumScaleMinMultiple = mediumScaleMinMultiple,
        )
        logger.d {
            val transform = initialZoom.baseTransform + initialZoom.userTransform
            "reset. containerSize=$containerSize, " +
                    "contentSize=$contentSize, " +
                    "contentOriginSize=$contentOriginSize, " +
                    "contentScale=${contentScale.name}, " +
                    "contentAlignment=${contentAlignment.name}, " +
                    "rotation=$rotation, " +
                    "mediumScaleMinMultiple=$mediumScaleMinMultiple, " +
                    "readMode=$readMode. " +
                    "minScale=${initialZoom.minScale}, " +
                    "mediumScale=${initialZoom.mediumScale}, " +
                    "maxScale=${initialZoom.maxScale}, " +
                    "baseTransform=${initialZoom.baseTransform}, " +
                    "initialUserTransform=${initialZoom.userTransform}, " +
                    "transform=${transform.toShortString()}"
        }

        minScale = initialZoom.minScale
        mediumScale = initialZoom.mediumScale
        maxScale = initialZoom.maxScale
        baseTransform = initialZoom.baseTransform
        updateUserTransform(
            targetUserTransform = initialZoom.userTransform,
            animated = false,
            caller = "reset"
        )
    }

    private fun limitUserScale(targetUserScale: Float): Float {
        val minUserScale = minScale / baseTransform.scaleX
        val maxUserScale = maxScale / baseTransform.scaleX
        return targetUserScale.coerceIn(minimumValue = minUserScale, maximumValue = maxUserScale)
    }

    private fun limitUserScaleWithRubberBand(targetUserScale: Float): Float {
        val minUserScale = minScale / baseTransform.scaleX
        val maxUserScale = maxScale / baseTransform.scaleX
        return limitScaleWithRubberBand(
            currentScale = userTransform.scaleX,
            targetScale = targetUserScale,
            minScale = minUserScale,
            maxScale = maxUserScale
        )
    }

    private fun limitUserOffset(userOffset: OffsetCompat, userScale: Float): OffsetCompat {
        val userOffsetBounds = com.github.panpf.zoomimage.util.computeUserOffsetBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
            userScale = userScale,
        ).round().toRect()
        if (userOffset.x >= userOffsetBounds.left
            && userOffset.x <= userOffsetBounds.right
            && userOffset.y >= userOffsetBounds.top
            && userOffset.y <= userOffsetBounds.bottom
        ) {
            return userOffset
        }
        return OffsetCompat(
            x = userOffset.x.coerceIn(userOffsetBounds.left, userOffsetBounds.right),
            y = userOffset.y.coerceIn(userOffsetBounds.top, userOffsetBounds.bottom),
        )
    }

    private fun updateUserTransform(
        targetUserTransform: TransformCompat,
        animated: Boolean,
        caller: String
    ) {
        if (animated) {
            val currentUserTransform = userTransform
            val scaleChange = currentUserTransform.scale != targetUserTransform.scale
            lastScaleAnimatable = FloatAnimatable(
                view = view,
                startValue = 0f,
                endValue = 1f,
                durationMillis = animationSpec.durationMillis,
                interpolator = animationSpec.interpolator,
                onUpdateValue = { value ->
                    val userTransform = lerp(
                        start = currentUserTransform,
                        stop = targetUserTransform,
                        fraction = value
                    )
                    logger.d {
                        "$caller. animated running. transform=${userTransform.toShortString()}"
                    }
                    this@ZoomEngine2.userTransform = userTransform
                    updateTransform()
                },
                onEnd = {
                    if (scaleChange) {
                        scaling = false
                    }
                    notifyMatrixChanged()
                }
            )
            if (scaleChange) {
                scaling = true
            }
            lastScaleAnimatable?.start()
        } else {
            this.userTransform = targetUserTransform
            updateTransform()
        }
    }

    private fun updateTransform() {
        transform = baseTransform + userTransform

        containerVisibleRect = computeContainerVisibleRect(
            containerSize = containerSize,
            userScale = userTransform.scaleX,
            userOffset = userTransform.offset
        ).round()
        contentBaseDisplayRect = computeContentBaseDisplayRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
        ).round()
        contentBaseVisibleRect = computeContentBaseVisibleRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
        ).round()
        contentDisplayRect = computeContentDisplayRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
            userScale = userTransform.scaleX,
            userOffset = userTransform.offset,
        ).round()
        contentVisibleRect = computeContentVisibleRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
            userScale = userTransform.scaleX,
            userOffset = userTransform.offset,
        ).round()

        val userOffsetBounds = com.github.panpf.zoomimage.util.computeUserOffsetBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
            userScale = userTransform.scaleX,
        )
        scrollEdge = computeScrollEdge(
            userOffsetBounds = userOffsetBounds,
            userOffset = userTransform.offset,
        )

        notifyMatrixChanged()
    }


    /*************************************** Interaction ******************************************/

    fun scale(
        targetScale: Float,
        contentPoint: IntOffsetCompat? = null,
        animated: Boolean = false
    ) {
        val containerSize = containerSize.takeIf { it.isNotEmpty() } ?: return
        val contentSize = contentSize.takeIf { it.isNotEmpty() } ?: return

        stopAllAnimation("scale")

        val targetUserScale = targetScale / baseTransform.scaleX
        val limitedTargetUserScale = limitUserScale(targetUserScale)
        val currentUserTransform = userTransform
        val currentUserScale = currentUserTransform.scaleX
        val currentUserOffset = currentUserTransform.offset

        val containerPoint = if (contentPoint != null) {
            contentPointToContainerPoint(
                containerSize = containerSize,
                contentSize = contentSize,
                contentScale = contentScale,
                contentAlignment = contentAlignment,
                rotation = rotation,
                contentPoint = contentPoint,
            )
        } else {
            containerSize.center
        }

        val targetUserOffset = computeTransformOffset(
            currentScale = currentUserScale,
            currentOffset = currentUserOffset,
            targetScale = limitedTargetUserScale,
            centroid = containerPoint.toOffset(),
            pan = OffsetCompat.Zero,
            gestureRotate = 0f,
        )
        val limitedTargetUserOffset = limitUserOffset(targetUserOffset, limitedTargetUserScale)
        val limitedTargetUserTransform = currentUserTransform.copy(
            scale = ScaleFactorCompat(limitedTargetUserScale),
            offset = limitedTargetUserOffset
        )
        logger.d {
            val targetAddUserScale = targetUserScale - currentUserScale
            val limitedAddUserScale = limitedTargetUserScale - currentUserScale
            val targetAddUserOffset = targetUserOffset - currentUserOffset
            val limitedTargetAddOffset = limitedTargetUserOffset - currentUserOffset
            "scale. " +
                    "targetScale=${targetScale.format(4)}, " +
                    "contentPoint=${contentPoint?.toShortString()}, " +
                    "animated=${animated}. " +
                    "containerPoint=${containerPoint.toShortString()}, " +
                    "targetUserScale=${targetUserScale.format(4)}, " +
                    "addUserScale=${targetAddUserScale.format(4)} -> ${limitedAddUserScale.format(4)}, " +
                    "addUserOffset=${targetAddUserOffset.toShortString()} -> ${limitedTargetAddOffset.toShortString()}, " +
                    "userTransform=${currentUserTransform.toShortString()} -> ${limitedTargetUserTransform.toShortString()}"
        }

        updateUserTransform(
            targetUserTransform = limitedTargetUserTransform,
            animated = animated,
            caller = "scale"
        )
    }

    fun offset(
        targetOffset: OffsetCompat,
        animated: Boolean = false
    ) {
        containerSize.takeIf { it.isNotEmpty() } ?: return
        contentSize.takeIf { it.isNotEmpty() } ?: return

        stopAllAnimation("offset")

        val targetUserOffset = targetOffset - (baseTransform.offset.times(userTransform.scale))
        val currentUserTransform = userTransform
        val currentUserScale = currentUserTransform.scaleX
        val limitedTargetUserOffset = limitUserOffset(targetUserOffset, currentUserScale)
        val limitedTargetUserTransform = currentUserTransform.copy(offset = limitedTargetUserOffset)
        logger.d {
            val currentUserOffset = currentUserTransform.offset
            val targetAddUserOffset = targetUserOffset - currentUserOffset
            val limitedTargetAddUserOffset = limitedTargetUserOffset - currentUserOffset
            "offset. " +
                    "targetOffset=${targetOffset.toShortString()}, " +
                    "animated=${animated}. " +
                    "targetUserOffset=${targetUserOffset.toShortString()}, " +
                    "currentUserScale=${currentUserScale.format(4)}, " +
                    "addUserOffset=${targetAddUserOffset.toShortString()} -> ${limitedTargetAddUserOffset}, " +
                    "userTransform=${currentUserTransform.toShortString()} -> ${limitedTargetUserTransform.toShortString()}"
        }

        updateUserTransform(
            targetUserTransform = limitedTargetUserTransform,
            animated = animated,
            caller = "offset"
        )
    }

    fun location(
        contentPoint: IntOffsetCompat,
        targetScale: Float = transform.scaleX,
        animated: Boolean = false,
    ) {
        val containerSize = containerSize.takeIf { it.isNotEmpty() } ?: return
        val contentSize =
            contentSize.takeIf { it.isNotEmpty() } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentUserTransform = userTransform
        val rotation = rotation

        stopAllAnimation("location")

        val containerPoint = contentPointToContainerPoint(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            rotation = rotation,
            contentPoint = contentPoint,
        )

        val targetUserScale = targetScale / baseTransform.scaleX
        val limitedTargetUserScale = limitUserScale(targetUserScale)

        val targetUserOffset = computeLocationUserOffset(
            containerSize = containerSize,
            containerPoint = containerPoint,
            userScale = limitedTargetUserScale,
        )
        val limitedTargetUserOffset = limitUserOffset(targetUserOffset, limitedTargetUserScale)
        val limitedTargetUserTransform = currentUserTransform.copy(
            scale = ScaleFactorCompat(limitedTargetUserScale),
            offset = limitedTargetUserOffset
        )
        logger.d {
            val currentUserScale = currentUserTransform.scaleX
            val currentUserOffset = currentUserTransform.offset
            val targetAddUserScale = targetUserScale - currentUserScale
            val limitedTargetAddUserScale = limitedTargetUserScale - currentUserScale
            val targetAddUserOffset = targetUserOffset - currentUserOffset
            val limitedTargetAddUserOffset = limitedTargetUserOffset - currentUserOffset
            val limitedTargetAddUserScaleFormatted = limitedTargetAddUserScale.format(4)
            "location. " +
                    "contentPoint=${contentPoint.toShortString()}, " +
                    "targetScale=${targetScale.format(4)}, " +
                    "animated=${animated}. " +
                    "containerSize=${containerSize.toShortString()}, " +
                    "contentSize=${contentSize.toShortString()}, " +
                    "containerPoint=${containerPoint.toShortString()}, " +
                    "addUserScale=${targetAddUserScale.format(4)} -> $limitedTargetAddUserScaleFormatted, " +
                    "addUserOffset=${targetAddUserOffset.toShortString()} -> ${limitedTargetAddUserOffset.toShortString()}, " +
                    "userTransform=${currentUserTransform.toShortString()} -> ${limitedTargetUserTransform.toShortString()}"
        }

        updateUserTransform(
            targetUserTransform = limitedTargetUserTransform,
            animated = animated,
            caller = "location"
        )
    }

    fun rotate(targetRotation: Int) {
        require(targetRotation >= 0) { "rotation must be greater than or equal to 0: $targetRotation" }
        require(targetRotation % 90 == 0) { "rotation must be in multiples of 90: $targetRotation" }
        val limitedTargetRotation = targetRotation % 360
        val currentRotation = rotation
        if (currentRotation == limitedTargetRotation) return

        stopAllAnimation("rotate")

        rotation = limitedTargetRotation
        reset("rotate")
    }

    fun transform(
        centroid: OffsetCompat,
        panChange: OffsetCompat,
        zoomChange: Float,
        rotationChange: Float
    ) {
        containerSize.takeIf { it.isNotEmpty() } ?: return
        contentSize.takeIf { it.isNotEmpty() } ?: return
        this.lastTransformCentroid = centroid

        val targetScale = transform.scaleX * zoomChange
        val targetUserScale = targetScale / baseTransform.scaleX
        val limitedTargetUserScale = if (rubberBandScale) {
            limitUserScaleWithRubberBand(targetUserScale)
        } else {
            limitUserScale(targetUserScale)
        }
        val currentUserTransform = userTransform
        val currentUserScale = currentUserTransform.scaleX
        val currentUserOffset = currentUserTransform.offset
        val targetUserOffset = computeTransformOffset(
            currentScale = currentUserScale,
            currentOffset = currentUserOffset,
            targetScale = limitedTargetUserScale,
            centroid = centroid,
            pan = panChange,
            gestureRotate = 0f,
        )
        val limitedTargetUserOffset = limitUserOffset(targetUserOffset, limitedTargetUserScale)
        val limitedTargetUserTransform = currentUserTransform.copy(
            scale = ScaleFactorCompat(limitedTargetUserScale),
            offset = limitedTargetUserOffset
        )
        logger.d {
            val targetAddUserScale = targetUserScale - currentUserScale
            val limitedAddUserScale = limitedTargetUserScale - currentUserScale
            val targetAddUserOffset = targetUserOffset - currentUserOffset
            val limitedTargetAddOffset = limitedTargetUserOffset - currentUserOffset
            "transform. " +
                    "centroid=${centroid.toShortString()}, " +
                    "panChange=${panChange.toShortString()}, " +
                    "zoomChange=${zoomChange.format(4)}, " +
                    "rotationChange=${rotationChange.format(4)}. " +
                    "targetScale=${targetScale.format(4)}, " +
                    "targetUserScale=${targetUserScale.format(4)}, " +
                    "addUserScale=${targetAddUserScale.format(4)} -> ${limitedAddUserScale.format(4)}, " +
                    "addUserOffset=${targetAddUserOffset.toShortString()} -> ${limitedTargetAddOffset.toShortString()}, " +
                    "userTransform=${currentUserTransform.toShortString()} -> ${limitedTargetUserTransform.toShortString()}"
        }

        updateUserTransform(
            targetUserTransform = limitedTargetUserTransform,
            animated = false,
            caller = "transform"
        )
    }

    fun fling(velocityX: Float, velocityY: Float) {
        stopAllAnimation("fling")
        val userTransform = userTransform
        val startUserOffset = userTransform.offset
        val userOffsetBounds = com.github.panpf.zoomimage.util.computeUserOffsetBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            alignment = contentAlignment,
            rotation = rotation,
            userScale = userTransform.scaleX,
        ).let {
            Rect(
                it.left.roundToInt(),
                it.top.roundToInt(),
                it.right.roundToInt(),
                it.bottom.roundToInt()
            )
        }
        val velocity = IntOffsetCompat(velocityX.roundToInt(), velocityY.roundToInt())
        logger.d {
            "fling. start. " +
                    "start=${startUserOffset.toShortString()}, " +
                    "bounds=${userOffsetBounds.toShortString()}, " +
                    "velocity=${velocity.toShortString()}"
        }
        lastFlingAnimatable = FlingAnimatable(
            view = view,
            start = startUserOffset.round(),
            bounds = userOffsetBounds,
            velocity = velocity,
            onUpdateValue = { value ->
                val targetUserOffset =
                    this@ZoomEngine2.userTransform.copy(offset = value.toOffset())
                updateUserTransform(targetUserOffset, false, "fling")
            },
            onEnd = {
                fling = false
                notifyMatrixChanged()
            }
        )
        fling = true
        lastFlingAnimatable?.start()
    }

    /**
     * Roll back to minimum or maximum scaling
     */
    fun rollbackScale(): Boolean {
        val lastTransformCentroid = lastTransformCentroid ?: return false
        val minScale = minScale
        val maxScale = maxScale
        val currentScale = transform.scaleX
        val targetScale = when {
            currentScale.format(2) > maxScale.format(2) -> maxScale
            currentScale.format(2) < minScale.format(2) -> minScale
            else -> null
        }
        if (targetScale != null) {
            val startScale = currentScale
            val endScale = targetScale
            logger.d {
                "rollbackScale. " +
                        "lastTransformCentroid=${lastTransformCentroid.toShortString()}. " +
                        "startScale=${startScale.format(4)}, " +
                        "endScale=${endScale.format(4)}"
            }
            lastScaleAnimatable = FloatAnimatable(
                view = view,
                startValue = 0f,
                endValue = 1f,
                durationMillis = animationSpec.durationMillis,
                interpolator = animationSpec.interpolator,
                onUpdateValue = { value ->
                    val frameScale = com.github.panpf.zoomimage.view.internal.lerp(
                        start = startScale,
                        stop = endScale,
                        fraction = value
                    )
                    val nowScale = transform.scaleX
                    val addScale = frameScale / nowScale
                    transform(
                        centroid = lastTransformCentroid,
                        panChange = OffsetCompat.Zero,
                        zoomChange = addScale,
                        rotationChange = 0f
                    )
                },
                onEnd = {
                    scaling = false
                    notifyMatrixChanged()
                }
            )

            scaling = true
            lastScaleAnimatable?.start()
        }
        return targetScale != null
    }

    fun switchScale(
        contentPoint: IntOffsetCompat? = null,
        animated: Boolean = true
    ): Float {
        val finalContentPoint = contentPoint
            ?: contentVisibleRect.takeIf { !it.isEmpty }?.center
            ?: contentSize.takeIf { it.isNotEmpty() }?.center
            ?: return transform.scaleX
        val nextScale = getNextStepScale()
        location(
            contentPoint = finalContentPoint,
            targetScale = nextScale,
            animated = animated
        )
        return nextScale
    }

    fun getNextStepScale(): Float {
        val stepScales = if (threeStepScale) {
            floatArrayOf(minScale, mediumScale, maxScale)
        } else {
            floatArrayOf(minScale, mediumScale)
        }
        return calculateNextStepScale(stepScales, transform.scaleX)
    }

    fun clean() {
        lastScaleAnimatable?.stop()
        lastScaleAnimatable = null
        lastFlingAnimatable?.stop()
        lastFlingAnimatable = null
    }

    fun stopAllAnimation(caller: String) {
        val lastScaleAnimatable = lastScaleAnimatable
        if (lastScaleAnimatable?.running == true) {
            lastScaleAnimatable.stop()
            scaling = false
            logger.d { "stopScaleAnimation:$caller" }
        }

        val lastFlingAnimatable = lastFlingAnimatable
        if (lastFlingAnimatable?.running == true) {
            lastFlingAnimatable.stop()
            fling = false
            logger.d { "stopFlingAnimation:$caller" }
        }
    }

    fun touchPointToContentPoint(touchPoint: OffsetCompat): IntOffsetCompat {
        val containerSize = containerSize.takeIf { it.isNotEmpty() } ?: return IntOffsetCompat.Zero
        val contentSize = contentSize.takeIf { it.isNotEmpty() } ?: return IntOffsetCompat.Zero
        val currentUserTransform = userTransform
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val rotation = rotation
        val containerPoint = touchPointToContainerPoint(
            containerSize = containerSize,
            userScale = currentUserTransform.scaleX,
            userOffset = currentUserTransform.offset,
            touchPoint = touchPoint
        )
        val contentPoint = containerPointToContentPoint(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            rotation = rotation,
            containerPoint = containerPoint
        )
        return contentPoint
    }

    /**
     * Whether you can scroll horizontally or vertical in the specified direction
     *
     * @param direction Negative to check scrolling left, positive to check scrolling right.
     */
    fun canScroll(horizontal: Boolean, direction: Int): Boolean {
        return canScrollByEdge(scrollEdge, horizontal, direction)
    }

    fun addOnMatrixChangeListener(listener: OnMatrixChangeListener) {
        this.onMatrixChangeListeners = (onMatrixChangeListeners ?: LinkedHashSet())
            .apply { add(listener) }
    }

    fun removeOnMatrixChangeListener(listener: OnMatrixChangeListener): Boolean {
        return onMatrixChangeListeners?.remove(listener) == true
    }

    fun addOnViewSizeChangeListener(listener: OnViewSizeChangeListener) {
        this.onViewSizeChangeListeners = (onViewSizeChangeListeners ?: LinkedHashSet())
            .apply { add(listener) }
    }

    fun removeOnViewSizeChangeListener(listener: OnViewSizeChangeListener): Boolean {
        return onViewSizeChangeListeners?.remove(listener) == true
    }

    fun addOnDrawableSizeChangeListener(listener: OnDrawableSizeChangeListener) {
        this.onDrawableSizeChangeListeners = (onDrawableSizeChangeListeners ?: LinkedHashSet())
            .apply { add(listener) }
    }

    fun removeOnDrawableSizeChangeListener(listener: OnDrawableSizeChangeListener): Boolean {
        return onDrawableSizeChangeListeners?.remove(listener) == true
    }

    private fun notifyMatrixChanged() {
        onMatrixChangeListeners?.forEach { listener ->
            listener.onMatrixChanged()
        }
    }

    private fun notifyViewSizeChanged() {
        onViewSizeChangeListeners?.forEach {
            it.onSizeChanged()
        }
    }

    private fun notifyDrawableSizeChanged() {
        onDrawableSizeChangeListeners?.forEach {
            it.onSizeChanged()
        }
    }
}