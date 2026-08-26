package dev.ziga.iperf3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.ziga.iperf3.ui.AppRoot
import dev.ziga.iperf3.ui.Iperf3Theme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Iperf3Theme {
                AppRoot(viewModel)
            }
        }
    }
}
