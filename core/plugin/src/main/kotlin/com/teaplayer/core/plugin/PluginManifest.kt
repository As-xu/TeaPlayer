package com.teaplayer.core.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 插件包清单（`manifest.json`），对应 `TeaPlayerApp/spec/manifest.schema.json`（待落）。
 * 只声明「它能访问哪些域名、能提供哪些钩子」，不含任何代码。
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    @SerialName("hostApi") val hostApi: Int = 1,
    val main: String = "main.js",
    val allowHosts: List<String> = emptyList(),
    val baseUrls: Map<String, String> = emptyMap(),
    val hooks: List<String> = emptyList(),
    val description: String? = null,
    val author: String? = null,
    val updateUrl: String? = null,
)

/** 已安装插件（先内存，P1 后段落 Room）。 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val sha256: String,
    val sizeBytes: Long,
    val source: InstallSource,
    val enabled: Boolean = true,
)

sealed class InstallSource {
    data object Seed : InstallSource()
    data object LocalFile : InstallSource()
    data class Http(val url: String) : InstallSource()
    data class Market(val indexId: String) : InstallSource()
}

/** 校验失败项。码为**候选**，冻结后与 docs/design/copy-errors.md 对齐。 */
data class Violation(val code: String, val detail: String)

sealed class InstallOutcome {
    data class Ok(val plugin: InstalledPlugin) : InstallOutcome()
    data class Fail(val violations: List<Violation>) : InstallOutcome()
}
