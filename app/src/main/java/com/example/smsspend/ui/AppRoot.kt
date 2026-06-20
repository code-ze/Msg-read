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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Dashboard, "Home", Icons.Filled.Home),
    Tab(Screen.Transactions, "Activity", Icons.Filled.ReceiptLong),
    Tab(Screen.Analytics, "Analytics", Icons.Filled.PieChart),
    Tab(Screen.Insights, "Insights", Icons.Filled.Lightbulb),
    Tab(Screen.Investments, "Portfolio", Icons.Filled.ShowChart)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val backStack by vm.backStack.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val current = backStack.last()
    val root = backStack.first()
    val isTabRoot = backStack.size == 1 && tabs.any { it.screen == current }

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

    val title = when (val s = current) {
        is Screen.Dashboard -> "Overview"
        is Screen.Transactions -> "Activity"
        is Screen.Analytics -> "Analytics"
        is Screen.Insights -> "Insights & Action"
        is Screen.Investments -> "Portfolio"
        is Screen.Category -> s.name
        is Screen.Merchant -> s.name
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
                    if (isTabRoot) {
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
                        IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = root == tab.screen,
                        onClick = { vm.selectTab(tab.screen) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = current) {
                is Screen.Dashboard -> DashboardScreen(vm)
                is Screen.Transactions -> TransactionsScreen(vm)
                is Screen.Analytics -> AnalyticsScreen(vm)
                is Screen.Insights -> InsightsScreen(vm)
                is Screen.Investments -> InvestmentsScreen(vm)
                is Screen.Category -> CategoryDetailScreen(vm, s.name)
                is Screen.Merchant -> MerchantDetailScreen(vm, s.name)
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
