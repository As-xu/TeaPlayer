package com.teaplayer.core.model

/**
 * 统一的源错误分类。**全工程只此处定义一份**（v3 §3.4）。
 * 用户可见文案见 docs/design/copy-errors.md，两边由 CI 断言一一对应。
 */
sealed class SourceError(val code: String) {
    data object Network : SourceError("E-NET-00")
    data object Timeout : SourceError("E-NET-01")
    data class Http(val status: Int) : SourceError("E-NET-02")
    data object Dns : SourceError("E-NET-03")
    data class Blocked(val reason: String) : SourceError("E-NET-06")
    data object TooLarge : SourceError("E-NET-07")
    data object Parse : SourceError("E-NET-04")
    data object Empty : SourceError("E-NET-11")
    data object Unsupported : SourceError("E-NET-12")
    data class PluginFault(val pluginId: String, val detail: String) : SourceError("E-HOOK-02")
    data class Disabled(val pluginId: String) : SourceError("E-MKT-01")

    /** 是否值得原样重试（超时/网络抖动），false 表示重试也无用。 */
    val retryable: Boolean
        get() = this is Timeout || this is Network
}
