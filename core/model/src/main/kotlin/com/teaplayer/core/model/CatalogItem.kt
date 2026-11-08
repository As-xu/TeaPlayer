package com.teaplayer.core.model

/** 列表/搜索结果里的「一片」，已按 contentKey 聚合前的最小单元。 */
data class CatalogItem(
    val sourceId: String,
    val sourceItemId: String,
    val title: String,
    val year: String? = null,
    val category: String? = null,
    val coverUrl: String? = null,
    val remark: String? = null,
    /** 该条在源站是否有可播线路（ac=list 不返回 play_url，所以这里是「可能」）。 */
    val hasPlayFrom: Boolean = false,
)
