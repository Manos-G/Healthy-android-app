package com.healthy.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.healthy.app.ui.data.DataScreen
import com.healthy.app.ui.morning.MorningScreen
import com.healthy.app.ui.theme.HealthyColors
import com.healthy.app.ui.today.TodayScreen
import com.healthy.app.ui.trends.TrendsScreen
import com.healthy.app.ui.weight.WeightScreen

/**
 * The tab shell. Trends and Data join it at steps 6 and 17.
 *
 * Navigation is a simple selected-index rather than a nav graph: there are no
 * arguments, no deep links yet and no back stack worth preserving between
 * tabs. Step 12's notification needs a deep link to a specific date, and that
 * is the point at which a real nav graph earns its cost.
 */
private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.LocalCafe),
    Morning("Morning", Icons.Filled.WbSunny),
    Weight("Weight", Icons.Filled.MonitorWeight),
    Trends("Trends", Icons.Filled.InsertChartOutlined),
    Data("Data", Icons.Filled.FolderOpen),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthyApp() {
    var tab by remember { mutableStateOf(Tab.Today) }
    val snackbars = remember { SnackbarHostState() }

    Scaffold(
        containerColor = HealthyColors.Ground,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.Today -> "Healthy"
                            Tab.Morning -> "Last night"
                            Tab.Weight -> "Weight"
                            Tab.Trends -> "Trends"
                            Tab.Data -> "Your data"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HealthyColors.Ground,
                    titleContentColor = HealthyColors.Paper,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = HealthyColors.Raised) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HealthyColors.Ground,
                            selectedTextColor = HealthyColors.Paper,
                            indicatorColor = HealthyColors.Caffeine,
                            unselectedIconColor = HealthyColors.Muted,
                            unselectedTextColor = HealthyColors.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.Today -> TodayScreen(
                snackbars = snackbars,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            Tab.Morning -> MorningScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            Tab.Weight -> WeightScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            Tab.Trends -> TrendsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            Tab.Data -> DataScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
