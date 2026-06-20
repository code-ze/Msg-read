package com.example.smsspend.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val backStack by vm.backStack.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val current = backStack.last()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.refresh() }

    fun ensureAndRefresh() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.refresh() else permLauncher.launch(Manifest.permission.READ_SMS)
    }

    LaunchedEffect(Unit) { ensureAndRefresh() }

    BackHandler(enabled = backStack.size > 1) { vm.pop() }

    val title = when (current) {
        is Screen.Dashboard -> "SMS Spend"
        is Screen.Category -> current.name
        is Screen.Merchant -> current.name
        is Screen.Investments -> "Investments"
        is Screen.Settings -> "Settings"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (backStack.size > 1) {
                        IconButton(onClick = { vm.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (current is Screen.Dashboard) {
                        IconButton(onClick = {
                            copyToClipboard(context, "SMS Spend view", vm.currentViewSummary())
                            Toast.makeText(context, "Copied this view", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy this view")
                        }
                        if (loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 14.dp).size(20.dp)
                            )
                        } else {
                            IconButton(onClick = { ensureAndRefresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh from SMS")
                            }
                        }
                        IconButton(onClick = { vm.navigate(Screen.Investments) }) {
                            Icon(Icons.Default.ShowChart, contentDescription = "Investments")
                        }
                        IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = current) {
                is Screen.Dashboard -> DashboardScreen(vm)
                is Screen.Category -> CategoryDetailScreen(vm, s.name)
                is Screen.Merchant -> MerchantDetailScreen(vm, s.name)
                is Screen.Investments -> InvestmentsScreen(vm)
                is Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}

/** Copies text to the system clipboard so the user can paste it elsewhere (e.g. into an AI). */
fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
