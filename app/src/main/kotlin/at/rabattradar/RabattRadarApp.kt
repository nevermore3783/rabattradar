package at.rabattradar

import android.app.Application
import at.rabattradar.core.net.OkHttp
import at.rabattradar.data.OfferRepository
import at.rabattradar.data.SettingsRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class RabattRadarApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient: OkHttpClient by lazy { OkHttp.defaultClient() }

    val settings by lazy { SettingsRepository(this) }
    val offers by lazy { OfferRepository(this, settings, httpClient, appScope) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            // Some image CDNs reject requests without a browser-like user agent.
            httpClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", OkHttp.USER_AGENT).build())
                }
                .build()
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
