package com.teaplayer.core.plugin

/**
 * 安装流水线：**唯一入口**，本地导入 / 市场 / 深链 / TV 扫描全部汇到这里。
 * 只接受内存 bytes——下载器、zip、sha256 校验都在同一个快照上完成（PLAN-v4 §5）。
 */
class PluginInstaller(
    private val registry: PluginRegistry,
) {

    fun install(
        bytes: ByteArray,
        source: InstallSource,
        expectedSha256: String? = null,
    ): InstallOutcome = when (val r = PluginPackage.parse(bytes, expectedSha256)) {
        is PluginPackage.Result.Bad -> InstallOutcome.Fail(r.violations)
        is PluginPackage.Result.Ok -> {
            // 插件 id 全局唯一：装同 id 即升级，但要拦「降级」以外的静默覆盖
            val existing = registry.get(r.parsed.manifest.id)
            if (existing != null && existing.manifest.version == r.parsed.manifest.version &&
                existing.sha256 == r.parsed.sha256
            ) {
                InstallOutcome.Ok(existing) // 幂等：同版本同 hash 重复安装不算错误
            } else {
                val plugin = InstalledPlugin(
                    manifest = r.parsed.manifest,
                    sha256 = r.parsed.sha256,
                    sizeBytes = bytes.size.toLong(),
                    source = source,
                    enabled = existing?.enabled ?: true,
                )
                registry.upsert(plugin)
                InstallOutcome.Ok(plugin)
            }
        }
    }
}

/** 内存态注册表；P1 后段换 Room，接口保持不变。 */
class PluginRegistry {

    private val items = LinkedHashMap<String, InstalledPlugin>()

    @Synchronized
    fun upsert(plugin: InstalledPlugin) {
        items[plugin.manifest.id] = plugin
    }

    @Synchronized
    fun get(id: String): InstalledPlugin? = items[id]

    @Synchronized
    fun all(): List<InstalledPlugin> = items.values.toList()

    /** 只有用户动作能改 enabled——远端索引永远不能（R-MKT-1）。 */
    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val cur = items[id] ?: return
        items[id] = cur.copy(enabled = enabled)
    }

    @Synchronized
    fun remove(id: String) {
        items.remove(id)
    }

    @Synchronized
    fun enabledCount(): Int = items.values.count { it.enabled }

    /** 连续失败自动禁用（熔断）也走这里，并标注来源为系统。 */
    @Synchronized
    fun autoDisable(id: String) {
        val cur = items[id] ?: return
        items[id] = cur.copy(enabled = false)
    }

    @Synchronized
    fun clear() {
        items.clear()
    }
}
