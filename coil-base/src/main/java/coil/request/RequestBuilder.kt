@file:Suppress("unused")

package coil.request

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import coil.DefaultRequestOptions
import coil.ImageLoaderBuilder
import coil.annotation.BuilderMarker
import coil.decode.DataSource
import coil.drawable.CrossfadeDrawable
import coil.fetch.Fetcher
import coil.memory.RequestService
import coil.size.PixelSize
import coil.size.Scale
import coil.size.Size
import coil.size.SizeResolver
import coil.target.ImageViewTarget
import coil.target.Target
import coil.transform.Transformation
import coil.util.Utils
import coil.util.self
import kotlinx.coroutines.CoroutineDispatcher

/** Base class for [LoadRequestBuilder] and [GetRequestBuilder]. */
@BuilderMarker
sealed class RequestBuilder<T : RequestBuilder<T>> {

    protected var data: Any?

    protected var keyOverride: String?
    protected var listener: Request.Listener?
    protected var sizeResolver: SizeResolver?
    protected var scale: Scale?
    protected var dispatcher: CoroutineDispatcher
    protected var transformations: List<Transformation>
    protected var bitmapConfig: Bitmap.Config
    protected var colorSpace: ColorSpace? = null

    protected var networkCachePolicy: CachePolicy
    protected var diskCachePolicy: CachePolicy
    protected var memoryCachePolicy: CachePolicy

    protected var allowHardware: Boolean
    protected var allowRgb565: Boolean

    constructor(defaults: DefaultRequestOptions) {
        data = null

        keyOverride = null
        listener = null
        sizeResolver = null
        scale = null
        dispatcher = defaults.dispatcher
        transformations = emptyList()
        bitmapConfig = Utils.getDefaultBitmapConfig()
        if (SDK_INT >= O) {
            colorSpace = null
        }

        networkCachePolicy = CachePolicy.ENABLED
        diskCachePolicy = CachePolicy.ENABLED
        memoryCachePolicy = CachePolicy.ENABLED

        allowHardware = true
        allowRgb565 = defaults.allowRgb565
    }

    constructor(request: Request) {
        data = request.data

        keyOverride = request.keyOverride
        listener = request.listener
        sizeResolver = request.sizeResolver
        scale = request.scale
        dispatcher = request.dispatcher
        transformations = request.transformations
        bitmapConfig = request.bitmapConfig
        if (SDK_INT >= O) {
            colorSpace = request.colorSpace
        }

        networkCachePolicy = request.networkCachePolicy
        diskCachePolicy = request.diskCachePolicy
        memoryCachePolicy = request.memoryCachePolicy

        allowHardware = request.allowHardware
        allowRgb565 = request.allowRgb565
    }

    /**
     * Convenience function to create and set the [Request.Listener].
     */
    inline fun listener(
        crossinline onStart: (data: Any) -> Unit = {},
        crossinline onCancel: (data: Any) -> Unit = {},
        crossinline onError: (data: Any, throwable: Throwable) -> Unit = { _, _ -> },
        crossinline onSuccess: (data: Any, source: DataSource) -> Unit = { _, _ -> }
    ): T = self {
        listener(object : Request.Listener {
            override fun onStart(data: Any) = onStart(data)
            override fun onCancel(data: Any) = onCancel(data)
            override fun onError(data: Any, throwable: Throwable) = onError(data, throwable)
            override fun onSuccess(data: Any, source: DataSource) = onSuccess(data, source)
        })
    }

    /**
     * Set the [Request.Listener].
     */
    fun listener(listener: Request.Listener?): T = self {
        this.listener = listener
    }

    /**
     * Set the list of [Transformation]s to be applied to this request.
     */
    fun transformations(vararg transformations: Transformation): T = self {
        this.transformations = transformations.toList()
    }

    /**
     * Set the list of [Transformation]s to be applied to this request.
     */
    fun transformations(transformations: List<Transformation>): T = self {
        this.transformations = transformations
    }

    /**
     * Set the [CoroutineDispatcher].
     */
    fun dispatcher(dispatcher: CoroutineDispatcher): T = self {
        this.dispatcher = dispatcher
    }

    /**
     * Set the requested width/height. Coil will attempt to load the image into memory with these dimensions.
     */
    fun size(@Px size: Int): T = self {
        size(size, size)
    }

