package com.teaplayer.core.net

import java.net.InetAddress
import java.net.URI

/**
 * 出网前的静态校验：scheme、IP 字面量、私网/回环/链路本地/组播、allowHosts 匹配。
 *
 * 纯逻辑、不触网——IP 字面量解析不走 DNS。
 * 之所以要自己判：插件可以把 downloadUrl / m3u8 写成 `http://127.0.0.1:8888` 或
 * `http://192.168.1.1/`，那就是把宿主当端口扫描器用（PLAN-v2 R-P0-3）。
 */
object HostGuard {

    enum class Verdict { Ok, Malformed, BadScheme, BlockedHost, NotAllowed, EmptyHost }

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun check(rawUrl: String, allowHosts: List<String>): Verdict {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return Verdict.Malformed
        val scheme = uri.scheme?.lowercase() ?: return Verdict.Malformed
        if (scheme != "http" && scheme != "https") return Verdict.BadScheme
        // http://user:pass@host/ 一律拒——userInfo 会被 okhttp 当认证头带出去
        if (!uri.userInfo.isNullOrEmpty()) return Verdict.Malformed
        val host = uri.host?.lowercase()?.removeSuffix(".") ?: return Verdict.EmptyHost
        if (host.isEmpty()) return Verdict.EmptyHost
        if (isBlockedLiteral(host)) return Verdict.BlockedHost
        if (!hostAllowed(host, allowHosts)) return Verdict.NotAllowed
        return Verdict.Ok
    }

    /** 主机是 IP 字面量且落在不可达/内网/组播区间。域名一律返回 false（域名由 allowHosts 管）。 */
    fun isBlockedLiteral(host: String): Boolean {
        val addr = parseLiteral(host) ?: return false
        return isBlockedAddress(addr)
    }

    fun parseLiteral(host: String): InetAddress? {
        val v4 = IPV4.matches(host)
        val v6 = host.contains(':')
        if (!v4 && !v6) return null
        return runCatching { InetAddress.getByName(host) }.getOrNull()
    }

    fun isBlockedAddress(addr: InetAddress): Boolean {
        val b = addr.address
        if (b.size == 4) return isBlockedV4(b)
        if (isV4Mapped(b)) return isBlockedV4(b.copyOfRange(12, 16))
        return isBlockedV6(b)
    }

    private fun isBlockedV4(b: ByteArray): Boolean {
        val o0 = b[0].toInt() and 0xff
        val o1 = b[1].toInt() and 0xff
        return when {
            o0 == 0 -> true                                   // 0.0.0.0/8
            o0 == 10 -> true                                  // 10/8
            o0 == 127 -> true                                 // loopback
            o0 == 169 && o1 == 254 -> true                    // link-local
            o0 == 172 && o1 in 16..31 -> true                 // 172.16/12
            o0 == 192 && o1 == 168 -> true                    // 192.168/16
            o0 == 100 && o1 in 64..127 -> true                // CGNAT 100.64/10
            o0 >= 224 -> true                                 // 组播 + 保留 + 广播
            else -> false
        }
    }

    private fun isBlockedV6(b: ByteArray): Boolean {
        val allZero = b.all { it.toInt() == 0 }
        val isLoop = allZero || (b.copyOfRange(0, 15).all { it.toInt() == 0 } && b[15].toInt() == 1)
        if (isLoop) return true
        val b0 = b[0].toInt() and 0xff
        val b1 = b[1].toInt() and 0xff
        return when {
            b0 == 0xfe && (b1 and 0xc0) == 0x80 -> true        // fe80::/10 link-local
            (b0 and 0xfe) == 0xfc -> true                     // fc00::/7 unique-local
            b0 == 0xff -> true                                // ff00::/8 组播
            else -> false
        }
    }

    private fun isV4Mapped(b: ByteArray): Boolean {
        if (b.size != 16) return false
        for (i in 0..9) if (b[i].toInt() != 0) return false
        return b[10].toInt() == 0xff && b[11].toInt() == 0xff
    }

    /**
     * allowHosts 匹配：精确，或 `*.suffix`（只允许最左一整段通配）。
     * `*.x.com` 不匹配 `x.com`（apex 要单独写），也不匹配 `evilx.com`。
     */
    fun hostAllowed(host: String, allowHosts: List<String>): Boolean {
        val h = host.lowercase().removeSuffix(".")
        return allowHosts.any { rule ->
            val r = rule.trim().lowercase().removeSuffix(".")
            when {
                r.isEmpty() -> false
                r == h -> true
                r.startsWith("*.") -> {
                    val base = r.substring(2)
                    base.isNotEmpty() && h.endsWith(".$base")
                }
                else -> false
            }
        }
    }
}
