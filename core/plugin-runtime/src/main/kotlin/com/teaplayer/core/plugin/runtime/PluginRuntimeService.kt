package com.teaplayer.core.plugin.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 插件执行进程的宿主服务（P2 起用 Binder 实现 PluginRuntimePort）。
 * 注意：本模块**禁止**依赖 :core:data / :core:player（Konsist 断言），
 * 否则 :plugin 进程会顺带初始化 SimpleCache（R-P0-1）。
 */
class PluginRuntimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