    /**
     * Set the requested width/height. Coil will attempt to load the image into memory with these dimensions.
     */
    fun size(@Px width: Int, @Px height: Int): T = self {
        size(PixelSize(width, height))
    }

    /**
     * Set the requested width/height. Coil will attempt to load the image into memory with these dimensions.
     */
    fun size(size: Size): T = self {
        this.sizeResolver = SizeResolver(size)
    }

    /**
     * Set the requested width/height. Coil will attempt to load the image into memory with these dimensions.
     *
     * If this isn't set, Coil will attempt to determine the size of the request using the logic in [RequestService.sizeResolver].
     */
    fun size(resolver: SizeResolver): T = self {
        this.sizeResolver = resolver
    }

    /**
     * Set the scaling algorithm that will be used to fit/fill the image into the dimensions provided by [sizeResolver].
     *
     * If this isn't set, Coil will attempt to determine the scale of the request using the logic in [RequestService.scale].
     *
     * NOTE: If [scale] is not set, it is automatically computed for [ImageView] targets.
     */
    fun scale(scale: Scale): T = self {
        this.scale = scale
    }

    /**
     * Enable/disable the use of [Bitmap.Config.HARDWARE] for this request.
     *
     * If false, any use of [Bitmap.Config.HARDWARE] will be treated as [Bitmap.Config.ARGB_8888].
     *
     * This is useful for shared element transitions, which do not support hardware Bitmaps.
     */
    fun allowHardware(enable: Boolean): T = self {
        this.allowHardware = enable
    }

    /**
     * See: [ImageLoaderBuilder.allowRgb565]
     */
    fun allowRgb565(enable: Boolean): T = self {
        this.allowRgb565 = enable
    }

    /**
     * Set the preferred [Bitmap.Config].
     *
     * This is not guaranteed and a different config may be used in some situations.
     */
    fun bitmapConfig(bitmapConfig: Bitmap.Config): T = self {
        this.bitmapConfig = bitmapConfig
    }

    /**
     * Set the preferred [ColorSpace].
     */
    @RequiresApi(O)
    fun colorSpace(colorSpace: ColorSpace): T = self {
        this.colorSpace = colorSpace
    }

    /**
     * Set the cache key for this request.
     *
     * By default, the cache key is computed by the [Fetcher] and any [Transformation]s.
     */
    fun key(key: String?): T = self {
        this.keyOverride = key
    }

    /**
     * Enable/disable reading/writing from/to the network.
     *
     * NOTE: Disabling writes has no effect.
     */
    fun networkCachePolicy(policy: CachePolicy): T = self {
        this.networkCachePolicy = policy
    }

    /**
     * Enable/disable reading/writing from/to the disk cache.
     */
    fun diskCachePolicy(policy: CachePolicy): T = self {
        this.diskCachePolicy = policy
    }

    /**
     * Enable/disable reading/writing from/to the memory.
     */
    fun memoryCachePolicy(policy: CachePolicy): T = self {
        this.memoryCachePolicy = policy
    }
}

/** Builder for a [LoadRequest]. */
class LoadRequestBuilder : RequestBuilder<LoadRequestBuilder> {

    private val context: Context

    private var target: Target?
    private var lifecycle: Lifecycle?
    private var crossfadeMillis: Int

    @DrawableRes private var placeholderResId: Int
    @DrawableRes private var errorResId: Int
    private var placeholderDrawable: Drawable?
    private var errorDrawable: Drawable?

    constructor(context: Context, defaults: DefaultRequestOptions) : super(defaults) {
        this.context = context

        target = null
        lifecycle = null
        crossfadeMillis = defaults.crossfadeMillis

        placeholderResId = 0
        errorResId = 0
        placeholderDrawable = defaults.placeholder
        errorDrawable = defaults.error
    }

    constructor(context: Context, request: LoadRequest) : super(request) {
        this.context = context

        target = request.target
        lifecycle = request.lifecycle
        crossfadeMillis = request.crossfadeMillis

        placeholderResId = request.placeholderResId
        errorResId = request.errorResId
        placeholderDrawable = request.placeholderDrawable
        errorDrawable = request.errorDrawable
    }

    /**
     * Set the data to load.
     */
    fun data(data: Any?) = apply {
        this.data = data
    }

