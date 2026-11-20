package com.syildirim.teslacharge

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.syildirim.teslacharge.reminders.EntryPrefill
import com.syildirim.teslacharge.reminders.Notifications
import com.syildirim.teslacharge.ui.AppNav
import com.syildirim.teslacharge.ui.Routes
import com.syildirim.teslacharge.ui.theme.TeslaChargeTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as TeslaChargeApp).container

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        maybeAskNotificationPermission()
        setContent { TeslaChargeTheme { AppNav(container) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = intent.data
        if (data != null && data.scheme == "teslachargetracker" && data.host == "oauth") {
            container.pendingOAuthRedirect.value = data.toString()
            container.pendingRoute.value = Routes.TESLA
            return
        }
        EntryPrefill.from(intent)?.let {
            container.pendingPrefill.value = it
            container.pendingRoute.value = Routes.ENTRY_NEW
            intent.removeExtra(EntryPrefill.EXTRA_ROUTE)
            return
        }
        if (intent.getStringExtra(EntryPrefill.EXTRA_ROUTE) == EntryPrefill.ROUTE_ODOMETER) {
            Notifications.cancel(this, Notifications.ID_CONNECT)
            container.pendingRoute.value = Routes.ODOMETER
            intent.removeExtra(EntryPrefill.EXTRA_ROUTE)
        }
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("ui_state", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (!Notifications.canPost(this)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
