package dev.ziga.iperf3.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ziga.iperf3.MainViewModel
import dev.ziga.iperf3.RunPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val start = rememberStartAction { viewModel.start() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == 0) "iperf3" else "History") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Test") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("History") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> if (engine.phase == RunPhase.IDLE) {
                    ConfigScreen(
                        config = config,
                        profiles = profiles,
                        onChange = viewModel::update,
                        onApplyProfile = viewModel::applyConfig,
                        onSaveProfile = viewModel::saveProfile,
                        onDeleteProfile = viewModel::deleteProfile,
                        onStart = start,
                    )
                } else {
                    RunScreen(
                        state = engine,
                        onStop = viewModel::stop,
                        onDone = viewModel::reset,
                        onRunAgain = { viewModel.reset(); start() },
                    )
                }
                else -> HistoryScreen(
                    records = history,
                    onRerun = { record -> viewModel.applyConfig(record.config); tab = 0 },
                    onDelete = viewModel::deleteRun,
                    onClear = viewModel::clearHistory,
                )
            }
        }
    }
}

/**
 * Asks for the notification permission the first time, then starts the test.
 * The run itself does not depend on the grant — only the live progress
 * notification does — so the test starts either way.
 */
@Composable
private fun rememberStartAction(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStart() }
    return remember(context, launcher) {
        {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onStart()
        }
    }
}