    /**
     * Convenience function to set [imageView] as the [Target].
     */
    fun target(imageView: ImageView) = apply {
        target(ImageViewTarget(imageView))
    }

    /**
     * Convenience function to create and set the [Target].
     */
    inline fun target(
        crossinline onStart: (placeholder: Drawable?) -> Unit = {},
        crossinline onError: (error: Drawable?) -> Unit = {},
        crossinline onSuccess: (result: Drawable) -> Unit = {}
    ) = apply {
        target(object : Target {
            override fun onStart(placeholder: Drawable?) = onStart(placeholder)
            override fun onError(error: Drawable?) = onError(error)
            override fun onSuccess(result: Drawable) = onSuccess(result)
        })
    }

    /**
     * Set the [Target]. If the target is null, this request will preload the image into memory.
     */
    fun target(target: Target?) = apply {
        this.target = target
    }

    /**
     * Set the [Lifecycle] for this request.
     */
    fun lifecycle(owner: LifecycleOwner?) = apply {
        lifecycle(owner?.lifecycle)
    }

    /**
     * Set the [Lifecycle] for this request.
     *
     * Requests are queued while the lifecycle is not at least [Lifecycle.State.STARTED].
     * Requests are cancelled when the lifecycle reaches [Lifecycle.State.DESTROYED].
     *
     * If this isn't set, Coil will attempt to find the lifecycle for this request
     * using the logic in [RequestService.lifecycleInfo].
     */
    fun lifecycle(lifecycle: Lifecycle?) = apply {
        this.lifecycle = lifecycle
    }

    /**
     * See: [ImageLoaderBuilder.crossfade]
     */
    fun crossfade(enable: Boolean) = apply {
        this.crossfadeMillis = if (enable) CrossfadeDrawable.DEFAULT_DURATION else 0
    }

    /**
     * See: [ImageLoaderBuilder.crossfade]
     */
    fun crossfade(durationMillis: Int) = apply {
        require(durationMillis >= 0) { "Duration must be >= 0." }
        this.crossfadeMillis = durationMillis
    }

    /**
     * Set a placeholder drawable to use when the request starts.
     */
    fun placeholder(@DrawableRes drawableResId: Int) = apply {
        this.placeholderResId = drawableResId
        this.placeholderDrawable = null
    }

    /**
     * Set a placeholder drawable to use when the request starts.
     */
    fun placeholder(drawable: Drawable?) = apply {
        this.placeholderDrawable = drawable
        this.placeholderResId = 0
    }

    /**
     * Set an error drawable to use if the request fails.
     */
    fun error(@DrawableRes drawableResId: Int) = apply {
        this.errorResId = drawableResId
        this.errorDrawable = null
    }

    /**
     * Set an error drawable to use if the request fails.
     */
    fun error(drawable: Drawable?) = apply {
        this.errorDrawable = drawable
        this.errorResId = 0
    }

    /** Create a new [LoadRequest] instance. */
    fun build(): LoadRequest {
        return LoadRequest(
            context,
            data,
            target,
            lifecycle,
            crossfadeMillis,
            keyOverride,
            listener,
            sizeResolver,
            scale,
            dispatcher,
            transformations,
            bitmapConfig,
            colorSpace,
            networkCachePolicy,
            diskCachePolicy,
            memoryCachePolicy,
            allowHardware,
            allowRgb565,
            placeholderResId,
            errorResId,
            placeholderDrawable,
            errorDrawable
        )
    }
}

/** Builder for a [GetRequest]. */
class GetRequestBuilder : RequestBuilder<GetRequestBuilder> {

    constructor(defaults: DefaultRequestOptions) : super(defaults)

    constructor(request: GetRequest) : super(request)

    /**
     * Set the data to load.
     */
    fun data(data: Any) = apply {
        this.data = data
    }

    /** Create a new [GetRequest] instance. */
    fun build(): GetRequest {
        return GetRequest(
            checkNotNull(data) { "data == null" },
            keyOverride,
            listener,
            sizeResolver,
            scale,
            dispatcher,
            transformations,
            bitmapConfig,
            colorSpace,
            networkCachePolicy,
            diskCachePolicy,
            memoryCachePolicy,
            allowHardware,
            allowRgb565
        )
    }
}
