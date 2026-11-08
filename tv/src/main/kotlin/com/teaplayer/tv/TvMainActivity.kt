package com.teaplayer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.teaplayer.core.designsystem.TeaPlayerTheme
import com.teaplayer.feature.Placeholder

class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TeaPlayerTheme { Placeholder() } }
    }
}
