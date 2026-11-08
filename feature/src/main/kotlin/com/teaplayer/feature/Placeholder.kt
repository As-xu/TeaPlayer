package com.teaplayer.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.teaplayer.core.designsystem.TpColor
import com.teaplayer.core.designsystem.TpType

/** 占位：PH-1 首页将于 P1 首批实现（见 docs/ux-prototype 与 docs/ux/IA.md）。 */
@Composable
fun Placeholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TeaPlayer", color = TpColor.Tx, style = TpType.titleL)
    }
}
