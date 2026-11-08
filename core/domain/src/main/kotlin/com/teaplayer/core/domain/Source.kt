package com.teaplayer.core.domain

import com.teaplayer.core.model.CatalogItem
import com.teaplayer.core.model.SourceError

/** 一个内容源的检索能力（v1 §3）。实现可以是内置种子，也可以是插件。 */
interface CatalogSource {
    val id: String
    val displayName: String
}

/** 解析结果：直链 / 需要二次跳 / 只能本地代理。 */
sealed interface PlaybackTarget {
    data class Hls(val url: String) : PlaybackTarget
    data class NeedsSecondHop(val url: String, val hints: Map<String, String> = emptyMap()) : PlaybackTarget
    data class LocalProxy(val url: String) : PlaybackTarget
}

/** 插件运行时端口：纯 JVM 签名，只有 JSON 与基本类型（v2 §5 规则 2）。 */
interface PluginRuntimePort {
    suspend fun call(pluginId: String, hook: String, payloadJson: String, deadlineMs: Long): String
    fun cancel(callId: String)
}

sealed class SourceResult<out T> {
    data class Ok<T>(val value: T) : SourceResult<T>()
    data class Err(val error: SourceError) : SourceResult<Nothing>()
}

/** 可检索的源：苹果CMS v10（MacCMS）系的标准两段式接口。 */
interface SearchableSource : CatalogSource {
    /** `ac=list&wd=` 层：只返回条目壳，不含播放地址。 */
    suspend fun search(keyword: String, page: Int = 1): SourceResult<List<CatalogItem>>

    /** 栏目浏览；`typeId` 为空表示全部。 */
    suspend fun browse(typeId: String? = null, page: Int = 1): SourceResult<List<CatalogItem>>
}
