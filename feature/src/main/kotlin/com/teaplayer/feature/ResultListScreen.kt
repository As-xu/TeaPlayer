package com.teaplayer.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teaplayer.core.designsystem.TpColor
import com.teaplayer.core.designsystem.TpRadius
import com.teaplayer.core.designsystem.TpSpace
import com.teaplayer.core.designsystem.TpType
import com.teaplayer.core.domain.SearchableSource
import com.teaplayer.core.domain.SourceResult
import com.teaplayer.core.model.CatalogItem
import com.teaplayer.core.model.SourceError

/** PH-3 结果列表的最小可用版：真实源 + 真实错误态，不是假数据。 */
sealed interface ListState {
    data object Loading : ListState
    data class Loaded(val items: List<CatalogItem>) : ListState
    data class Failed(val error: SourceError) : ListState
}

@Composable
fun ResultListScreen(
    source: SearchableSource,
    keyword: String? = null,
) {
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }

    LaunchedEffect(source.id, keyword) {
        state = ListState.Loading
        state = when (val r = if (keyword.isNullOrBlank()) source.browse() else source.search(keyword)) {
            is SourceResult.Ok -> ListState.Loaded(r.value)
            is SourceResult.Err -> ListState.Failed(r.error)
        }
    }

    Column(Modifier.fillMaxSize().background(TpColor.Bg)) {
        TopBar(title = source.displayName, subtitle = keyword?.let { "搜索「$it」" } ?: "全部栏目")
        when (val s = state) {
            ListState.Loading -> Center { CircularProgressIndicator(color = TpColor.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
            is ListState.Failed -> Center {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TpSpace.s2)) {
                    Text(errorTitle(s.error), style = TpType.titleS, color = TpColor.Tx)
                    Text(errorDetail(s.error), style = TpType.bodyS, color = TpColor.Tx2)
                    Text("码 ${s.error.code}", style = TpType.caption, color = TpColor.Tx3)
                }
            }
            is ListState.Loaded -> if (s.items.isEmpty()) {
                Center { Text("这个源没有返回条目", style = TpType.body, color = TpColor.Tx2) }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = TpSpace.s4, vertical = TpSpace.s2,
                    ),
                ) {
                    items(s.items, key = { it.sourceId + "/" + it.sourceItemId }) { item -> ItemRow(item) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TpSpace.s4, vertical = TpSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TpType.titleL, color = TpColor.Tx)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = TpType.caption, color = TpColor.Tx3)
        }
        Box(
            Modifier.background(TpColor.Surface2, RoundedCornerShape(TpRadius.pill))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text("1 个源", style = TpType.caption, color = TpColor.Tx2) }
    }
}

@Composable
private fun ItemRow(item: CatalogItem) {
    Row(
        Modifier.fillMaxWidth()
            .background(TpColor.Surface, RoundedCornerShape(TpRadius.r2))
            .padding(TpSpace.s3)
            .padding(bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面位先不放图（P1 后段接 Coil），用色块占位保持行高一致
        Box(
            Modifier.size(width = 44.dp, height = 60.dp)
                .background(TpColor.Surface3, RoundedCornerShape(TpRadius.r1)),
        )
        Spacer(Modifier.size(TpSpace.s3))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = TpType.titleS, color = TpColor.Tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val meta = listOfNotNull(item.year, item.category, item.remark).joinToString(" · ")
            Text(meta.ifBlank { "—" }, style = TpType.caption, color = TpColor.Tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(TpSpace.s2))
        Row(
            Modifier.background(TpColor.AccentSoft, RoundedCornerShape(TpRadius.pill))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) { Text("可播", style = TpType.caption, color = TpColor.Accent) }
    }
    Spacer(Modifier.height(TpSpace.s2))
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 文案对齐 docs/design/copy-errors.md A 段；这里只覆盖本轮会出现的几条。 */
private fun errorTitle(e: SourceError): String = when (e) {
    is SourceError.Timeout -> "源站没有响应"
    is SourceError.Network -> "网络断了"
    is SourceError.Dns -> "找不到源站"
    is SourceError.Http -> "源站拒绝请求"
    is SourceError.Blocked -> "这个域名不在允许清单"
    is SourceError.TooLarge -> "返回内容过大"
    is SourceError.Parse -> "源站返回看不懂的数据"
    is SourceError.Empty -> "源站没有内容"
    else -> "这个源暂时用不了"
}

private fun errorDetail(e: SourceError): String = when (e) {
    is SourceError.Timeout -> "超过 2.5 秒没有回应，稍后重试或换个源"
    is SourceError.Network -> "检查 Wi-Fi 或移动数据后重试"
    is SourceError.Dns -> "域名可能已经换掉了"
    is SourceError.Http -> "服务器返回 ${e.status}，可能是临时故障"
    is SourceError.Blocked -> "插件没有声明这个域名，或它指向内网"
    else -> "换个源试试"
}
