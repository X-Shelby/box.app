package com.box.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.asImage

data class AppIcon(val packageName: String)

class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String = "appicon:${data.packageName}"
}

class AppIconFetcher(
    private val context: Context,
    private val data: AppIcon,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pm = context.packageManager
        val drawable: Drawable = try {
            pm.getApplicationIcon(data.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            throw e
        }
        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher? {
            return AppIconFetcher(context.applicationContext, data)
        }
    }
}

fun buildAppIconImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(AppIconKeyer())
            add(AppIconFetcher.Factory(context))
            // 注册 OkHttp 网络 fetcher，支持远程图片加载（核心图标等）
            add(coil3.network.okhttp.OkHttpNetworkFetcherFactory())
        }
        .build()
}
