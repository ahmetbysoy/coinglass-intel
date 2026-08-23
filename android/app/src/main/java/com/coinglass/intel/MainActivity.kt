package com.coinglass.intel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.coinglass.intel.ui.IntelScreen
import com.coinglass.intel.ui.IntelViewModel
import com.coinglass.intel.ui.theme.Bg
import com.coinglass.intel.ui.theme.CoinGlassTheme

class MainActivity : ComponentActivity() {
    private val vm: IntelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoinGlassTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    IntelScreen(vm)
                }
            }
        }
    }
}
