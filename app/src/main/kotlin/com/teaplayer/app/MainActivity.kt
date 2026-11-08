package com.teaplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.teaplayer.core.data.MacCmsSource
import com.teaplayer.core.designsystem.TeaPlayerTheme
import com.teaplayer.core.net.SafeHttpClient
import com.teaplayer.feature.ResultListScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // P1 阶段还没有 DI 容器（Hilt 未接），先在入口手工装配唯一出网口 + 第一个种子源。
        val allowHosts = listOf("bfzyapi.com", "*.bfzyapi.com")
        val http = SafeHttpClient(
            allowHosts = { allowHosts },
            // 明文 http 需要「用户显式确认过」才算数（D5′），这里没有任何确认记录
            cleartextAcknowledged = { emptySet() },
        )
        val source = MacCmsSource(
            id = "bfzy",
            displayName = "暴风资源",
            apiBase = "https://bfzyapi.com/api.php/provide/vod/",
            http = http,
            extraHeaders = mapOf("User-Agent" to "TeaPlayer/0.1 (Android)"),
        )

        setContent {
            TeaPlayerTheme {
                ResultListScreen(source = source)
            }
        }
    }
}
