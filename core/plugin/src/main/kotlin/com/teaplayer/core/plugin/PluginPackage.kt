package com.teaplayer.core.plugin

import com.teaplayer.core.net.HostGuard
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

/**
 * `.tpkg` 解包与校验。**必须在内存里完成**：`PluginInstaller` 只收 bytes 不收 File，
 * 避免「校验时文件已被换掉」的 TOCTOU（PLAN-v4 §5 TOCTOU 修正）。
 */
object PluginPackage {

    const val MAX_PACKAGE_BYTES = 8L * 1024 * 1024
    const val MAX_UNCOMPRESSED = 16L * 1024 * 1024
    const val MAX_ENTRIES = 200
    const val HOST_API = 1

    private val ID_RE = Regex("""^[a-z][a-z0-9]{2,39}$""")
    private val VERSION_RE = Regex("""^\d+\.\d+\.\d+([-+][0-9A-Za-z.\-]+)?$""")
    private val HOST_RE = Regex("""^(\*\.)?[a-z0-9]([a-z0-9\-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9\-]*[a-z0-9])?)*$""")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    data class Parsed(val manifest: PluginManifest, val scripts: Map<String, String>, val sha256: String)

    sealed class Result {
        data class Ok(val parsed: Parsed) : Result()
        data class Bad(val violations: List<Violation>) : Result()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * @param expectedSha256 期望值；null 表示调用方没有可信摘要（例如本地手选文件），
     *        此时只做结构校验，并在确认页标注「未校验来源」。
     */
    fun parse(bytes: ByteArray, expectedSha256: String? = null): Result {
        val bad = mutableListOf<Violation>()

        if (bytes.size > MAX_PACKAGE_BYTES) {
            return Result.Bad(listOf(Violation("E-PKG-10", "包体积 ${bytes.size}B 超过 ${MAX_PACKAGE_BYTES}B")))
        }
        // magic bytes：PK\x03\x04（空包是 PK\x05\x06）
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()
        if (!isZip) return Result.Bad(listOf(Violation("E-PKG-01", "不是 zip/tpkg 容器")))

        val actualSha = sha256(bytes)
        if (expectedSha256 != null && !expectedSha256.equals(actualSha, ignoreCase = true)) {
            return Result.Bad(listOf(Violation("E-SIG-01", "sha256 不符：期望 ${expectedSha256.take(12)}… 实际 ${actualSha.take(12)}…")))
        }

        var totalUncompressed = 0L
        var entryCount = 0
        var manifestText: String? = null
        val scripts = mutableMapOf<String, String>()

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > MAX_ENTRIES) {
                        bad += Violation("E-PKG-10", "条目数超过 $MAX_ENTRIES")
                        break
                    }
                    val name = e.name
                    if (!isSafeEntryName(name)) {
                        bad += Violation("E-PKG-09", "非法条目路径：$name")
                        continue
                    }
                    val data = zin.readBytes()
                    totalUncompressed += data.size
                    if (totalUncompressed > MAX_UNCOMPRESSED) {
                        bad += Violation("E-PKG-10", "解压后超过 ${MAX_UNCOMPRESSED}B（疑似压缩炸弹）")
                        break
                    }
                    when {
                        name == "manifest.json" -> manifestText = data.toString(Charsets.UTF_8)
                        name.endsWith(".js") -> scripts[name] = data.toString(Charsets.UTF_8)
                    }
                }
            }
        } catch (e: Exception) {
            return Result.Bad(listOf(Violation("E-PKG-01", "zip 解析失败：${e.javaClass.simpleName}")))
        }

        val text = manifestText ?: return Result.Bad(bad + Violation("E-PKG-02", "缺少 manifest.json"))
        val manifest = try {
            json.decodeFromString(PluginManifest.serializer(), text)
        } catch (e: Exception) {
            return Result.Bad(bad + Violation("E-PKG-03", "manifest 解析失败：${e.message?.take(120)}"))
        }

        bad += validate(manifest, scripts)
        return if (bad.isEmpty()) Result.Ok(Parsed(manifest, scripts, actualSha)) else Result.Bad(bad)
    }

    /** 清单层校验：id / version / hostApi / allowHosts / 入口脚本。 */
    fun validate(manifest: PluginManifest, scripts: Map<String, String>): List<Violation> {
        val bad = mutableListOf<Violation>()

        if (!ID_RE.matches(manifest.id)) {
            bad += Violation("E-PKG-04", "id 必须是小写字母开头、3–40 位 [a-z0-9]：${manifest.id}")
        }
        if (!VERSION_RE.matches(manifest.version)) {
            bad += Violation("E-PKG-05", "version 必须是 x.y.z：${manifest.version}")
        }
        if (manifest.hostApi != HOST_API) {
            bad += Violation("E-PKG-06", "hostApi=${manifest.hostApi}，本宿主只支持 $HOST_API")
        }
        if (manifest.allowHosts.isEmpty()) {
            bad += Violation("E-PKG-07", "allowHosts 不能为空——没有域名声明就用不了")
        }
        manifest.allowHosts.forEach { host ->
            val h = host.trim().lowercase()
            when {
                h == "*" || h.isEmpty() -> bad += Violation("E-PKG-07", "allowHosts 不允许空或通配全部：$host")
                !HOST_RE.matches(h) -> bad += Violation("E-PKG-07", "allowHosts 不是合法域名：$host")
                HostGuard.parseLiteral(h.removePrefix("*.")) != null ->
                    bad += Violation("E-PKG-07", "allowHosts 不允许 IP 字面量：$host")
            }
        }
        if (manifest.main.isBlank() || !isSafeEntryName(manifest.main)) {
            bad += Violation("E-PKG-08", "入口路径非法：${manifest.main}")
        } else if (scripts[manifest.main].isNullOrBlank()) {
            bad += Violation("E-PKG-08", "缺少入口脚本 ${manifest.main}")
        }
        manifest.baseUrls.forEach { (k, v) ->
            val okScheme = v.startsWith("https://") || v.startsWith("http://")
            if (!okScheme) bad += Violation("E-PKG-07", "baseUrls[$k] 必须是 http(s) URL：$v")
        }
        return bad
    }

    /** 拒绝绝对路径、`..`、反斜杠、以 `/` 结尾的目录条目。 */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains('\\')) return false
        if (name.endsWith("/")) return false
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        // Windows 保留名 / 设备名
        if (parts.any { it.matches(Regex("""(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?$""")) }) return false
        return true
    }
}
