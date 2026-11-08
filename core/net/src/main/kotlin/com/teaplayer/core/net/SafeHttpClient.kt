package com.teaplayer.core.net

import com.teaplayer.core.model.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 全工程**唯一出网口**（v1 安全红线 RB-1，v3 §4.5）。
 *
 * 三条不可绕过的规则：
 * 1. scheme 只允许 http/https，host 必须命中 allowHosts 且不是内网 IP 字面量；
 * 2. 重定向自己跟（followRedirects=false），每跳都重新过 [HostGuard]——3xx → 内网是经典绕过；
 * 3. 按 body 类型限长（播放列表 2 MB / key 1 KB / 分片 64 MB），超限立即断流，判 [SourceError.TooLarge]。
 *
 * 播放链路上它同时是 ExoPlayer 的 DataSource 底座：playlist、分片、AES key 走同一条出网约束。
 */
class SafeHttpClient internal constructor(
    private val allowHosts: () -> List<String>,
    /** 已由用户显式确认过是明文 http 的 host（D5′ 双条件拦截的第二个条件）。 */
    private val cleartextAcknowledged: () -> Set<String>,
    private val client: OkHttpClient,
) {

    /** 公开入口**不暴露 OkHttp 类型**，调用方（app/tv/feature）不必依赖 okhttp。 */
    constructor(
        allowHosts: () -> List<String>,
        cleartextAcknowledged: () -> Set<String> = { emptySet() },
    ) : this(allowHosts, cleartextAcknowledged, defaultClient())

    enum class Kind(val maxBytes: Long) {
        Playlist(2L * 1024 * 1024),
        Key(1024L),
        Segment(64L * 1024 * 1024),
        Json(4L * 1024 * 1024),
        Generic(8L * 1024 * 1024),
    }

    data class Result(
        val status: Int,
        val finalUrl: String,
        val contentType: String?,
        val bytes: ByteArray,
        val truncated: Boolean,
    )

    sealed class Outcome {
        data class Ok(val value: Result) : Outcome()
        data class Fail(val error: SourceError) : Outcome()
    }

    suspend fun get(
        url: String,
        kind: Kind,
        headers: Map<String, String> = emptyMap(),
        rangeStart: Long? = null,
    ): Outcome = request(url, kind, headers, rangeStart)

    private suspend fun request(
        url: String,
        kind: Kind,
        headers: Map<String, String>,
        rangeStart: Long?,
    ): Outcome = withContext<Outcome>(Dispatchers.IO) { fetchLoop(url, kind, headers, rangeStart) }

    /** 阻塞实现，只在 Dispatchers.IO 上跑。302 自己跟：每跳重新过 guard。 */
    private fun fetchLoop(
        url: String,
        kind: Kind,
        headers: Map<String, String>,
        rangeStart: Long?,
    ): Outcome {
        var current = url
        var hop = 0
        while (true) {
            when (guard(current)) {
                HostGuard.Verdict.Ok -> Unit
                HostGuard.Verdict.BadScheme -> return Outcome.Fail(SourceError.Blocked("bad scheme"))
                HostGuard.Verdict.BlockedHost -> return Outcome.Fail(SourceError.Blocked("private or reserved address"))
                HostGuard.Verdict.NotAllowed -> return Outcome.Fail(SourceError.Blocked("host not in allowHosts"))
                HostGuard.Verdict.Malformed -> return Outcome.Fail(SourceError.Parse)
                HostGuard.Verdict.EmptyHost -> return Outcome.Fail(SourceError.Parse)
            }

            val reqBuilder = Request.Builder().url(current)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            rangeStart?.let { reqBuilder.header("Range", "bytes=$it-") }

            val resp: Response = try {
                client.newCall(reqBuilder.build()).execute()
            } catch (e: SocketTimeoutException) {
                return Outcome.Fail(SourceError.Timeout)
            } catch (e: InterruptedIOException) {
                return Outcome.Fail(SourceError.Timeout)
            } catch (e: java.net.UnknownHostException) {
                return Outcome.Fail(SourceError.Dns)
            } catch (e: IOException) {
                return Outcome.Fail(SourceError.Network)
            }

            val code = resp.code
            if (code in 300..399) {
                val location = resp.header("Location")
                resp.close()
                if (location == null) return Outcome.Fail(SourceError.Http(code))
                hop += 1
                if (hop > MAX_REDIRECTS) return Outcome.Fail(SourceError.Blocked("too many redirects"))
                current = resolve(current, location)
                continue // 下一跳重新过 guard
            }

            return resp.use {
                if (code !in 200..299 && code != 206) {
                    Outcome.Fail(SourceError.Http(code))
                } else {
                    val declared = it.body?.contentLength() ?: -1L
                    if (declared > kind.maxBytes) {
                        Outcome.Fail(SourceError.TooLarge)
                    } else {
                        val (bytes, truncated) = readCapped(it, kind.maxBytes)
                        Outcome.Ok(
                            Result(
                                status = code,
                                finalUrl = current,
                                contentType = it.header("Content-Type"),
                                bytes = bytes,
                                truncated = truncated,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** 读流时也截断：contentLength 常缺失（chunked），不能只信声明长度。 */
    private fun readCapped(resp: Response, cap: Long): Pair<ByteArray, Boolean> {
        val body = resp.body ?: return ByteArray(0) to false
        val out = java.io.ByteArrayOutputStream(minOf(cap, 64 * 1024).toInt())
        val buf = ByteArray(16 * 1024)
        var total = 0L
        body.byteStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val room = cap - total
                if (room <= 0) return out.toByteArray() to true
                val take = minOf(n.toLong(), room).toInt()
                out.write(buf, 0, take)
                total += take
                if (take < n) return out.toByteArray() to true
            }
        }
        return out.toByteArray() to false
    }

    private fun guard(url: String): HostGuard.Verdict {
        val verdict = HostGuard.check(url, allowHosts())
        if (verdict != HostGuard.Verdict.Ok) return verdict
        // D5′：明文 http 还要命中「用户已确认」集合
        val isHttp = url.trim().startsWith("http://", ignoreCase = true)
        if (isHttp) {
            val host = runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull()
                ?: return HostGuard.Verdict.Malformed
            if (host !in cleartextAcknowledged()) return HostGuard.Verdict.BadScheme
        }
        return HostGuard.Verdict.Ok
    }

    companion object {
        const val MAX_REDIRECTS = 5

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

/** 相对 Location 解析（okhttp 的 HttpUrl 能处理，但这里保持零依赖便于单测）。 */
private fun resolve(base: String, location: String): String = runCatching {
    java.net.URI(base).resolve(java.net.URI(location)).toString()
}.getOrElse { location }
