package com.teaplayer.core.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-PLG-01..04（安装链路）：包结构、sha256、allowHosts、幂等与升级。
 * 用例码见 docs/PLAN-v3-plugins.md §7。
 */
class PluginInstallerTest {

    private val goodManifest = """
        {
          "id": "bfzy",
          "name": "暴风资源",
          "version": "1.4.0",
          "hostApi": 1,
          "main": "main.js",
          "allowHosts": ["api.bfzy.com", "*.bfzydns.com"],
          "baseUrls": {"api": "https://api.bfzy.com/api.php/provide/vod/"}
        }
    """.trimIndent()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun pkg(manifest: String = goodManifest, main: String = "function search(){return []}") =
        zip("manifest.json" to manifest, "main.js" to main)

    private fun codes(outcome: InstallOutcome): List<String> =
        (outcome as InstallOutcome.Fail).violations.map { it.code }

    @Test
    fun `合法包安装成功 且记下 sha256`() {
        val bytes = pkg()
        val reg = PluginRegistry()
        val out = PluginInstaller(reg).install(bytes, InstallSource.LocalFile, PluginPackage.sha256(bytes))
        assertTrue(out is InstallOutcome.Ok)
        val p = (out as InstallOutcome.Ok).plugin
        assertEquals("bfzy", p.manifest.id)
        assertEquals(PluginPackage.sha256(bytes), p.sha256)
        assertTrue(p.enabled)
        assertEquals(1, reg.enabledCount())
    }

    @Test
    fun `sha256 不符直接拒`() {
        val bytes = pkg()
        val out = PluginInstaller(PluginRegistry())
            .install(bytes, InstallSource.Http("https://x/y.tpkg"), expectedSha256 = "00".repeat(32))
        assertEquals(listOf("E-SIG-01"), codes(out))
    }

    @Test
    fun `没有期望摘要时只做结构校验`() {
        val out = PluginInstaller(PluginRegistry()).install(pkg(), InstallSource.LocalFile, null)
        assertTrue(out is InstallOutcome.Ok)
    }

    @Test
    fun `不是 zip 的文件拒掉`() {
        val out = PluginInstaller(PluginRegistry()).install("hello".toByteArray(), InstallSource.LocalFile)
        assertEquals(listOf("E-PKG-01"), codes(out))
    }

    @Test
    fun `缺 manifest 或入口脚本都拒`() {
        assertEquals(listOf("E-PKG-02"), codes(PluginInstaller(PluginRegistry()).install(zip("main.js" to "x"), InstallSource.LocalFile)))
        val noMain = zip("manifest.json" to goodManifest)
        assertEquals(listOf("E-PKG-08"), codes(PluginInstaller(PluginRegistry()).install(noMain, InstallSource.LocalFile)))
    }

    @Test
    fun `allowHosts 通配全部 与 IP 字面量都拒`() {
        val star = goodManifest.replace("""["api.bfzy.com", "*.bfzydns.com"]""", """["*"]""")
        assertEquals(listOf("E-PKG-07"), codes(PluginInstaller(PluginRegistry()).install(pkg(star), InstallSource.LocalFile)))

        val ip = goodManifest.replace("""["api.bfzy.com", "*.bfzydns.com"]""", """["192.168.1.1"]""")
        assertEquals(listOf("E-PKG-07"), codes(PluginInstaller(PluginRegistry()).install(pkg(ip), InstallSource.LocalFile)))
    }

    @Test
    fun `空 allowHosts 拒 因为没域名等于不能用`() {
        val empty = goodManifest.replace("""["api.bfzy.com", "*.bfzydns.com"]""", "[]")
        assertEquals(listOf("E-PKG-07"), codes(PluginInstaller(PluginRegistry()).install(pkg(empty), InstallSource.LocalFile)))
    }

    @Test
    fun `hostApi 版本不匹配拒`() {
        val v2 = goodManifest.replace("\"hostApi\": 1", "\"hostApi\": 2")
        assertEquals(listOf("E-PKG-06"), codes(PluginInstaller(PluginRegistry()).install(pkg(v2), InstallSource.LocalFile)))
    }

    @Test
    fun `清单字段非法 id version 各自报码`() {
        val badId = goodManifest.replace("\"bfzy\"", "\"BF_ZY\"")
        assertEquals(listOf("E-PKG-04"), codes(PluginInstaller(PluginRegistry()).install(pkg(badId), InstallSource.LocalFile)))
        val badVer = goodManifest.replace("\"1.4.0\"", "\"1.4\"")
        assertEquals(listOf("E-PKG-05"), codes(PluginInstaller(PluginRegistry()).install(pkg(badVer), InstallSource.LocalFile)))
    }

    @Test
    fun `路径穿越条目拒`() {
        val zip = zip(
            "manifest.json" to goodManifest,
            "main.js" to "x",
            "../../etc/passwd" to "root",
        )
        assertEquals(listOf("E-PKG-09"), codes(PluginInstaller(PluginRegistry()).install(zip, InstallSource.LocalFile)))
    }

    @Test
    fun `同版本同 hash 幂等 不产生第二条`() {
        val bytes = pkg()
        val reg = PluginRegistry()
        val inst = PluginInstaller(reg)
        val sha = PluginPackage.sha256(bytes)
        inst.install(bytes, InstallSource.Seed, sha)
        inst.install(bytes, InstallSource.Market("idx"), sha)
        assertEquals(1, reg.all().size)
    }

    @Test
    fun `升级替换 但保留用户的启用状态`() {
        val reg = PluginRegistry()
        val inst = PluginInstaller(reg)
        inst.install(pkg(), InstallSource.Seed)
        reg.setEnabled("bfzy", false)
        val v2 = goodManifest.replace("\"1.4.0\"", "\"1.5.0\"")
        val out = inst.install(pkg(v2), InstallSource.Market("idx"))
        val p = (out as InstallOutcome.Ok).plugin
        assertEquals("1.5.0", p.manifest.version)
        assertTrue("升级不应把用户手动停用的插件重新打开", !p.enabled)
    }

    @Test
    fun `远端动作不能改 enabled`() {
        val reg = PluginRegistry()
        val inst = PluginInstaller(reg)
        inst.install(pkg(), InstallSource.Seed)
        // 只有 setEnabled 能改；install 同版本同 hash 时原样返回
        val same = pkg()
        inst.install(same, InstallSource.Market("idx"), PluginPackage.sha256(same))
        assertNotNull(reg.get("bfzy"))
        assertTrue(reg.get("bfzy")!!.enabled)
        reg.remove("bfzy")
        assertNull(reg.get("bfzy"))
    }
}
