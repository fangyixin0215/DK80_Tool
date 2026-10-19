package com.dk80.tool

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dk80.tool.ble.BleManager
import com.dk80.tool.ui.components.ConnectionBar
import com.dk80.tool.ui.screens.DashboardScreen
import com.dk80.tool.ui.screens.LogScreen
import com.dk80.tool.ui.screens.OtaScreen
import com.dk80.tool.ui.screens.RemoteScreen
import com.dk80.tool.ui.theme.DK80Theme

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleManager

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = BleManager.get(this)
        ensurePermissions()
        setContent { DK80Theme { App(ble) } }
    }

    private fun ensurePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("remote", "遥控", Icons.Filled.SettingsRemote),
    Tab("dashboard", "工况", Icons.Filled.Info),
    Tab("log", "日志", Icons.Filled.List),
    Tab("ota", "OTA", Icons.Filled.SystemUpdate)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(ble: BleManager) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route ?: tabs[0].route
    val state by ble.state.collectAsState()
    val name by ble.deviceName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DK80 Tool") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = androidx.compose.ui.graphics.Color.White)
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo(tabs[0].route); launchSingleTop = true } },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(12.dp))
            ConnectionBar(
                state = state, deviceName = name,
                onScan = { ble.startScan() },
                onDisconnect = { ble.disconnect() }
            )
            NavHost(nav, startDestination = tabs[0].route, Modifier.fillMaxSize()) {
                composable("remote") { RemoteScreen(ble) }
                composable("dashboard") { DashboardScreen(ble) }
                composable("log") { LogScreen(ble) }
                composable("ota") { OtaScreen(ble) }
            }
        }
    }
}
