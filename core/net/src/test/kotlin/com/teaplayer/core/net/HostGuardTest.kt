package com.teaplayer.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用例编号对应 docs/PLAN.md 验收清单：
 * TC-SEARCH / RB-01（唯一出网口）、TC-PLG-05（allowHosts 不越界）、R-P0-3（内网扫描）。
 */
class HostGuardTest {

    private val allow = listOf("api.example.com", "*.cdn.example.com", "8.8.8.8")

    private fun v(url: String, hosts: List<String> = allow) = HostGuard.check(url, hosts)

    @Test
    fun `命中精确域名放行`() {
        assertEquals(HostGuard.Verdict.Ok, v("https://api.example.com/api.php/provide/vod/"))
        assertEquals(HostGuard.Verdict.Ok, v("http://api.example.com/x"))
    }

    @Test
    fun `通配只匹配子域 不含 apex 与相似域名`() {
        assertEquals(HostGuard.Verdict.Ok, v("https://a.cdn.example.com/v.m3u8"))
        assertEquals(HostGuard.Verdict.Ok, v("https://a.b.cdn.example.com/v.m3u8"))
        assertEquals(HostGuard.Verdict.NotAllowed, v("https://cdn.example.com/v.m3u8"))
        assertEquals(HostGuard.Verdict.NotAllowed, v("https://evilcdn.example.com/v.m3u8"))
        assertEquals(HostGuard.Verdict.NotAllowed, v("https://x.cdn.example.com.evil.tld/v.m3u8"))
    }

    @Test
    fun `非 http schema 一律拒`() {
        assertEquals(HostGuard.Verdict.BadScheme, v("file:///sdcard/a.m3u8"))
        assertEquals(HostGuard.Verdict.BadScheme, v("ftp://api.example.com/a.m3u8"))
        assertEquals(HostGuard.Verdict.BadScheme, v("content://x/y"))
        assertEquals(HostGuard.Verdict.BadScheme, v("javascript:alert(1)"))
    }

    @Test
    fun `带 userInfo 的 URL 拒掉`() {
        assertEquals(HostGuard.Verdict.Malformed, v("https://user:pw@api.example.com/x"))
    }

    @Test
    fun `内网与保留 IPv4 全部拦`() {
        val blocked = listOf(
            "http://127.0.0.1:8888/x",
            "http://127.1.2.3/",
            "http://10.0.0.5/",
            "http://172.16.0.1/",
            "http://172.31.255.255/",
            "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://0.0.0.0/",
            "http://100.64.1.1/",
            "http://224.0.0.1/",
            "http://255.255.255.255/",
        )
        blocked.forEach {
            val hit = v(it, listOf("*"))
            assertTrue("应拦截: $it", hit == HostGuard.Verdict.BlockedHost)
        }
    }

    @Test
    fun `公网 IPv4 字面量不因 IP 被判死 但仍要过 allowHosts`() {
        assertEquals(HostGuard.Verdict.Ok, v("http://8.8.8.8/x"))
        assertEquals(HostGuard.Verdict.NotAllowed, v("http://1.1.1.1/x"))
        assertFalse(HostGuard.isBlockedLiteral("1.1.1.1"))
    }

    @Test
    fun `IPv6 回环 链路本地 ULA 组播 映射地址全部拦`() {
        val blocked = listOf(
            "http://[::1]/x",
            "http://[::]/x",
            "http://[fe80::1]/x",
            "http://[fc00::1]/x",
            "http://[fd12:3456::1]/x",
            "http://[ff02::1]/x",
            "http://[::ffff:127.0.0.1]/x",
            "http://[::ffff:10.0.0.1]/x",
        )
        blocked.forEach {
            assertEquals("应拦截: $it", HostGuard.Verdict.BlockedHost, v(it, listOf("*")))
        }
    }

    @Test
    fun `公网 IPv6 不被误杀`() {
        assertFalse(HostGuard.isBlockedLiteral("2606:4700:3033::6815:1b2e"))
        assertFalse(HostGuard.isBlockedLiteral("2a03:2880::1"))
    }

    @Test
    fun `域名不做 DNS 解析 因此内网域名靠 allowHosts 兜底`() {
        // intranet 这种名字不是 IP 字面量，静态判不了；必须靠 allowHosts 白名单
        assertFalse(HostGuard.isBlockedLiteral("intranet"))
        assertEquals(HostGuard.Verdict.NotAllowed, v("http://intranet/x"))
    }

    @Test
    fun `空 allowHosts 等于什么都不放行`() {
        assertEquals(HostGuard.Verdict.NotAllowed, v("https://any.com/x", emptyList()))
    }

    @Test
    fun `畸形 URL 不崩`() {
        assertEquals(HostGuard.Verdict.Malformed, v("not a url"))
        assertEquals(HostGuard.Verdict.EmptyHost, v("https:///path"))
    }
}
