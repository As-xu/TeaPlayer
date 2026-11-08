package com.teaplayer.core.data

import com.teaplayer.core.domain.SearchableSource
import com.teaplayer.core.domain.SourceResult
import com.teaplayer.core.model.CatalogItem
import com.teaplayer.core.model.SourceError
import com.teaplayer.core.net.SafeHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 苹果CMS v10（MacCMS）采集站适配器。
 *
 * 实测口径（docs/PLAN.md §2 E4–E6）：`ac=list` 一次 14–16 KB / 20 条，
 * TTFB 0.6–2.2 s，`vod_play_url` 在列表层恒为空——真正的地址要等用户点进详情再 `ac=detail&ids=`。
 */
class MacCmsSource(
    override val id: String,
    override val displayName: String,
    private val apiBase: String,
    private val http: SafeHttpClient,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : SearchableSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun search(keyword: String, page: Int): SourceResult<List<CatalogItem>> =
        fetch(query = "&wd=" + java.net.URLEncoder.encode(keyword, "UTF-8"), page = page)

    override suspend fun browse(typeId: String?, page: Int): SourceResult<List<CatalogItem>> =
        fetch(query = typeId?.let { "&t=$it" } ?: "", page = page)

    private suspend fun fetch(query: String, page: Int): SourceResult<List<CatalogItem>> {
        val sep = if (apiBase.contains('?')) '&' else '?'
        val url = "$apiBase${sep}ac=list$query&pg=$page"
        return when (val r = http.get(url, SafeHttpClient.Kind.Json, extraHeaders)) {
            is SafeHttpClient.Outcome.Fail -> SourceResult.Err(r.error)
            is SafeHttpClient.Outcome.Ok -> parse(r.value.bytes)
        }
    }

    internal fun parse(bytes: ByteArray): SourceResult<List<CatalogItem>> {
        val root = try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return SourceResult.Err(SourceError.Parse)
        }
        val obj = root as? JsonObject ?: return SourceResult.Err(SourceError.Parse)
        val list = obj["list"] as? JsonArray ?: return SourceResult.Ok(emptyList())

        val items = list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val vodId = o.str("vod_id") ?: return@mapNotNull null
            val name = o.str("vod_name") ?: return@mapNotNull null
            CatalogItem(
                sourceId = id,
                sourceItemId = vodId,
                title = name,
                year = o.str("vod_year")?.takeIf { it.isNotBlank() && it != "0" },
                category = o.str("vod_class") ?: o.str("type_name"),
                coverUrl = o.str("vod_pic")?.takeIf { it.startsWith("http") },
                remark = o.str("vod_remarks")?.takeIf { it.isNotBlank() },
                hasPlayFrom = !o.str("vod_play_from").isNullOrBlank(),
            )
        }
        return SourceResult.Ok(items)
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
