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

package com.github.panpf.zoomimage.view.coil.internal

import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.panpf.zoomimage.coil.internal.dataToImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import com.github.panpf.zoomimage.view.coil.CoilViewSubsamplingImageGenerator

class EngineCoilViewSubsamplingImageGenerator : CoilViewSubsamplingImageGenerator {

    override suspend fun generateImage(
        context: PlatformContext,
        imageLoader: ImageLoader,
        request: ImageRequest,
        result: SuccessResult,
        drawable: Drawable
    ): SubsamplingImageGenerateResult {
        val data = request.data
        val imageSource = dataToImageSource(context, imageLoader, data)
            ?: return SubsamplingImageGenerateResult.Error("Unsupported data")
        return SubsamplingImageGenerateResult.Success(SubsamplingImage(imageSource, null))
    }
}