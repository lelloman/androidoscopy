package com.lelloman.androidoscopy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.ConnectionState
import com.lelloman.androidoscopy.ui.components.Section
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.theme.DashboardTheme
import com.lelloman.androidoscopy.ui.widgets.ChartDataStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Composable
fun DashboardScreen() {
    DashboardTheme {
        val scope = rememberCoroutineScope()
        val chartDataStores = remember { mutableStateMapOf<String, ChartDataStore>() }

        // Collect data from SDK
        val data by Androidoscopy.dataFlow.collectAsState()
        val logs by Androidoscopy.logFlow.collectAsState()
        val connectionState by Androidoscopy.connectionState.collectAsState()

        // Get dashboard schema
        val dashboardSchema = Androidoscopy.dashboardSchema?.jsonObject
        val sections = dashboardSchema?.get("sections")?.jsonArray ?: emptyList()
        val appName = Androidoscopy.appName ?: "Androidoscopy"

        Scaffold(
            containerColor = DashboardColors.Background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header
                DashboardHeader(
                    appName = appName,
                    connectionState = connectionState
                )

                // Content
                if (sections.isEmpty()) {
                    // No dashboard configured
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No dashboard configured",
                                style = MaterialTheme.typography.bodyLarge,
                                color = DashboardColors.TextSecondary
                            )
                            Text(
                                text = "Configure a dashboard in your Androidoscopy.init() call",
                                style = MaterialTheme.typography.bodySmall,
                                color = DashboardColors.TextMuted,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(sections) { sectionSchema ->
                            Section(
                                schema = sectionSchema.jsonObject,
                                data = data,
                                logs = logs,
                                chartDataStores = chartDataStores,
                                onAction = { action, args ->
                                    scope.launch {
                                        Androidoscopy.invokeAction(action, args)
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    appName: String,
    connectionState: ConnectionState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardColors.Surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                color = DashboardColors.TextPrimary
            )
            Text(
                text = "Androidoscopy Dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = DashboardColors.TextMuted
            )
        }

        // Connection status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionState) {
                            is ConnectionState.Connected -> DashboardColors.Success
                            is ConnectionState.Connecting -> DashboardColors.Warning
                            is ConnectionState.Error -> DashboardColors.Danger
                            ConnectionState.Disconnected -> DashboardColors.TextMuted
                        }
                    )
            )
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Error -> "Error"
                    ConnectionState.Disconnected -> "Disconnected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = DashboardColors.TextSecondary
            )
        }
    }
}
