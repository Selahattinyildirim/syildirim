package com.sarjdefteri.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sarjdefteri.app.AppContainer
import com.sarjdefteri.app.ui.campaigns.CampaignEditScreen
import com.sarjdefteri.app.ui.campaigns.CampaignEditViewModel
import com.sarjdefteri.app.ui.campaigns.CampaignsScreen
import com.sarjdefteri.app.ui.campaigns.CampaignsViewModel
import com.sarjdefteri.app.ui.dashboard.DashboardScreen
import com.sarjdefteri.app.ui.detail.DetailScreen
import com.sarjdefteri.app.ui.entry.EntryScreen
import com.sarjdefteri.app.ui.entry.EntryViewModel
import com.sarjdefteri.app.ui.history.HistoryScreen
import com.sarjdefteri.app.ui.odometer.OdometerScreen
import com.sarjdefteri.app.ui.pro.ProScreen
import com.sarjdefteri.app.ui.pro.ProViewModel
import com.sarjdefteri.app.ui.settings.SettingsScreen
import com.sarjdefteri.app.ui.settings.SettingsViewModel
import com.sarjdefteri.app.ui.tesla.TeslaScreen
import com.sarjdefteri.app.ui.tesla.TeslaViewModel
import com.sarjdefteri.app.ui.theme.TeslaRed

object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val TESLA = "tesla"
    const val ODOMETER = "odometer"
    const val PRO = "pro"
    const val CAMPAIGNS = "campaigns"
    const val CAMPAIGN_NEW = "campaign/new"
    const val CAMPAIGN_EDIT = "campaign/edit/{id}"
    fun campaign(id: Long) = "campaign/edit/$id"
    const val ENTRY_NEW = "entry/new"
    const val ENTRY_EDIT = "entry/edit/{id}"
    const val DETAIL = "detail/{id}"
    fun edit(id: Long) = "entry/edit/$id"
    fun detail(id: Long) = "detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Panel", Icons.Default.Dashboard),
    Tab(Routes.HISTORY, "Geçmiş", Icons.Default.History),
    Tab(Routes.CAMPAIGNS, "Kampanya", Icons.Default.LocalOffer),
    Tab(Routes.SETTINGS, "Ayarlar", Icons.Default.Settings),
)

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val pendingRoute by container.pendingRoute.collectAsState()

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            container.pendingRoute.value = null
            nav.navigate(route) { launchSingleTop = true }
        }
    }

    val isTab = tabs.any { it.route == current }
    Scaffold(
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        },
        floatingActionButton = {
            if (current == Routes.CAMPAIGNS) {
                FloatingActionButton(onClick = { nav.navigate(Routes.CAMPAIGN_NEW) }, containerColor = TeslaRed,
                    contentColor = MaterialTheme.colorScheme.onSecondary) { Icon(Icons.Default.Add, "Kampanya ekle") }
            }
            if (current == Routes.DASHBOARD || current == Routes.HISTORY) {
                FloatingActionButton(onClick = { nav.navigate(Routes.ENTRY_NEW) }, containerColor = TeslaRed,
                    contentColor = MaterialTheme.colorScheme.onSecondary) { Icon(Icons.Default.Add, "Şarj ekle") }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.DASHBOARD) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(appViewModel { DashboardViewModel(it) }, padding,
                    onAdd = { nav.navigate(Routes.ENTRY_NEW) }, onOdometer = { nav.navigate(Routes.ODOMETER) },
                    onPro = { nav.navigate(Routes.PRO) }, onCampaigns = { nav.navigate(Routes.CAMPAIGNS) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } })
            }
            composable(Routes.CAMPAIGNS) {
                CampaignsScreen(appViewModel { CampaignsViewModel(it) }, padding, onEdit = { id ->
                    nav.navigate(if (id == null) Routes.CAMPAIGN_NEW else Routes.campaign(id)) })
            }
            composable(Routes.CAMPAIGN_NEW) { entry ->
                CampaignEditScreen(appViewModel(key = "cnew-${entry.id}") { CampaignEditViewModel(it, null) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CAMPAIGN_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                CampaignEditScreen(appViewModel(key = "cedit-$id") { CampaignEditViewModel(it, id) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(appViewModel { HistoryViewModel(it) }, padding, onOpen = { nav.navigate(Routes.detail(it)) },
                    onPro = { nav.navigate(Routes.PRO) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(appViewModel { SettingsViewModel(it) }, padding, onTesla = { nav.navigate(Routes.TESLA) },
                    onPro = { nav.navigate(Routes.PRO) })
            }
            composable(Routes.TESLA) {
                Wrapper(padding) { TeslaScreen(appViewModel { TeslaViewModel(it) }, onBack = { nav.popBackStack() }) }
            }
            composable(Routes.PRO) {
                Wrapper(padding) { ProScreen(appViewModel { ProViewModel(it) }, onBack = { nav.popBackStack() }) }
            }
            composable(Routes.ODOMETER) {
                Wrapper(padding) { OdometerScreen(appViewModel { OdometerViewModel(it) }, onBack = { nav.popBackStack() }) }
            }
            composable(Routes.ENTRY_NEW) { entry ->
                Wrapper(padding) {
                    EntryScreen(appViewModel(key = "new-${entry.id}") { EntryViewModel(it, null) }, onBack = { nav.popBackStack() },
                        onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.detail(id)) })
                }
            }
            composable(Routes.ENTRY_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                Wrapper(padding) {
                    EntryScreen(appViewModel(key = "edit-$id") { EntryViewModel(it, id) }, onBack = { nav.popBackStack() },
                        onSaved = { nav.popBackStack() })
                }
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                Wrapper(padding) {
                    DetailScreen(appViewModel(key = "detail-$id") { DetailViewModel(it, id) }, onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Routes.edit(it)) })
                }
            }
        }
    }
}

/** Alt çubuğu olmayan ekranlar kendi Scaffold'larını kullanır; dış padding yalnızca yok sayılır. */
@Composable
private fun Wrapper(@Suppress("UNUSED_PARAMETER") padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable () -> Unit) {
    content()
}
