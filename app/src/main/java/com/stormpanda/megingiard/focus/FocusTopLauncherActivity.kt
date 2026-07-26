package com.stormpanda.megingiard.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stormpanda.megingiard.AppLog

private const val TAG = "FocusTopLauncherActivity"

class FocusTopLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i(TAG, "FocusTopLauncherActivity created on primary display")

        InstalledAppsManager.loadInstalledApps(this)

        setContent {
            val apps by InstalledAppsManager.installedApps.collectAsState()
            var searchQuery by remember { mutableStateOf("") }

            FocusTopLauncherScreen(
                apps = apps,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onRefresh = { InstalledAppsManager.loadInstalledApps(this) },
                onAppClick = { appInfo ->
                    AppLog.i(TAG, "Launching app from top launcher: ${appInfo.label}")
                    InstalledAppsManager.launchAppOnPrimaryDisplay(this, appInfo)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "FocusTopLauncherActivity resumed, refreshing installed apps")
        InstalledAppsManager.loadInstalledApps(this)
    }
}
