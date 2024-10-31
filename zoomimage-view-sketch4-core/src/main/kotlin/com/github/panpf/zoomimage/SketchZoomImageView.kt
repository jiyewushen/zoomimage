/*
 * Copyright (C) 2024 panpf <panpfpanpf@outlook.com>
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

package com.github.panpf.zoomimage

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.util.SketchUtils
import com.github.panpf.zoomimage.sketch.SketchTileImageCache
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import com.github.panpf.zoomimage.util.Logger
import com.github.panpf.zoomimage.view.sketch.SketchViewSubsamplingImageGenerator
import com.github.panpf.zoomimage.view.sketch.internal.AbsStateZoomImageView
import com.github.panpf.zoomimage.view.sketch.internal.EngineSketchViewSubsamplingImageGenerator

/**
 * An ImageView that integrates the Sketch image loading framework that zoom and subsampling huge images
 *
 * Example usages:
 *
 * ```kotlin
 * val sketchZoomImageView = SketchZoomImageView(context)
 * sketchZoomImageView.loadImage("https://sample.com/sample.jpeg") {
 *     placeholder(R.drawable.placeholder)
 *     crossfade()
 * }
 * ```
 *
 * @see com.github.panpf.zoomimage.view.sketch4.core.test.SketchZoomImageViewTest
 */
open class SketchZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AbsStateZoomImageView(context, attrs, defStyle) {

    private val subsamplingImageGenerators = mutableListOf<SketchViewSubsamplingImageGenerator>()
    private var resetImageSourceOnAttachedToWindow: Boolean = false

    fun registerSubsamplingImageGenerator(convertor: SketchViewSubsamplingImageGenerator) {
        subsamplingImageGenerators.add(0, convertor)
    }

    fun unregisterSubsamplingImageGenerator(convertor: SketchViewSubsamplingImageGenerator) {
        subsamplingImageGenerators.remove(convertor)
    }

    override fun newLogger(): Logger = Logger(tag = "SketchZoomImageView")

    override fun onDrawableChanged(oldDrawable: Drawable?, newDrawable: Drawable?) {
        super.onDrawableChanged(oldDrawable, newDrawable)
        if (isAttachedToWindow) {
            resetImageSource()
        } else {
            resetImageSourceOnAttachedToWindow = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (resetImageSourceOnAttachedToWindow) {
            resetImageSourceOnAttachedToWindow = false
            resetImageSource()
        }
    }

    private fun resetImageSource() {
        // You must use post to delay execution because 'SketchUtils.getResult' may not be ready when onDrawableChanged is executed.
        post {
            if (!isAttachedToWindow) {
                resetImageSourceOnAttachedToWindow = true
                return@post
            }

            val sketch = SketchUtils.getSketch(this)
            val subsamplingEngine = _subsamplingEngine ?: return@post
            val tileImageCacheState = subsamplingEngine.tileImageCacheState
            if (tileImageCacheState.value == null && sketch != null) {
                tileImageCacheState.value = SketchTileImageCache(sketch)
            }

            val result = SketchUtils.getResult(this)
            val drawable = drawable
            if (sketch != null && result is ImageResult.Success && drawable != null) {
                val request = result.request
                val generateResult = subsamplingImageGenerators
                    // TODO filter animatable drawable
                    .plus(EngineSketchViewSubsamplingImageGenerator())
                    .firstNotNullOfOrNull {
                        it.generateImage(sketch, request, result, drawable)
                    }
                if (generateResult is SubsamplingImageGenerateResult.Error) {
                    logger.d {
                        "SketchZoomImageView. ${generateResult.message}. uri='${request.uri}'"
                    }
                }
                if (generateResult is SubsamplingImageGenerateResult.Success) {
                    setImage(generateResult.subsamplingImage)
                } else {
                    setImage(null as SubsamplingImage?)
                }
            } else {
                setImage(null as SubsamplingImage?)
            }
        }
    }
}